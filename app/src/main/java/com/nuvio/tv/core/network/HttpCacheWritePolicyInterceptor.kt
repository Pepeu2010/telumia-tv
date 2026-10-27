package com.nuvio.tv.core.network

import okhttp3.Interceptor
import okhttp3.Response

/** Prevents disk writes on network responses while leaving already cached responses readable. */
internal class HttpCacheWritePolicyInterceptor(
    private val writesEnabled: () -> Boolean,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        return if (response.code in 400..599 || !writesEnabled()) {
            response.newBuilder()
                .header("Cache-Control", "no-store")
                .build()
        } else {
            response
        }
    }
}
