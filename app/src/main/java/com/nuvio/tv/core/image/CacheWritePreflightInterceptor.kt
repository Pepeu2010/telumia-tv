package com.nuvio.tv.core.image

import coil3.intercept.Interceptor
import coil3.request.CachePolicy
import coil3.request.ImageResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Rechecks storage for every request while keeping existing disk-cache entries readable. */
internal class CacheWritePreflightInterceptor(
    private val canWrite: () -> Boolean,
) : Interceptor {
    private val storagePreflight = StorageWritePreflightSampler(canWrite)

    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val request = chain.request
        val policy = request.diskCachePolicy
        val storageAllowsWrite = !policy.writeEnabled || storagePreflight.canWrite()
        val checkedPolicy = cachePolicyAfterStorageCheck(policy, canWrite = storageAllowsWrite)
        if (checkedPolicy == policy) return chain.proceed()

        return chain.withRequest(request.newBuilder().diskCachePolicy(checkedPolicy).build()).proceed()
    }
}

internal fun cachePolicyAfterStorageCheck(policy: CachePolicy, canWrite: Boolean): CachePolicy =
    if (canWrite || !policy.writeEnabled) policy
    else if (policy.readEnabled) CachePolicy.READ_ONLY else CachePolicy.DISABLED

/** Shares the cheap filesystem probe across bursty image requests; a sample is fresh for one second. */
internal class StorageWritePreflightSampler(
    private val sample: () -> Boolean,
    private val clockMillis: () -> Long = { System.nanoTime() / 1_000_000L },
    private val refreshIntervalMillis: Long = 1_000L,
) {
    private data class Snapshot(val canWrite: Boolean, val sampledAtMillis: Long)

    @Volatile private var snapshot: Snapshot? = null

    init { require(refreshIntervalMillis > 0) }

    suspend fun canWrite(): Boolean {
        val now = clockMillis()
        snapshot?.takeIf { isFresh(it, now) }?.let { return it.canWrite }
        return withContext(Dispatchers.IO) {
            val afterDispatch = clockMillis()
            snapshot?.takeIf { isFresh(it, afterDispatch) }?.let { return@withContext it.canWrite }
            synchronized(this@StorageWritePreflightSampler) {
                val synchronizedNow = clockMillis()
                snapshot?.takeIf { isFresh(it, synchronizedNow) }?.let { return@synchronized it.canWrite }
                runCatching(sample).getOrDefault(false).also {
                    snapshot = Snapshot(it, synchronizedNow)
                }
            }
        }
    }

    private fun isFresh(snapshot: Snapshot, now: Long): Boolean =
        now >= snapshot.sampledAtMillis && now - snapshot.sampledAtMillis < refreshIntervalMillis
}

