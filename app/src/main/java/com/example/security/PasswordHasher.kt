package com.example.security

import java.security.MessageDigest
import java.security.SecureRandom
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.PBEKeySpec

data class HashResult(val hashHex: String, val saltHex: String)

class PasswordHasher {
    companion object {
        val DEFAULT = PasswordHasher()
        private const val ITERATIONS = 10000
        private const val KEY_LENGTH = 256
        private const val SALT_LENGTH = 16
    }

    fun generateSalt(): String {
        val random = SecureRandom()
        val salt = ByteArray(SALT_LENGTH)
        random.nextBytes(salt)
        return bytesToHex(salt)
    }

    fun hash(pin: String, saltHex: String = generateSalt()): HashResult {
        require(pin.isNotEmpty()) { "PIN must not be empty" }
        val saltBytes = hexToBytes(saltHex)
        val spec = PBEKeySpec(pin.toCharArray(), saltBytes, ITERATIONS, KEY_LENGTH)
        val factory = try {
            SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        } catch (e: Exception) {
            throw IllegalStateException("PBKDF2WithHmacSHA256 is required; refusing weak fallback", e)
        }
        val hashBytes = try {
            factory.generateSecret(spec).encoded
        } finally {
            spec.clearPassword()
        }
        return HashResult(bytesToHex(hashBytes), saltHex)
    }

    fun verify(pin: String, saltHex: String, expectedHashHex: String): Boolean {
        if (pin.isEmpty() || saltHex.isEmpty() || expectedHashHex.isEmpty()) return false
        return try {
            val result = hash(pin, saltHex)
            MessageDigest.isEqual(
                hexToBytes(result.hashHex),
                hexToBytes(expectedHashHex)
            )
        } catch (_: Exception) {
            false
        }
    }

    private fun bytesToHex(bytes: ByteArray): String =
        bytes.joinToString("") { "%02x".format(it) }

    private fun hexToBytes(hex: String): ByteArray {
        require(hex.length % 2 == 0) { "Invalid hex length" }
        val result = ByteArray(hex.length / 2)
        for (i in result.indices) {
            result[i] = hex.substring(i * 2, i * 2 + 2).toInt(16).toByte()
        }
        return result
    }
}
