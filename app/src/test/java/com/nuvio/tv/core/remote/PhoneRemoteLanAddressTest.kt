package com.nuvio.tv.core.remote

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.InetAddress

class PhoneRemoteLanAddressTest {
    @Test
    fun allowsOnlyRfc1918Ipv4Addresses() {
        listOf("10.0.0.8", "172.16.0.5", "172.31.255.254", "192.168.1.20")
            .forEach { address ->
                assertTrue(address, PhoneRemoteLanAddress.isPrivateIpv4(InetAddress.getByName(address).address))
            }
        listOf("8.8.8.8", "172.32.0.1", "127.0.0.1", "169.254.1.2", "::1")
            .forEach { address ->
                assertFalse(address, PhoneRemoteLanAddress.isPrivateIpv4(InetAddress.getByName(address).address))
            }
    }
}
