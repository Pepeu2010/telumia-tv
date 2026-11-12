package com.nuvio.tv.core.livetv

import android.content.Context
import com.nuvio.tv.domain.model.LiveTvChannel
import com.nuvio.tv.domain.model.LiveTvProgram
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.xml.sax.Attributes
import org.xml.sax.InputSource
import org.xml.sax.SAXException
import org.xml.sax.helpers.DefaultHandler
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.text.Normalizer
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.zip.GZIPInputStream
import javax.inject.Inject
import javax.inject.Singleton
import javax.xml.XMLConstants
import javax.xml.parsers.SAXParserFactory

/** A regional XMLTV file published by epgshare01.online. */
data class EpgRegion(val code: String, val label: String)

/** Programming matched to add-on channels, keyed by [LiveTvChannel.id]. */
data class EpgGuideResult(
    val programsByChannelId: Map<String, List<LiveTvProgram>>,
    val matchedChannels: Int,
    val totalChannels: Int,
    val failedRegions: List<String>,
)

/**
 * Real programme guide for add-on channels. The add-ons list channels but carry no schedule, so the
 * regional XMLTV files from epgshare01 are downloaded (and kept on disk for a few hours), the
 * channels are matched by name, and only the matched channels' programmes are kept in memory.
 */
@Singleton
class EpgShareRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val preferences by lazy { context.getSharedPreferences("epg_share", Context.MODE_PRIVATE) }
    private val directory by lazy { File(context.cacheDir, "epgshare").apply { mkdirs() } }

    fun selectedRegions(): List<String> {
        val stored = preferences.getStringSet(KEY_REGIONS, null)
        val known = REGIONS.map(EpgRegion::code).toSet()
        return (stored?.filter { it in known }?.takeIf { it.isNotEmpty() } ?: defaultRegions())
            .sortedBy { code -> REGIONS.indexOfFirst { it.code == code } }
    }

    fun setRegions(codes: Collection<String>) {
        preferences.edit().putStringSet(KEY_REGIONS, codes.toSet()).apply()
    }

    private fun defaultRegions(): List<String> {
        val local = when (Locale.getDefault().country.uppercase()) {
            "BR" -> "BR1,BR2"
            "PT" -> "PT1"
            "GB", "UK" -> "UK1"
            "ES" -> "ES1"
            "MX" -> "MX1"
            "AR" -> "AR1"
            "IT" -> "IT1"
            "FR" -> "FR1"
            "DE" -> "DE1"
            "CA" -> "CA2"
            else -> null
        }
        return (local?.split(",").orEmpty() + "US2").distinct()
    }

    suspend fun load(channels: List<LiveTvChannel>, regionCodes: List<String>): EpgGuideResult =
        withContext(Dispatchers.IO) {
            val wanted = channels.filter { it.displayName.isNotBlank() }
            val keysByChannel = wanted.associate { it.id to channelKeys(it.displayName) }
            val programs = HashMap<String, MutableList<LiveTvProgram>>()
            val failed = mutableListOf<String>()
            val now = System.currentTimeMillis()
            for (code in regionCodes) {
                try {
                    val file = ensureFile(code)
                    val region = parseRegion(file, code, keysByChannel, now)
                    region.forEach { (channelId, list) -> programs.getOrPut(channelId) { mutableListOf() }.addAll(list) }
                } catch (error: IOException) {
                    android.util.Log.w(TAG, "EPG region $code failed: ${error.message}")
                    failed += code
                } catch (error: SAXException) {
                    android.util.Log.w(TAG, "EPG region $code is not valid XMLTV: ${error.message}")
                    failed += code
                }
            }
            android.util.Log.i(TAG, "EPG matched ${programs.size} of ${wanted.size} channels from ${regionCodes.joinToString()}")
            val sorted = programs.mapValues { (_, list) ->
                list.sortedBy(LiveTvProgram::startEpochMillis)
                    .distinctBy { it.startEpochMillis to it.title }
            }.filterValues { it.isNotEmpty() }
            EpgGuideResult(sorted, sorted.size, wanted.size, failed)
        }

    private fun ensureFile(code: String): File {
        val file = File(directory, "$code.xml.gz")
        val fresh = file.isFile && System.currentTimeMillis() - file.lastModified() < MAX_AGE_MILLIS
        if (fresh) return file
        try {
            val connection = (URL("$BASE_URL/epg_ripper_$code.xml.gz").openConnection() as HttpURLConnection).apply {
                connectTimeout = 20_000
                readTimeout = 60_000
                setRequestProperty("User-Agent", "Telumia")
                instanceFollowRedirects = true
            }
            try {
                if (connection.responseCode !in 200..299) throw IOException("EPG HTTP ${connection.responseCode}")
                val temporary = File(directory, "$code.tmp")
                var total = 0L
                connection.inputStream.use { input ->
                    temporary.outputStream().use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            val read = input.read(buffer)
                            if (read < 0) break
                            total += read
                            if (total > MAX_DOWNLOAD_BYTES) throw IOException("EPG file too large")
                            output.write(buffer, 0, read)
                        }
                    }
                }
                if (!temporary.renameTo(file)) {
                    temporary.copyTo(file, overwrite = true)
                    temporary.delete()
                }
                file.setLastModified(System.currentTimeMillis())
            } finally {
                connection.disconnect()
            }
        } catch (error: IOException) {
            // A stale copy is better than no guide when the network is unavailable.
            if (file.isFile) return file
            throw error
        }
        return file
    }

    private fun parseRegion(
        file: File,
        regionCode: String,
        keysByChannel: Map<String, Set<String>>,
        nowMillis: Long,
    ): Map<String, List<LiveTvProgram>> {
        val handler = GuideHandler(regionCode.lowercase(), keysByChannel, nowMillis)
        val factory = SAXParserFactory.newInstance().apply {
            isNamespaceAware = false
            // Android's parser does not know some of these hardening options; it does not load external
            // entities by default, so an unsupported option must not abort the import.
            runCatching { setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true) }
            runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
            runCatching { setFeature("http://xml.org/sax/features/external-general-entities", false) }
            runCatching { setFeature("http://xml.org/sax/features/external-parameter-entities", false) }
        }
        GZIPInputStream(file.inputStream().buffered(64 * 1024)).use { stream ->
            factory.newSAXParser().parse(InputSource(java.io.InputStreamReader(stream, Charsets.UTF_8)), handler)
        }
        return handler.result()
    }

    private class GuideHandler(
        private val regionCode: String,
        private val keysByChannel: Map<String, Set<String>>,
        private val nowMillis: Long,
    ) : DefaultHandler() {
        private class XmlChannel(val id: String) { val names = mutableListOf<String>() }

        private val xmlChannels = LinkedHashMap<String, XmlChannel>()
        private var resolved = false
        // xml channel id -> addon channel ids
        private var targets: Map<String, List<String>> = emptyMap()
        // (add-on channel id, xml channel id) -> programmes
        private val collected = HashMap<Pair<String, String>, MutableList<LiveTvProgram>>()
        private val text = StringBuilder()
        private var currentChannel: XmlChannel? = null
        private var title: String? = null
        private var description: String? = null
        private var category: String? = null
        private var icon: String? = null
        private var start = 0L
        private var stop = 0L
        private var programmeChannel: String? = null
        private var keepProgramme = false

        override fun startElement(uri: String?, localName: String?, qName: String, attributes: Attributes) {
            text.setLength(0)
            when (qName) {
                "channel" -> currentChannel = XmlChannel(attributes.getValue("id").orEmpty())
                "programme" -> {
                    if (!resolved) resolveTargets()
                    programmeChannel = attributes.getValue("channel")
                    keepProgramme = programmeChannel != null && targets.containsKey(programmeChannel)
                    if (keepProgramme) {
                        start = parseTimestamp(attributes.getValue("start"))
                        stop = parseTimestamp(attributes.getValue("stop"))
                        keepProgramme = start > 0 && stop > start && stop > nowMillis - PAST_MILLIS &&
                            start < nowMillis + FUTURE_MILLIS
                    }
                    title = null; description = null; category = null; icon = null
                }
                "icon" -> if (keepProgramme && icon == null) icon = attributes.getValue("src")?.takeIf { it.startsWith("http") }
            }
        }

        override fun characters(ch: CharArray, start: Int, length: Int) {
            if (text.length < MAX_TEXT) text.append(ch, start, length)
        }

        override fun endElement(uri: String?, localName: String?, qName: String) {
            when (qName) {
                "display-name" -> currentChannel?.names?.add(text.toString().trim())
                "channel" -> currentChannel?.let { xmlChannels[it.id] = it }.also { currentChannel = null }
                "title" -> if (keepProgramme && title == null) title = text.toString().trim()
                "desc" -> if (keepProgramme && description == null) description = text.toString().trim().ifBlank { null }
                "category" -> if (keepProgramme && category == null) category = text.toString().trim().ifBlank { null }
                "programme" -> {
                    if (keepProgramme) {
                        val name = title?.takeIf(String::isNotBlank)
                        if (name != null) {
                            targets[programmeChannel].orEmpty().forEach { addonChannelId ->
                                collected.getOrPut(addonChannelId to programmeChannel.orEmpty()) { mutableListOf() }.add(
                                    LiveTvProgram(
                                        sourceId = "epgshare:$regionCode",
                                        channelId = addonChannelId,
                                        title = name,
                                        startEpochMillis = start,
                                        endEpochMillis = stop,
                                        description = description,
                                        category = category,
                                        iconUrl = icon,
                                    )
                                )
                            }
                        }
                    }
                    keepProgramme = false
                }
            }
        }

        /** Chooses, for every add-on channel, the XMLTV channel that best matches its name. */
        private fun resolveTargets() {
            resolved = true
            val byKey = HashMap<String, MutableList<XmlChannel>>()
            xmlChannels.values.forEach { channel ->
                val keys = HashSet<String>()
                keys += channelKeys(channel.id, regionCode)
                channel.names.forEach { keys += channelKeys(it, regionCode) }
                keys.forEach { byKey.getOrPut(it) { mutableListOf() }.add(channel) }
            }
            val result = HashMap<String, MutableList<String>>()
            keysByChannel.forEach { (addonChannelId, keys) ->
                // Several XMLTV channels can share a name (one per city, HD/SD); keep every candidate and
                // pick, once the programmes are read, the one that actually has a schedule.
                val candidates = keys.flatMap { key -> byKey[key].orEmpty() }.distinctBy { it.id }
                    .ifEmpty { listOfNotNull(fuzzy(keys, byKey)) }
                candidates.forEach { result.getOrPut(it.id) { mutableListOf() }.add(addonChannelId) }
            }
            targets = result
        }

        private fun fuzzy(keys: Set<String>, byKey: Map<String, List<XmlChannel>>): XmlChannel? {
            for (key in keys) {
                if (key.length < 6) continue
                for ((candidate, channels) in byKey) {
                    if (candidate.length < 6) continue
                    val shorter = minOf(candidate.length, key.length)
                    val longer = maxOf(candidate.length, key.length)
                    if (shorter.toFloat() / longer >= 0.75f && (candidate.startsWith(key) || key.startsWith(candidate))) {
                        return channels.firstOrNull()
                    }
                }
            }
            return null
        }

        fun result(): Map<String, List<LiveTvProgram>> {
            if (!resolved) resolveTargets()
            return collected.entries.groupBy({ it.key.first }, { it.value })
                .mapValues { (_, lists) -> lists.maxByOrNull { it.size }.orEmpty() }
        }
    }

    companion object {
        private const val BASE_URL = "https://epgshare01.online/epgshare01"
        private const val TAG = "EpgShare"
        private const val KEY_REGIONS = "regions"
        private const val MAX_AGE_MILLIS = 6L * 60L * 60L * 1000L
        private const val MAX_DOWNLOAD_BYTES = 20L * 1024L * 1024L
        private const val PAST_MILLIS = 2L * 60L * 60L * 1000L
        private const val FUTURE_MILLIS = 48L * 60L * 60L * 1000L
        private const val MAX_TEXT = 4_096

        /** Files published by epgshare01 that fit comfortably on a TV (the world-wide dumps do not). */
        val REGIONS = listOf(
            EpgRegion("BR1", "Brasil"),
            EpgRegion("BR2", "Brasil 2"),
            EpgRegion("US2", "Estados Unidos"),
            EpgRegion("US_SPORTS1", "EUA - Esportes"),
            EpgRegion("PT1", "Portugal"),
            EpgRegion("UK1", "Reino Unido"),
            EpgRegion("ES1", "Espanha"),
            EpgRegion("MX1", "México"),
            EpgRegion("AR1", "Argentina"),
            EpgRegion("IT1", "Itália"),
            EpgRegion("FR1", "França"),
            EpgRegion("DE1", "Alemanha"),
            EpgRegion("CA2", "Canadá"),
            EpgRegion("PLEX1", "Plex"),
        )

        private val timestamp = DateTimeFormatter.ofPattern("uuuuMMddHHmmss")
        private val offsetPattern = Regex("^(\\d{14})(?:\\s*([+-]\\d{4}|Z))?$")

        internal fun parseTimestamp(value: String?): Long {
            val match = offsetPattern.matchEntire(value?.trim().orEmpty()) ?: return 0L
            return try {
                val local = LocalDateTime.parse(match.groupValues[1], timestamp)
                val zone = match.groupValues[2]
                val offset = if (zone.isBlank() || zone == "Z") ZoneOffset.UTC else ZoneOffset.of(
                    zone.substring(0, 3) + ":" + zone.substring(3)
                )
                local.toInstant(offset).toEpochMilli()
            } catch (_: Exception) {
                0L
            }
        }

        private val noiseTokens = setOf("hd", "fhd", "uhd", "sd", "4k", "hevc", "h265", "canal", "channel")
        private val placePrefix = Regex("^.*?/[a-z]{2}\\.*\\s*")

        /**
         * Comparable keys for a channel name: lower-case, no accents, no city prefix
         * ("Sao Paulo/SP ..."), no quality or country markers, letters and digits only.
         */
        internal fun channelKeys(raw: String, regionCode: String? = null): Set<String> {
            val ascii = Normalizer.normalize(raw, Normalizer.Form.NFD)
                .replace(Regex("\\p{Mn}+"), "")
                .lowercase()
                .replace(Regex("\\(.*?\\)|\\[.*?]"), " ")
            val withoutPlace = ascii.replace(placePrefix, "")
            val keys = LinkedHashSet<String>()
            for (candidate in listOf(withoutPlace, ascii)) {
                val tokens = candidate.replace("&amp;", " and ").replace("&", " and ")
                    .split(Regex("[^a-z0-9]+")).filter(String::isNotBlank)
                    .filterNot { it in noiseTokens }
                    .toMutableList()
                val countryTail = setOfNotNull(regionCode, regionCode?.trimEnd { it.isDigit() })
                    .plus(listOf("br", "us", "uk", "pt", "es", "mx", "ar", "it", "fr", "de", "ca", "us2", "us1"))
                if (tokens.size > 1 && tokens.last() in countryTail) tokens.removeAt(tokens.lastIndex)
                val key = tokens.joinToString("")
                if (key.length >= 2) keys += key
            }
            return keys
        }
    }
}
