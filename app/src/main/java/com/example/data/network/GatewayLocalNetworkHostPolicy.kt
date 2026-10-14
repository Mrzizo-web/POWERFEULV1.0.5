package com.example.data.network

import java.net.Inet6Address
import java.net.InetAddress

/**
 * Cleartext HTTP is permitted only to explicit private LAN/VPN hosts.
 * This includes RFC1918, loopback, link-local, ULA IPv6, and Tailscale-style
 * overlay VPN addresses in RFC 6598's 100.64.0.0/10 shared address range.
 */
object GatewayLocalNetworkHostPolicy {
    fun isAllowedCleartextHost(host: String): Boolean {
        val normalized = host.trim().removePrefix("[").removeSuffix("]").lowercase()
        if (normalized == "localhost" || normalized.endsWith(".localhost")) return true

        if (normalized.contains(":")) {
            // Parse as an actual IPv6 literal; a prefix such as "fd" is not sufficient.
            if (normalized.any { !(it in '0'..'9' || it in 'a'..'f' || it == ':' || it == '.' || it == '%') }) return false
            val address = runCatching { InetAddress.getByName(normalized) }.getOrNull() as? Inet6Address
                ?: return false
            val firstByte = address.address[0].toInt() and 0xff
            return address.isLoopbackAddress || address.isLinkLocalAddress || (firstByte and 0xfe) == 0xfc
        }

        val octets = normalized.split(".")
        if (octets.size != 4 || octets.any { it.isEmpty() || it.any { char -> char !in '0'..'9' } }) return false
        val numbers = octets.map { it.toIntOrNull() ?: return false }
        if (numbers.any { it !in 0..255 }) return false
        val a = numbers[0]
        val b = numbers[1]
        return a == 10 ||
            (a == 100 && b in 64..127) ||
            (a == 172 && b in 16..31) ||
            (a == 192 && b == 168) ||
            (a == 169 && b == 254) ||
            a == 127
    }
}
