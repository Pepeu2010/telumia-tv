package com.nuvio.tv.data.local

import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.StandardCopyOption
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.charset.StandardCharsets

/** Stores only regenerable Continue Watching snapshots inside their device-local metadata share. */
internal class BoundedContinueWatchingSnapshotStore(
    directory: File,
    private val quotaBytes: () -> Long,
    private val canWriteEntry: (Long) -> Boolean,
    private val maxBytes: Long = MAX_TOTAL_BYTES,
) {
    private val directory = directory.toPath().toAbsolutePath().normalize()
    private val lock = Any()

    init { require(maxBytes > 0) }

    fun read(fileName: String): String? = synchronized(lock) {
        val target = target(fileName) ?: return@synchronized null
        runCatching {
            if (Files.isSymbolicLink(directory) || !Files.isDirectory(directory, NOFOLLOW_LINKS)) return@runCatching null
            if (Files.isSymbolicLink(target) || !Files.isRegularFile(target, NOFOLLOW_LINKS)) return@runCatching null
            val root = directory.toRealPath()
            if (!target.toRealPath().startsWith(root)) return@runCatching null
            val size = Files.size(target)
            if (size !in 1..MAX_READ_BYTES) return@runCatching null
            String(Files.readAllBytes(target), StandardCharsets.UTF_8)
        }.getOrNull()
    }

    /** A denied or oversized update leaves the previous snapshot untouched. */
    fun write(fileName: String, content: String): Boolean = synchronized(lock) {
        val target = target(fileName) ?: return@synchronized false
        val bytes = content.toByteArray(StandardCharsets.UTF_8)
        val quota = runCatching { quotaBytes().coerceAtLeast(0L) }.getOrDefault(0L).coerceAtMost(maxBytes)
        if (bytes.isEmpty() || bytes.size.toLong() > quota ||
            !runCatching { canWriteEntry(bytes.size.toLong()) }.getOrDefault(false)
        ) return@synchronized false

        val usedWithoutTarget = existingOwnedBytesExcluding(target) ?: return@synchronized false
        if (usedWithoutTarget > quota || bytes.size.toLong() > quota - usedWithoutTarget) return@synchronized false

        runCatching {
            Files.createDirectories(directory)
            if (Files.isSymbolicLink(directory) || !Files.isDirectory(directory, NOFOLLOW_LINKS)) return@runCatching false
            val root = directory.toRealPath()
            if (target.parent != directory || !target.parent.toRealPath().startsWith(root)) return@runCatching false

            val pending = Files.createTempFile(directory, PENDING_PREFIX, PENDING_SUFFIX)
            try {
                Files.write(pending, bytes)
                if (Files.size(pending) != bytes.size.toLong()) return@runCatching false
                try {
                    Files.move(pending, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                } catch (_: AtomicMoveNotSupportedException) {
                    Files.move(pending, target, StandardCopyOption.REPLACE_EXISTING)
                }
                Files.isRegularFile(target, NOFOLLOW_LINKS) && target.toRealPath().parent == root
            } finally {
                Files.deleteIfExists(pending)
            }
        }.getOrDefault(false)
    }

    fun delete(fileName: String): Boolean = synchronized(lock) {
        val target = target(fileName) ?: return@synchronized false
        runCatching {
            if (Files.isSymbolicLink(directory) || !Files.isDirectory(directory, NOFOLLOW_LINKS) ||
                target.parent.toRealPath() != directory.toRealPath()
            ) return@runCatching false
            Files.deleteIfExists(target)
        }.getOrDefault(false)
    }

    private fun existingOwnedBytesExcluding(target: java.nio.file.Path): Long? = runCatching {
        if (!Files.exists(directory, NOFOLLOW_LINKS)) return@runCatching 0L
        if (Files.isSymbolicLink(directory) || !Files.isDirectory(directory, NOFOLLOW_LINKS)) return@runCatching null
        val root = directory.toRealPath()
        var total = 0L
        var visited = 0
        Files.newDirectoryStream(directory).use { children ->
            for (path in children) {
                val name = path.fileName.toString()
                if (!isOwnedSnapshot(name) &&
                    !(name.startsWith(PENDING_PREFIX) && name.endsWith(PENDING_SUFFIX))
                ) continue
                visited++
                if (visited > MAX_TRACKED_FILES || Files.isSymbolicLink(path) ||
                    !Files.isRegularFile(path, NOFOLLOW_LINKS) || !path.toRealPath().startsWith(root)
                ) return@runCatching null
                if (path.toAbsolutePath().normalize() == target) continue
                val size = Files.size(path)
                if (total > Long.MAX_VALUE - size) return@runCatching null
                total += size
            }
        }
        total
    }.getOrNull()

    private fun target(fileName: String) = fileName
        .takeIf(::isOwnedSnapshot)
        ?.let { directory.resolve(it).normalize() }
        ?.takeIf { it.parent == directory }

    private fun isOwnedSnapshot(fileName: String): Boolean = OWNED_FILE.matches(fileName)

    companion object {
        const val MAX_TOTAL_BYTES = 8L * 1024L * 1024L
        private const val MAX_READ_BYTES = 32L * 1024L * 1024L
        private const val MAX_TRACKED_FILES = 32
        private const val PENDING_PREFIX = "cw-enrichment-pending-"
        private const val PENDING_SUFFIX = ".part"
        private val OWNED_FILE = Regex("(?:nextup|inprogress)_[1-6]\\.json")
    }
}
