package com.nuvio.tv.core.storage

import java.io.File
import java.nio.file.Files
import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.FileTime
import java.util.Locale

/** Bounded, allowlisted cache for remote member image files. Existing files remain readable in read-only mode. */
internal class BoundedMemberImageCache(
    directory: File,
    private val quotaBytes: () -> Long,
    private val canWrite: (Long) -> Boolean,
    private val clockMillis: () -> Long = System::currentTimeMillis,
    private val maxEntryBytes: Long = DEFAULT_MAX_ENTRY_BYTES,
    private val maxFiles: Int = DEFAULT_MAX_FILES,
) {
    companion object {
        const val DEFAULT_MAX_ENTRY_BYTES = 2L * MIB
        const val DEFAULT_MAX_FILES = 256
        private val idPattern = Regex("[A-Za-z0-9_-]{1,128}")
        private val extensionPattern = Regex("(?:png|jpe?g|webp|gif|img)")
        private val ownedFilePattern = Regex("([A-Za-z0-9_-]{1,128})-v([0-9]{1,10})\\.(png|jpe?g|webp|gif|img)")
    }

    private val directory: Path = directory.toPath().toAbsolutePath().normalize()
    private val lock = Any()

    init { require(maxEntryBytes > 0 && maxFiles > 0) }

    fun get(id: String, version: Int, extension: String): File? = synchronized(lock) {
        val path = file(id, version, extension) ?: return@synchronized null
        safeOwnedFile(path)?.also { touch(path) }?.toFile()
    }

    fun latest(id: String): File? = synchronized(lock) {
        if (!idPattern.matches(id) || Files.isSymbolicLink(directory)) return@synchronized null
        val candidates = ownedFiles().filter { ownedFilePattern.matchEntire(it.fileName.toString())?.groupValues?.getOrNull(1) == id }
        val latest = candidates.maxWithOrNull(
            compareBy<Path> {
                ownedFilePattern.matchEntire(it.fileName.toString())?.groupValues?.getOrNull(2)?.toIntOrNull() ?: Int.MIN_VALUE
            }.thenBy {
                runCatching { Files.getLastModifiedTime(it, NOFOLLOW_LINKS).toMillis() }.getOrDefault(Long.MIN_VALUE)
            },
        ) ?: return@synchronized null
        safeOwnedFile(latest)?.also { touch(latest) }?.toFile()
    }

    fun put(id: String, version: Int, extension: String, bytes: ByteArray): File? = synchronized(lock) {
        val target = file(id, version, extension) ?: return@synchronized null
        val quota = quotaBytes().coerceAtLeast(0)
        val size = bytes.size.toLong()
        if (size == 0L || size > minOf(quota, maxEntryBytes) || !canWrite(size)) return@synchronized null
        runCatching {
            Files.createDirectories(directory)
            if (Files.isSymbolicLink(directory)) {
                null
            } else {
                val root = directory.toRealPath()
                val pending = Files.createTempFile(directory, "member-image-pending-", ".part")
                try {
                    Files.write(pending, bytes)
                    runCatching {
                        Files.move(pending, target, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING)
                    }.getOrElse {
                        Files.move(pending, target, StandardCopyOption.REPLACE_EXISTING)
                    }
                    Files.setLastModifiedTime(target, FileTime.fromMillis(clockMillis()))
                } finally {
                    Files.deleteIfExists(pending)
                }
                trim(quota, root)
                safeOwnedFile(target)?.toFile()
            }
        }.getOrNull()
    }

    fun validKey(id: String, version: Int, extension: String): Boolean = file(id, version, extension) != null

    private fun file(id: String, version: Int, extension: String): Path? {
        val normalizedExtension = extension.lowercase(Locale.ROOT)
        if (!idPattern.matches(id) || version < 0 || !extensionPattern.matches(normalizedExtension)) return null
        return directory.resolve("$id-v$version.$normalizedExtension")
    }

    private fun safeOwnedFile(path: Path): Path? = runCatching {
        if (Files.isSymbolicLink(directory) || !Files.isRegularFile(path, NOFOLLOW_LINKS) || Files.size(path) > maxEntryBytes) return@runCatching null
        if (ownedFilePattern.matchEntire(path.fileName.toString()) == null) return@runCatching null
        val root = directory.toRealPath()
        val real = path.toRealPath()
        if (!real.startsWith(root)) return@runCatching null
        path
    }.getOrNull()

    private fun ownedFiles(): List<Path> = runCatching {
        if (!Files.isDirectory(directory, NOFOLLOW_LINKS) || Files.isSymbolicLink(directory)) return@runCatching emptyList()
        Files.newDirectoryStream(directory).use { stream ->
            stream.asSequence().filter { path ->
                ownedFilePattern.matchEntire(path.fileName.toString()) != null &&
                    Files.isRegularFile(path, NOFOLLOW_LINKS) && !Files.isSymbolicLink(path)
            }.take(maxFiles * 8).toList()
        }
    }.getOrDefault(emptyList())

    private fun touch(path: Path) {
        runCatching { Files.setLastModifiedTime(path, FileTime.fromMillis(clockMillis())) }
    }

    private fun trim(quota: Long, root: Path) {
        val files = ownedFiles().filter { path -> runCatching { path.toRealPath().startsWith(root) }.getOrDefault(false) }
            .sortedBy { path -> runCatching { Files.getLastModifiedTime(path, NOFOLLOW_LINKS).toMillis() }.getOrDefault(Long.MIN_VALUE) }
        var size = files.sumOf { path -> runCatching { Files.size(path) }.getOrDefault(0L) }
        var count = files.size
        for (path in files) {
            if (size <= quota && count <= maxFiles) break
            val bytes = runCatching { Files.size(path) }.getOrDefault(0L)
            if (runCatching { Files.deleteIfExists(path) }.getOrDefault(false)) {
                size = (size - bytes).coerceAtLeast(0)
                count--
            }
        }
    }
}
