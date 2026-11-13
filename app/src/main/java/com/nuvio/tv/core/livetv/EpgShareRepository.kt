package com.nuvio.tv.core.livetv

import android.content.Context
import com.nuvio.tv.domain.model.LiveTvChannel
import com.nuvio.tv.domain.model.LiveTvProgram
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
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

/** A regional XMLTV file from epgshare01.online or free-epg.de. */
data class EpgRegion(val code: String, val label: String, val url: String, val provider: String)

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
    private data class CachedGuide(
        val channelNames: List<Pair<String, String>>,
        val regions: List<String>,
        val createdMillis: Long,
        val result: EpgGuideResult,
    )
    @Volatile private var cachedGuide: CachedGuide? = null

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
            "BR" -> "FE_BR,BR1,BR2"
            "PT" -> "FE_PT,PT1"
            "GB", "UK" -> "FE_GB,UK1"
            "ES" -> "FE_ES"
            "MX" -> "FE_MX"
            "AR" -> "FE_AR"
            "IT" -> "FE_IT"
            "FR" -> "FE_FR"
            "DE" -> "FE_DE"
            "CA" -> "FE_CA"
            else -> null
        }
        // The 78 MB US guide is only a default when nothing local exists: parsing it dominates the load time.
        return local?.split(",").orEmpty().ifEmpty { listOf("FE_BR", "BR2") }
    }

    suspend fun load(channels: List<LiveTvChannel>, regionCodes: List<String>): EpgGuideResult =
        withContext(Dispatchers.IO) {
            val wanted = channels.filter { it.displayName.isNotBlank() }
            val channelNames = wanted.map { it.id to it.displayName }
            cachedGuide?.takeIf {
                it.channelNames == channelNames && it.regions == regionCodes &&
                    System.currentTimeMillis() - it.createdMillis < GUIDE_MEMORY_AGE_MILLIS
            }?.let { return@withContext it.result }
            val diskKey = diskCacheKey(channelNames, regionCodes)
            readDiskCache(diskKey, wanted.size)?.let { cached ->
                cachedGuide = CachedGuide(channelNames, regionCodes.toList(), System.currentTimeMillis(), cached)
                return@withContext cached
            }
            val keysByChannel = wanted.associate { it.id to channelKeyTiers(it.displayName) }
            val programs = HashMap<String, List<LiveTvProgram>>()
            val failed = mutableListOf<String>()
            val now = System.currentTimeMillis()
            val limiter = Semaphore(3)
            val regional = coroutineScope {
                regionCodes.distinct().map { code ->
                    async {
                        limiter.withPermit {
                            try {
                                val region = REGIONS.firstOrNull { it.code == code }
                                    ?: return@withPermit code to emptyMap<String, List<LiveTvProgram>>()
                                code to parseRegion(ensureFile(region), code, keysByChannel, now)
                            } catch (error: IOException) {
                                android.util.Log.w(TAG, "EPG region $code failed: ${error.message}")
                                code to null
                            } catch (error: SAXException) {
                                android.util.Log.w(TAG, "EPG region $code is not valid XMLTV: ${error.message}")
                                code to null
                            }
                        }
                    }
                }.awaitAll()
            }
            regional.forEach { (code, parsed) ->
                if (parsed == null) failed += code
                else parsed.forEach { (channelId, list) ->
                    // Keep the fullest schedule when regions overlap.
                    if (list.size > (programs[channelId]?.size ?: 0)) programs[channelId] = list
                }
            }
            android.util.Log.i(TAG, "EPG matched ${programs.size} of ${wanted.size} channels from ${regionCodes.joinToString()}")
            val sorted = programs.mapValues { (_, list) ->
                list.sortedBy(LiveTvProgram::startEpochMillis)
                    .distinctBy { it.startEpochMillis to it.title }
            }.filterValues { it.isNotEmpty() }
            EpgGuideResult(sorted, sorted.size, wanted.size, failed).also {
                if (failed.isEmpty()) {
                    cachedGuide = CachedGuide(channelNames, regionCodes.toList(), System.currentTimeMillis(), it)
                    writeDiskCache(diskKey, it)
                }
            }
        }

    private fun diskCacheKey(channelNames: List<Pair<String, String>>, regionCodes: List<String>): String {
        val digest = java.security.MessageDigest.getInstance("SHA-256")
        digest.update(regionCodes.sorted().joinToString(",").toByteArray())
        channelNames.sortedBy { it.first }.forEach { digest.update("${it.first}|${it.second}\n".toByteArray()) }
        return digest.digest().take(12).joinToString("") { "%02x".format(it) }
    }

    /** The matched programmes are small (a few thousand rows); keeping them avoids re-parsing 80 MB of XML. */
    private fun readDiskCache(key: String, totalChannels: Int): EpgGuideResult? = try {
        val file = File(directory, "result-$key.json")
        if (!file.isFile || System.currentTimeMillis() - file.lastModified() > DISK_RESULT_AGE_MILLIS) null
        else {
            val root = org.json.JSONObject(file.readText())
            val map = HashMap<String, List<LiveTvProgram>>()
            root.keys().forEach { channelId ->
                val array = root.getJSONArray(channelId)
                map[channelId] = List(array.length()) { i ->
                    val o = array.getJSONObject(i)
                    LiveTvProgram(
                        sourceId = o.getString("s"), channelId = channelId, title = o.getString("t"),
                        startEpochMillis = o.getLong("a"), endEpochMillis = o.getLong("b"),
                        description = o.optString("d").takeIf(String::isNotBlank),
                        category = o.optString("c").takeIf(String::isNotBlank),
                        iconUrl = o.optString("i").takeIf(String::isNotBlank),
                    )
                }
            }
            if (map.isEmpty()) null else EpgGuideResult(map, map.size, totalChannels, emptyList())
        }
    } catch (_: Exception) {
        null
    }

    private fun writeDiskCache(key: String, result: EpgGuideResult) {
        try {
            directory.listFiles { f -> f.name.startsWith("result-") }?.forEach(File::delete)
            val root = org.json.JSONObject()
            result.programsByChannelId.forEach { (channelId, list) ->
                root.put(channelId, org.json.JSONArray().also { array ->
                    list.forEach { p ->
                        array.put(org.json.JSONObject().apply {
                            put("s", p.sourceId); put("t", p.title); put("a", p.startEpochMillis); put("b", p.endEpochMillis)
                            p.description?.let { put("d", it.take(400)) }
                            p.category?.let { put("c", it) }
                            p.iconUrl?.let { put("i", it) }
                        })
                    }
                })
            }
            File(directory, "result-$key.json").writeText(root.toString())
        } catch (_: Exception) {
            // The cache is an optimisation only.
        }
    }

    private fun ensureFile(region: EpgRegion): File {
        val code = region.code
        val file = File(directory, "$code.xml.gz")
        val fresh = file.isFile && System.currentTimeMillis() - file.lastModified() < MAX_AGE_MILLIS
        if (fresh) return file
        try {
            val connection = (URL(region.url).openConnection() as HttpURLConnection).apply {
                connectTimeout = 20_000
                readTimeout = 60_000
                setRequestProperty("User-Agent", "Telumia")
                // Ask for the compressed body so the big files stay small; it is stored as received.
                setRequestProperty("Accept-Encoding", "gzip")
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
        keysByChannel: Map<String, Pair<Set<String>, Set<String>>>,
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
        // The body is gzip when the server compressed it, plain XML when it already decoded it.
        val buffered = java.io.BufferedInputStream(file.inputStream(), 64 * 1024)
        buffered.mark(2)
        val gzip = buffered.read() == 0x1f && buffered.read() == 0x8b
        buffered.reset()
        (if (gzip) GZIPInputStream(buffered, 64 * 1024) else buffered).use { stream ->
            factory.newSAXParser().parse(InputSource(java.io.InputStreamReader(stream, Charsets.UTF_8)), handler)
        }
        return handler.result()
    }

    private class GuideHandler(
        private val regionCode: String,
        private val keysByChannel: Map<String, Pair<Set<String>, Set<String>>>,
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
                        val name = title?.takeIf { it.isNotBlank() && !isPlaceholderTitle(it) }
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

        /** Chooses, for every add-on channel, the XMLTV channels that may match its name. */
        private fun resolveTargets() {
            resolved = true
            val byFull = HashMap<String, MutableList<XmlChannel>>()
            val byLoose = HashMap<String, MutableList<XmlChannel>>()
            xmlChannels.values.forEach { channel ->
                val full = HashSet<String>()
                val loose = HashSet<String>()
                (listOf(channel.id) + channel.names).forEach { name ->
                    val (f, l) = channelKeyTiers(name, regionCode)
                    full += f; loose += l
                }
                full.forEach { byFull.getOrPut(it) { mutableListOf() }.add(channel) }
                loose.forEach { byLoose.getOrPut(it) { mutableListOf() }.add(channel) }
            }
            val result = HashMap<String, MutableList<String>>()
            keysByChannel.forEach { (addonChannelId, tiers) ->
                val (full, loose) = tiers
                // A name with a place/region word ("CNN Brasil") only matches exactly; a bare name ("SBT")
                // also matches "SBT Brasil", "SBT SP"... Several XMLTV channels can share a name, so keep
                // every candidate and pick, once the programmes are read, the one with a schedule.
                val bare = loose.isEmpty() || loose == full
                var candidates = full.flatMap { byFull[it].orEmpty() }
                if (bare) candidates = candidates + full.flatMap { byLoose[it].orEmpty() }
                else if (candidates.isEmpty()) candidates = loose.flatMap { byFull[it].orEmpty() }
                candidates = candidates.distinctBy { it.id }
                    .ifEmpty { listOfNotNull(fuzzy(full, byFull)) }
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
        private const val FREE_URL = "https://www.free-epg.de/api/epg"
        private const val TAG = "EpgShare"
        private const val KEY_REGIONS = "regions"
        private const val MAX_AGE_MILLIS = 6L * 60L * 60L * 1000L
        private const val GUIDE_MEMORY_AGE_MILLIS = 15L * 60L * 1000L
        private const val DISK_RESULT_AGE_MILLIS = 45L * 60L * 1000L
        private const val MAX_DOWNLOAD_BYTES = 32L * 1024L * 1024L
        private const val PAST_MILLIS = 2L * 60L * 60L * 1000L
        private const val FUTURE_MILLIS = 48L * 60L * 60L * 1000L
        private const val MAX_TEXT = 4_096

        /** Files published by epgshare01 that fit comfortably on a TV (the world-wide dumps do not). */
        private fun share(code: String, label: String) =
            EpgRegion(code, label, "$BASE_URL/epg_ripper_$code.xml.gz", "epgshare01")
        private fun free(country: String, label: String) =
            EpgRegion("FE_${country.uppercase()}", "$label (FreeEPG)", "$FREE_URL/${country.lowercase()}.xml.gz", "free-epg")

        val REGIONS = listOf(
            free("br", "Brasil"),
            share("BR1", "Brasil"),
            share("BR2", "Brasil 2"),
            free("pt", "Portugal"),
            share("PT1", "Portugal"),
            free("us", "Estados Unidos"),
            share("US2", "Estados Unidos"),
            share("US_SPORTS1", "EUA - Esportes"),
            free("gb", "Reino Unido"),
            share("UK1", "Reino Unido"),
            free("es", "Espanha"),
            free("mx", "México"),
            free("ar", "Argentina"),
            free("it", "Itália"),
            free("fr", "França"),
            free("de", "Alemanha"),
            free("ca", "Canadá"),
            share("PLEX1", "Plex"),
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

        /** Guides pad empty slots with titles like "No Data"; they are not programmes. */
        internal fun isPlaceholderTitle(title: String): Boolean =
            title.trim().lowercase().trimEnd('.', '!') in setOf(
                "no data", "no information", "no information available", "no programme information", "no programming",
                "sem dados", "sem informacao", "sem informação", "sem programacao", "sem programação", "n/a", "tba", "to be announced",
            )

        private val noiseTokens = setOf("hd", "fhd", "uhd", "sd", "4k", "hevc", "h265", "canal", "channel")
        private val placePrefix = Regex("^.*?/[a-z]{2}\\.*\\s*")

        private val countryPrefix = Regex("^[a-z]{2}\\s*[-–]\\s+")
        private val placeWords = setOf("brasil", "brazil", "sp", "rj", "mg", "rs", "pr", "ba", "pe", "df", "ce", "latam")

        /** All comparable keys of a channel name (full and without place words). */
        internal fun channelKeys(raw: String, regionCode: String? = null): Set<String> =
            channelKeyTiers(raw, regionCode).let { it.first + it.second }

        /**
         * Comparable keys for a channel name: lower-case, no accents, no "BR - " or city prefix, no quality
         * or country markers, letters and digits only. [Pair.first] keeps place words ("cnnbrasil");
         * [Pair.second] drops trailing ones ("cnn").
         */
        internal fun channelKeyTiers(raw: String, regionCode: String? = null): Pair<Set<String>, Set<String>> {
            val ascii = Normalizer.normalize(raw, Normalizer.Form.NFD)
                .replace(Regex("\\p{Mn}+"), "")
                .lowercase()
                .replace(Regex("\\(.*?\\)|\\[.*?]"), " ")
                .trim()
                .replace(countryPrefix, "")
            val withoutPlace = ascii.replace(placePrefix, "")
            val full = LinkedHashSet<String>()
            val loose = LinkedHashSet<String>()
            for (candidate in listOf(withoutPlace, ascii)) {
                val tokens = candidate.replace("&amp;", " and ").replace("&", " and ")
                    .split(Regex("[^a-z0-9]+")).filter(String::isNotBlank)
                    .filterNot { it in noiseTokens }
                    .toMutableList()
                val countryTail = setOfNotNull(regionCode?.lowercase(), regionCode?.lowercase()?.trimEnd { it.isDigit() })
                    .plus(listOf("br", "us", "uk", "gb", "pt", "es", "mx", "ar", "it", "fr", "de", "ca", "us2", "us1"))
                if (tokens.size > 1 && tokens.last() in countryTail) tokens.removeAt(tokens.lastIndex)
                val fullKey = tokens.joinToString("")
                if (fullKey.length >= 2) full += fullKey
                val trimmed = tokens.toMutableList()
                while (trimmed.size > 1 && trimmed.last() in placeWords) trimmed.removeAt(trimmed.lastIndex)
                val looseKey = trimmed.joinToString("")
                if (looseKey.length >= 2 && looseKey != fullKey) loose += looseKey
            }
            return full to loose
        }
    }
}
