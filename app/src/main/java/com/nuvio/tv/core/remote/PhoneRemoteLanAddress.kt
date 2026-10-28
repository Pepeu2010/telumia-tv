package com.nuvio.tv.core.remote

import java.net.Inet4Address
import java.net.NetworkInterface

/** Resolves only RFC1918 addresses from active Wi-Fi or Ethernet interfaces. */
internal object PhoneRemoteLanAddress {
    fun resolve(): String? = runCatching {
        NetworkInterface.getNetworkInterfaces()
            ?.toList()
            .orEmpty()
            .asSequence()
            .filter { network ->
                runCatching { network.isUp && !network.isLoopback }.getOrDefault(false) &&
                    isSupportedInterfaceName(network.name)
            }
            .sortedBy { interfacePriority(it.name) }
            .flatMap { network ->
                runCatching { network.inetAddresses.toList().asSequence() }
                    .getOrDefault(emptySequence())
            }
            .filterIsInstance<Inet4Address>()
            .firstOrNull { isPrivateIpv4(it.address) }
            ?.hostAddress
    }.getOrNull()

    internal fun isPrivateIpv4(address: ByteArray): Boolean {
        if (address.size != 4) return false
        val first = address[0].toInt() and 0xff
        val second = address[1].toInt() and 0xff
        return first == 10 ||
            (first == 172 && second in 16..31) ||
            (first == 192 && second == 168)
    }

    private fun isSupportedInterfaceName(name: String): Boolean {
        val normalized = name.lowercase()
        return normalized.startsWith("wlan") || normalized.startsWith("wifi") ||
            normalized.startsWith("eth") || normalized.startsWith("en")
    }

    private fun interfacePriority(name: String): Int = when {
        name.startsWith("wlan", ignoreCase = true) || name.startsWith("wifi", ignoreCase = true) -> 0
        else -> 1
    }
}
