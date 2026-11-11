package com.nuvio.tv.data.local

import android.content.Context
import java.io.File

/**
 * Removes credentials that the retired Trakt integration left on disk.
 *
 * Only the per-profile authentication stores are deleted. Nuvio sign-in, Telumia Sync data and
 * local watch progress live in other files and are never touched here.
 */
internal object LegacyTraktDataCleaner {
    private const val LEGACY_AUTH_STORE_PREFIX = "trakt_auth_store"

    fun purge(context: Context): Int = purge(File(context.filesDir, "datastore"))

    internal fun purge(dataStoreDirectory: File): Int {
        val files = dataStoreDirectory.listFiles().orEmpty()
        return files.count { file ->
            file.isFile && file.name.startsWith(LEGACY_AUTH_STORE_PREFIX) && file.delete()
        }
    }
}
