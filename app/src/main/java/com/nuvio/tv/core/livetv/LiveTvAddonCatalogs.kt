package com.nuvio.tv.core.livetv

import com.nuvio.tv.domain.model.Addon
import com.nuvio.tv.domain.model.CatalogDescriptor
import com.nuvio.tv.domain.model.LiveTvChannel
import com.nuvio.tv.domain.model.MetaPreview
import java.security.MessageDigest

internal data class LiveTvAddonCatalog(
    val addon: Addon,
    val catalog: CatalogDescriptor,
    val sourceId: String,
)

internal fun installedLiveTvAddonCatalogs(addons: List<Addon>): List<LiveTvAddonCatalog> =
    addons.asSequence()
        .filter(Addon::enabled)
        .flatMap { addon ->
            addon.catalogs.asSequence()
                .filter { it.apiType.trim().lowercase() in LIVE_TV_ADDON_TYPES }
                .map { catalog ->
                    LiveTvAddonCatalog(
                        addon = addon,
                        catalog = catalog,
                        sourceId = liveTvAddonSourceId(addon, catalog),
                    )
                }
        }
        .distinctBy { request ->
            "${request.addon.baseUrl}|${request.catalog.apiType}|${request.catalog.id}"
        }
        .toList()

internal fun LiveTvAddonCatalog.project(items: List<MetaPreview>): List<LiveTvChannel> {
    val type = catalog.apiType.trim().lowercase()
    if (type !in LIVE_TV_ADDON_TYPES) return emptyList()
    return items.asSequence()
        .filter { it.id.isNotBlank() && it.name.isNotBlank() }
        .map { item ->
            LiveTvChannel(
                id = "$sourceId:${item.id}",
                sourceId = sourceId,
                displayName = item.name,
                logoUrl = item.logo?.takeIf(String::isNotBlank) ?: item.poster,
                groupName = LiveTvChannelCategory.fromGenres(item.genres).name,
                tvgId = item.id,
                tvgName = item.name,
                addonBaseUrl = addon.baseUrl,
                addonId = addon.id,
                addonType = type,
                addonContentId = item.id,
                addonDisplayName = addon.displayName,
            )
        }
        .toList()
}

private fun liveTvAddonSourceId(addon: Addon, catalog: CatalogDescriptor): String {
    val identity = "${addon.id}|${addon.baseUrl}|${catalog.apiType}|${catalog.id}"
    val digest = MessageDigest.getInstance("SHA-256")
        .digest(identity.toByteArray(Charsets.UTF_8))
        .take(12)
        .joinToString("") { it.toUByte().toString(16).padStart(2, '0') }
    return "addon:$digest"
}

private val LIVE_TV_ADDON_TYPES = setOf("channel", "tv")
