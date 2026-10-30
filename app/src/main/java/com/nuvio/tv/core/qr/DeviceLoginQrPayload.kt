package com.nuvio.tv.core.qr

import okhttp3.HttpUrl.Companion.toHttpUrlOrNull

/** Prefer the server's scan-ready link, then fall back to a manually-entered verification URL. */
internal fun resolveDeviceLoginQrPayload(
    verificationUriComplete: String?,
    verificationUri: String?,
    configuredFallbackUri: String?,
): String? = sequenceOf(verificationUriComplete, verificationUri, configuredFallbackUri)
    .mapNotNull { it?.trim()?.takeIf(String::isNotEmpty) }
    .firstOrNull { it.toHttpUrlOrNull() != null }
