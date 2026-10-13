package com.example.ui.wallet

import com.example.data.local.entity.WalletTransactionEntity
import java.util.Calendar

/**
 * Applies wallet-history visibility policy before UI search and status filters.
 * Users without VIEW_ALL_TRANSFERS can see only today's transactions in their
 * active shift. A missing shift never exposes unassigned/out-of-shift history.
 */
internal fun visibleWalletTransactionsForUser(
    transactions: List<WalletTransactionEntity>,
    canViewAll: Boolean,
    currentShiftId: String?,
    nowMillis: Long
): List<WalletTransactionEntity> {
    if (canViewAll) return transactions
    val shiftId = currentShiftId?.takeIf { it.isNotBlank() } ?: return emptyList()
    val today = Calendar.getInstance().apply { timeInMillis = nowMillis }
    return transactions.filter { transaction ->
        if (transaction.shiftId != shiftId) return@filter false
        val received = Calendar.getInstance().apply { timeInMillis = transaction.receivedAt }
        received.get(Calendar.ERA) == today.get(Calendar.ERA) &&
            received.get(Calendar.YEAR) == today.get(Calendar.YEAR) &&
            received.get(Calendar.DAY_OF_YEAR) == today.get(Calendar.DAY_OF_YEAR)
    }
}
