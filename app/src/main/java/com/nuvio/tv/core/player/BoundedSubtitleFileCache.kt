package com.nuvio.tv.core.player

import java.io.ByteArrayOutputStream
import java.io.File
import java.io.InputStream
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.FileTime
import java.util.Locale

/** Stores only regenerable subtitle copies under a per-device media-cache budget. */
internal class BoundedSubtitleFileCache(
    directory: File,
    private val quotaBytes: () -> Long,
    private val canWriteEntry: (Long) -> Boolean,
    private val nowMillis: () -> Long = System::currentTimeMillis,
    private val maxEntryBytes: Long = MAX_ENTRY_BYTES.toLong(),
    private val maxFiles: Int = MAX_FILES,
) {
    companion object {
        const val MAX_ENTRY_BYTES = 4 * 1024 * 1024
        const val MAX_FILES = 64
        private val fileNamePattern = Regex("[A-Za-z0-9._-]{1,120}\\.(?:srt|vtt|ass|ssa|ttml|dfxp)", RegexOption.IGNORE_CASE)

        /** Reads at most maxBytes + 1 bytes so oversized bodies are rejected before full allocation. */
        fun readAtMost(input: InputStream, maxBytes: Int = MAX_ENTRY_BYTES): ByteArray? {
            if (maxBytes <= 0) return null
            val output = ByteArrayOutputStream(minOf(maxBytes, 32 * 1024))
            val buffer = ByteArray(16 * 1024)
            var total = 0
            while (true) {
                val remainingThroughOverflowProbe = maxBytes - total + 1
                val count = input.read(buffer, 0, minOf(buffer.size, remainingThroughOverflowProbe))
                if (count < 0) break
                if (count == 0) continue
                if (count > maxBytes - total) return null
                output.write(buffer, 0, count)
                total += count
            }
            return output.toByteArray().takeIf { it.isNotEmpty() }
        }
    }

    private val directory: Path = directory.toPath().toAbsolutePath().normalize()
    private val lock = Any()

    init { require(maxEntryBytes > 0 && maxFiles > 0) }

    fun put(fileName: String, bytes: ByteArray): File? = synchronized(lock) {
        val extension = fileName.substringAfterLast('.', "").lowercase(Locale.ROOT)
        val target = directory.resolve(fileName).normalize()
        if (!fileNamePattern.matches(fileName) || target.parent != directory) return@synchronized null
        val size = bytes.size.toLong()
        val quota = runCatching { quotaBytes().coerceAtLeast(0) }.getOrDefault(0L)
        if (size <= 0L || size > minOf(maxEntryBytes, quota) || !runCatching { canWriteEntry(size) }.getOrDefault(false)) {
            return@synchronized null
        }

        runCatching {
            Files.createDirectories(directory)
            if (Files.isSymbolicLink(directory) || extension.isEmpty()) return@runCatching null
            val root = directory.toRealPath()
            removePendingFiles()
            val files = ownedFiles()
            val existing = files.firstOrNull { it == target }
            val resultingCount = files.size + if (existing == null) 1 else 0
            if (resultingCount > maxFiles) return@runCatching null
            val usedWithoutTarget = files.asSequence().filter { it != target }.sumSaturated { Files.size(it) }
            if (usedWithoutTarget > quota || size > quota - usedWithoutTarget) return@runCatching null

            val pending = Files.createTempFile(directory, "subtitle-pending-", ".part")
            try {
                Files.write(pending, bytes)
                runCatching {
                    Files.move(pending, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                }.getOrElse {
                    Files.move(pending, target, StandardCopyOption.REPLACE_EXISTING)
                }
                Files.setLastModifiedTime(target, FileTime.fromMillis(nowMillis()))
            } finally {
                Files.deleteIfExists(pending)
            }
            if (Files.isRegularFile(target, NOFOLLOW_LINKS) && target.toRealPath().startsWith(root)) target.toFile() else null
        }.getOrNull()
    }

    fun clear() {
        synchronized(lock) {
            runCatching {
                if (!Files.isDirectory(directory, NOFOLLOW_LINKS) || Files.isSymbolicLink(directory)) return@runCatching
                Files.newDirectoryStream(directory).use { files ->
                    files.forEach { path ->
                        if (Files.isRegularFile(path, NOFOLLOW_LINKS) && !Files.isSymbolicLink(path)) Files.deleteIfExists(path)
                    }
                }
            }
        }
    }

    private fun ownedFiles(): List<Path> = runCatching {
        if (!Files.isDirectory(directory, NOFOLLOW_LINKS) || Files.isSymbolicLink(directory)) return@runCatching emptyList()
        Files.newDirectoryStream(directory).use { stream ->
            stream.asSequence().filter { path ->
                fileNamePattern.matches(path.fileName.toString()) &&
                    Files.isRegularFile(path, NOFOLLOW_LINKS) && !Files.isSymbolicLink(path) &&
                    path.toRealPath().startsWith(directory.toRealPath())
            }.take(maxFiles + 1).toList()
        }
    }.getOrDefault(emptyList())

    private fun removePendingFiles() {
        runCatching {
            Files.newDirectoryStream(directory).use { stream ->
                stream.forEach { path ->
                    if (path.fileName.toString().startsWith("subtitle-pending-") && path.fileName.toString().endsWith(".part") &&
                        Files.isRegularFile(path, NOFOLLOW_LINKS) && !Files.isSymbolicLink(path)
                    ) Files.deleteIfExists(path)
                }
            }
        }
    }

    private inline fun <T> Sequence<T>.sumSaturated(size: (T) -> Long): Long = fold(0L) { total, value ->
        val next = runCatching { size(value).coerceAtLeast(0) }.getOrDefault(0L)
        if (total > Long.MAX_VALUE - next) Long.MAX_VALUE else total + next
    }
}
