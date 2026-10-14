package com.example.data.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GatewayLocalNetworkHostPolicyTest {
    @Test
    fun allowsPrivateIpv4AndLocalHosts() {
        listOf(
            "192.168.1.10", "10.0.0.14", "100.64.0.1", "100.127.255.254", "172.16.0.1", "172.31.255.254",
            "169.254.1.2", "127.0.0.1", "127.255.255.254", "localhost", "pos.localhost"
        ).forEach { host ->
            assertTrue("Expected local host to be allowed: $host",
                GatewayLocalNetworkHostPolicy.isAllowedCleartextHost(host))
        }
    }

    @Test
    fun rejectsPublicAndSpoofedLoopbackHostnames() {
        listOf(
            "8.8.8.8", "1.1.1.1", "100.63.255.254", "100.128.0.1", "172.32.0.1", "192.167.1.10",
            "10.0.0.256", "127.attacker.example", "127.0.0.1.attacker.example",
            "127.evil", "notlocalhost.example", "http://192.168.1.10",
            "fd:not-an-ip", "fdnotipv6:aa:bb", "１２７.０.０.１", ""
        ).forEach { host ->
            assertFalse("Expected invalid/public host to be rejected: $host",
                GatewayLocalNetworkHostPolicy.isAllowedCleartextHost(host))
        }
    }

    @Test
    fun allowsAndRejectsIpv6Ranges() {
        assertTrue(GatewayLocalNetworkHostPolicy.isAllowedCleartextHost("::1"))
        assertTrue(GatewayLocalNetworkHostPolicy.isAllowedCleartextHost("fd12::10"))
        assertTrue(GatewayLocalNetworkHostPolicy.isAllowedCleartextHost("fe80::1"))
        assertFalse(GatewayLocalNetworkHostPolicy.isAllowedCleartextHost("2001:4860:4860::8888"))
        assertFalse(GatewayLocalNetworkHostPolicy.isAllowedCleartextHost("fd:not-an-ip"))
    }
}
