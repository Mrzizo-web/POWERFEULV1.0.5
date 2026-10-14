package com.example.security

import com.example.data.local.entity.UserEntity
import com.example.domain.model.UserRole
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class OwnerRecoveryPolicyTest {
    private fun disabledOwner() = PasswordHasher.DEFAULT.hash("2468").let { hash ->
        UserEntity(
            id = OwnerRecoveryPolicy.OWNER_ID,
            name = "Test owner",
            username = "test-owner",
            pinHash = hash.hashHex,
            pinSalt = hash.saltHex,
            role = UserRole.OWNER,
            isActive = false
        )
    }

    @Test
    fun eligibleWhenKnownOwnerIsDisabledNoOtherUsersAreActiveAndPinIsSalted() {
        assertTrue(OwnerRecoveryPolicy.isEligible(disabledOwner(), activeUserCount = 0))
    }

    @Test
    fun refusesRecoveryWhenAnotherAccountIsActive() {
        assertFalse(OwnerRecoveryPolicy.isEligible(disabledOwner(), activeUserCount = 1))
    }

    @Test
    fun refusesRecoveryForCashiersOrOtherAccounts() {
        val owner = disabledOwner()
        assertFalse(OwnerRecoveryPolicy.isEligible(owner.copy(id = "other-owner"), 0))
        assertFalse(OwnerRecoveryPolicy.isEligible(owner.copy(role = UserRole.CASHIER), 0))
    }

    @Test
    fun refusesRecoveryWhenOwnerIsActiveOrCredentialsAreMissing() {
        val owner = disabledOwner()
        assertFalse(OwnerRecoveryPolicy.isEligible(owner.copy(isActive = true), 0))
        assertFalse(OwnerRecoveryPolicy.isEligible(owner.copy(pinHash = ""), 0))
        assertFalse(OwnerRecoveryPolicy.isEligible(owner.copy(pinSalt = ""), 0))
        assertFalse(OwnerRecoveryPolicy.isEligible(null, 0))
    }
}
