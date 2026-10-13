package com.example.ui.wallet

import com.example.data.local.entity.WalletTransactionEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Calendar

class WalletTransactionVisibilityTest {
    private val now = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, 12)
        set(Calendar.MINUTE, 0)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun transaction(
        id: String,
        shiftId: String?,
        receivedAt: Long
    ) = WalletTransactionEntity(
        id = id,
        walletCode = "JEEB",
        amount = 100.0,
        transactionId = id,
        sender = "Test sender",
        receivedAt = receivedAt,
        shiftId = shiftId
    )

    @Test
    fun cashierWithNoOpenShiftSeesNoWalletHistory() {
        val data = listOf(
            transaction("old-unshifted", null, now - 86_400_000L),
            transaction("current-shift", "shift-today", now)
        )

        assertTrue(
            visibleWalletTransactionsForUser(
                data,
                canViewAll = false,
                currentShiftId = null,
                nowMillis = now
            ).isEmpty()
        )
    }

    @Test
    fun cashierSeesOnlyTodayTransactionsFromTheirCurrentShift() {
        val yesterday = Calendar.getInstance().apply {
            timeInMillis = now
            add(Calendar.DAY_OF_YEAR, -1)
        }.timeInMillis
        val data = listOf(
            transaction("today-current", "shift-today", now),
            transaction("yesterday-same-shift", "shift-today", yesterday),
            transaction("today-other-shift", "other-shift", now),
            transaction("today-no-shift", null, now)
        )

        val visible = visibleWalletTransactionsForUser(
            data,
            canViewAll = false,
            currentShiftId = "shift-today",
            nowMillis = now
        )

        assertEquals(listOf("today-current"), visible.map { it.id })
    }

    @Test
    fun managerWithViewAllPermissionRetainsHistoricalRecords() {
        val data = listOf(
            transaction("old-unshifted", null, now - 86_400_000L),
            transaction("today-current", "shift-today", now)
        )

        assertEquals(
            data,
            visibleWalletTransactionsForUser(
                data,
                canViewAll = true,
                currentShiftId = null,
                nowMillis = now
            )
        )
    }
}
