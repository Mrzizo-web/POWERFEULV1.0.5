package com.example.data.network

/**
 * Cleartext HTTP is permitted only to explicit local/private hosts.
 *
 * IPv4 is parsed as four numeric octets before loopback is allowed; a DNS name
 * such as 127.attacker.example must never qualify merely because it begins "127.".
 */
object GatewayLocalNetworkHostPolicy {
    fun isAllowedCleartextHost(host: String): Boolean {
        val normalized = host.trim().removePrefix("[").removeSuffix("]").lowercase()
        if (normalized == "localhost" || normalized.endsWith(".localhost") || normalized == "::1") {
            return true
        }

        if (normalized.contains(":")) {
            // Only accept IPv6 literals in the loopback, unique-local, or link-local ranges.
            if (normalized.count { it == ':' } < 2 ||
                normalized.any { !(it in '0'..'9' || it in 'a'..'f' || it == ':' || it == '.' || it == '%') }
            ) return false
            return normalized == "::1" ||
                normalized.startsWith("fc") ||
                normalized.startsWith("fd") ||
                normalized.startsWith("fe80:")
        }

        val octets = normalized.split(".")
        if (octets.size != 4 || octets.any { it.isEmpty() || it.any { char -> !char.isDigit() } }) return false
        val numbers = octets.map { it.toIntOrNull() ?: return false }
        if (numbers.any { it !in 0..255 }) return false
        val a = numbers[0]
        val b = numbers[1]
        return a == 10 ||
            (a == 172 && b in 16..31) ||
            (a == 192 && b == 168) ||
            (a == 169 && b == 254) ||
            a == 127
    }
}
