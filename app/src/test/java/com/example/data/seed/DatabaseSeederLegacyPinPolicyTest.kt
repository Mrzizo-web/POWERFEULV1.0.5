package com.example.data.seed

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DatabaseSeederLegacyPinPolicyTest {
    @Test
    fun matchesOnlyExactPlaintextLegacyValuesWhenSaltIsMissing() {
        val knownLegacyValues = listOf("legacy-test-pin")

        assertTrue(
            DatabaseSeeder.isKnownLegacyPlaintextPin(
                pinHash = "legacy-test-pin",
                pinSalt = "",
                knownLegacyPins = knownLegacyValues
            )
        )
        assertFalse(
            DatabaseSeeder.isKnownLegacyPlaintextPin(
                pinHash = "custom-owner-pin",
                pinSalt = "",
                knownLegacyPins = knownLegacyValues
            )
        )
        assertFalse(
            DatabaseSeeder.isKnownLegacyPlaintextPin(
                pinHash = "legacy-test-pin",
                pinSalt = "existing-salt",
                knownLegacyPins = knownLegacyValues
            )
        )
    }
}
