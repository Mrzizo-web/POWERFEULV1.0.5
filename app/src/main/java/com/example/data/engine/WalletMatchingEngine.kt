package com.example.data.engine

import androidx.room.withTransaction
import com.example.data.local.AppDatabase
import com.example.data.local.entity.AuditLogEntity
import com.example.data.local.entity.WalletSmsEntity
import com.example.data.local.entity.WalletTransactionEntity
import com.example.domain.model.SmsProcessingStatus
import com.example.domain.model.WalletTransferStatus
import com.example.domain.model.UserRole
import com.example.security.AppPermission
import com.example.security.PermissionChecker
import java.security.MessageDigest
import java.util.UUID
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

sealed class IngestResult {
    data class Created(val tx: WalletTransactionEntity, val isOutOfShift: Boolean) : IngestResult()
    data class Duplicate(val tx: WalletTransactionEntity, val reason: String) : IngestResult()
    data class Unmatched(val sms: WalletSmsEntity) : IngestResult()
}

class WalletMatchingEngine(private val db: AppDatabase) {
    private val walletDao = db.walletDao()
    private val shiftDao = db.shiftDao()
    private val auditLogDao = db.auditLogDao()
    private val ingestMutex = Mutex()

    suspend fun ingestSms(
        sender: String,
        body: String,
        receivedAt: Long = System.currentTimeMillis(),
        sourceSmsId: String? = null
    ): IngestResult = ingestMutex.withLock {
        try {
            ingestSmsLocked(sender, body, receivedAt, sourceSmsId)
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            // Separate service/ViewModel engine instances do not share the in-memory Mutex.
            // The unique Room index on originalSmsId is the durable race winner; turn the
            // losing constraint exception into a normal Duplicate response for retry safety.
            val stableId = sourceSmsId?.trim().takeIf { !it.isNullOrEmpty() }
                ?: stableSmsId(sender, receivedAt, body)
            val existing = walletDao.getTransactionByOriginalSmsId(stableId)
            if (existing != null) {
                IngestResult.Duplicate(existing, "نفس حدث SMS/Idempotency-Key حُفظ بالفعل بواسطة معالجة متزامنة")
            } else {
                // Unknown/non-financial messages may be stored without a wallet transaction.
                // A concurrent retry hitting the unique SMS primary key must return the
                // stored unmatched SMS rather than bubbling up a duplicate-key exception.
                val existingSms = walletDao.getSmsById(stableId)
                if (existingSms != null) {
                    val matched = existingSms.matchedTransactionId?.let { walletDao.getTransactionById(it) }
                    if (matched != null) {
                        IngestResult.Duplicate(matched, "نفس حدث SMS/Idempotency-Key تمت معالجته مسبقًا")
                    } else {
                        IngestResult.Unmatched(existingSms)
                    }
                } else {
                    throw error
                }
            }
        }
    }

    private suspend fun ingestSmsLocked(
        sender: String,
        body: String,
        receivedAt: Long,
        sourceSmsId: String?
    ): IngestResult {
        val parsed = WalletParserRegistry.parseSms(sender, body)
        // Stable id makes gateway retries idempotent even when the SMS has no financial reference.
        val smsId = sourceSmsId?.trim().takeIf { !it.isNullOrEmpty() }
            ?: stableSmsId(sender, receivedAt, body)

        val alreadyReceived = walletDao.getSmsById(smsId)
        if (alreadyReceived != null) {
            val existingTx = alreadyReceived.matchedTransactionId?.let { walletDao.getTransactionById(it) }
            if (existingTx != null) {
                return IngestResult.Duplicate(existingTx, "نفس حدث SMS/Idempotency-Key تمت معالجته مسبقاً")
            }
            return IngestResult.Unmatched(alreadyReceived)
        }

        if (parsed == null || parsed.amount <= 0) {
            val sms = WalletSmsEntity(
                id = smsId,
                walletCode = "UNKNOWN",
                receivedAt = receivedAt,
                sender = sender,
                body = body,
                processingStatus = SmsProcessingStatus.UNMATCHED
            )
            walletDao.insertSms(sms)
            return IngestResult.Unmatched(sms)
        }

        // Jaib outgoing SMS is never converted into an incoming POS transfer.
        if (parsed.direction == WalletDirection.OUTGOING) {
            val sms = WalletSmsEntity(
                id = smsId,
                walletCode = parsed.walletCode,
                receivedAt = receivedAt,
                sender = sender,
                body = body,
                parsedAmount = parsed.amount,
                parsedTransactionId = parsed.transactionId,
                parsedSender = parsed.sender,
                processingStatus = SmsProcessingStatus.UNMATCHED
            )
            walletDao.insertSms(sms)
            return IngestResult.Unmatched(sms)
        }

        // Only a real transaction/reference ID is a financial duplicate key.
        // Never use amount + sender + time as a duplicate key.
        val existingTx = parsed.transactionId
            .takeIf { it.isNotBlank() }
            ?.let { walletDao.findTransactionByTxId(parsed.walletCode, it) }

        if (existingTx != null) {
            val amountConflict = kotlin.math.abs(existingTx.amount - parsed.amount) > 0.01
            val sms = WalletSmsEntity(
                id = smsId,
                walletCode = parsed.walletCode,
                receivedAt = receivedAt,
                sender = sender,
                body = body,
                parsedAmount = parsed.amount,
                parsedTransactionId = parsed.transactionId,
                parsedSender = parsed.sender,
                processingStatus = if (amountConflict) SmsProcessingStatus.UNMATCHED else SmsProcessingStatus.DUPLICATE,
                matchedTransactionId = if (amountConflict) null else existingTx.id
            )
            db.withTransaction {
                walletDao.insertSms(sms)
                auditLogDao.insertLog(
                    AuditLogEntity(
                        userId = "SYSTEM",
                        userName = "SMS Gateway",
                        userRole = "SYSTEM",
                        action = if (amountConflict) "WALLET_REFERENCE_CONFLICT" else "DUPLICATE_SMS_DETECTED",
                        entityType = "WALLET_TX",
                        entityId = existingTx.id,
                        previousValue = existingTx.amount.toString(),
                        newValue = parsed.amount.toString(),
                        notes = if (amountConflict) {
                            "رقم المعاملة نفسه وصل بمبلغ مختلف؛ حُفظت الرسالة للمراجعة ولم تُنشأ معاملة مالية إضافية"
                        } else {
                            "تم اكتشاف التكرار بواسطة رقم المرجع/المعاملة والمبلغ متطابق"
                        }
                    )
                )
            }
            return if (amountConflict) {
                IngestResult.Unmatched(sms)
            } else {
                IngestResult.Duplicate(existingTx, "رسالة مكررة لنفس رقم المرجع ${existingTx.transactionId}")
            }
        }

        // Critical: resolve the shift from the SMS arrival time, not processing time.
        val shift = shiftDao.getShiftContainingTime(receivedAt)
        val isOutOfShift = shift == null

        // Do not invent a transaction/reference number when the real SMS has none.
        val tx = WalletTransactionEntity(
            id = UUID.randomUUID().toString(),
            walletCode = parsed.walletCode,
            amount = parsed.amount,
            transactionId = parsed.transactionId,
            sender = parsed.sender.ifEmpty { sender },
            receivedAt = receivedAt,
            shiftId = shift?.id,
            cashierId = shift?.userId,
            cashierName = shift?.userName,
            status = if (isOutOfShift) WalletTransferStatus.OUT_OF_SHIFT else WalletTransferStatus.MATCHED,
            originalSmsId = smsId,
            notes = if (isOutOfShift) {
                "وصلت خارج نطاق أي شفت بحسب receivedAt"
            } else {
                "مرتبطة بالشفت ${shift.userName} بحسب receivedAt"
            }
        )

        val sms = WalletSmsEntity(
            id = smsId,
            walletCode = parsed.walletCode,
            receivedAt = receivedAt,
            sender = sender,
            body = body,
            parsedAmount = parsed.amount,
            parsedTransactionId = parsed.transactionId,
            parsedSender = parsed.sender,
            processingStatus = SmsProcessingStatus.PROCESSED,
            matchedTransactionId = tx.id
        )

        db.withTransaction {
            walletDao.insertTransaction(tx)
            walletDao.insertSms(sms)
            auditLogDao.insertLog(
                AuditLogEntity(
                    userId = "SYSTEM",
                    userName = "SMS Gateway",
                    userRole = "SYSTEM",
                    action = if (isOutOfShift) "WALLET_TX_OUT_OF_SHIFT" else "WALLET_TX_RECEIVED",
                    entityType = "WALLET_TX",
                    entityId = tx.id,
                    newValue = "${tx.amount} ريال (${tx.walletCode})",
                    notes = if (isOutOfShift) {
                        "حوالة واردة بدون شفت يحتوي receivedAt"
                    } else {
                        "حوالة واردة مرتبطة بشفت ${shift?.userName} حسب receivedAt"
                    }
                )
            )
        }

        return IngestResult.Created(tx, isOutOfShift)
    }

    private fun stableSmsId(sender: String, receivedAt: Long, body: String): String {
        val source = "$sender|$receivedAt|$body".toByteArray(Charsets.UTF_8)
        val digest = MessageDigest.getInstance("SHA-256").digest(source)
        return "sms-" + digest.joinToString("") { "%02x".format(it) }
    }

    suspend fun updateTransactionStatus(
        txId: String,
        newStatus: WalletTransferStatus,
        reason: String,
        userId: String,
        userName: String,
        userRole: String
    ): Result<WalletTransactionEntity> {
        val role = runCatching { UserRole.valueOf(userRole) }.getOrNull()
            ?: return Result.failure(SecurityException("دور المستخدم غير صالح"))
        if (!PermissionChecker.hasPermission(role, AppPermission.MANAGE_WALLETS)) {
            return Result.failure(SecurityException("ليس لديك صلاحية تعديل حالة الحوالة"))
        }
        if (newStatus == WalletTransferStatus.REJECTED && reason.isBlank()) {
            return Result.failure(IllegalArgumentException("سبب رفض الحوالة مطلوب"))
        }
        return try {
            db.withTransaction {
                val tx = walletDao.getTransactionById(txId)
                    ?: return@withTransaction Result.failure(Exception("الحوالة غير موجودة"))
                if (tx.status == WalletTransferStatus.DUPLICATE && newStatus != WalletTransferStatus.DUPLICATE) {
                    return@withTransaction Result.failure(IllegalStateException("لا يمكن تغيير حالة حوالة مصنفة مكررة"))
                }
                val updated = tx.copy(
                    status = newStatus,
                    rejectionReason = if (newStatus == WalletTransferStatus.REJECTED) reason.trim() else tx.rejectionReason,
                    updatedAt = System.currentTimeMillis()
                )
                walletDao.updateTransaction(updated)
                auditLogDao.insertLog(
                    AuditLogEntity(
                        userId = userId,
                        userName = userName,
                        userRole = userRole,
                        action = "UPDATE_WALLET_TX_STATUS",
                        entityType = "WALLET_TX",
                        entityId = txId,
                        previousValue = tx.status.name,
                        newValue = newStatus.name,
                        notes = reason.ifEmpty { "تعديل حالة الحوالة إلى ${newStatus.titleAr}" }
                    )
                )
                Result.success(updated)
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        }
    }

    suspend fun reassignShift(
        txId: String,
        newShiftId: String,
        cashierId: String,
        cashierName: String,
        userId: String,
        userName: String,
        userRole: String,
        reason: String
    ): Result<WalletTransactionEntity> {
        val role = runCatching { UserRole.valueOf(userRole) }.getOrNull()
            ?: return Result.failure(SecurityException("دور المستخدم غير صالح"))
        if (!PermissionChecker.hasPermission(role, AppPermission.REASSIGN_TRANSFER)) {
            return Result.failure(SecurityException("ليس لديك صلاحية إعادة إسناد الحوالة"))
        }
        if (reason.isBlank()) {
            return Result.failure(IllegalArgumentException("سبب إعادة إسناد الحوالة مطلوب"))
        }
        return try {
            db.withTransaction {
                val tx = walletDao.getTransactionById(txId)
                    ?: return@withTransaction Result.failure(Exception("الحوالة غير موجودة"))
                if (tx.status == WalletTransferStatus.DUPLICATE || tx.status == WalletTransferStatus.REJECTED) {
                    return@withTransaction Result.failure(IllegalStateException("لا يمكن إعادة إسناد حوالة مكررة أو مرفوضة"))
                }
                val targetShift = shiftDao.getShiftById(newShiftId)
                    ?: return@withTransaction Result.failure(IllegalArgumentException("الشفت الهدف غير موجود"))
                if (targetShift.userId != cashierId || targetShift.userName != cashierName) {
                    return@withTransaction Result.failure(IllegalArgumentException("بيانات الكاشير لا تطابق مالك الشفت الهدف"))
                }
                val previousShift = tx.shiftId ?: "خارج الدوام"
                val updated = tx.copy(
                    shiftId = newShiftId,
                    cashierId = cashierId,
                    cashierName = cashierName,
                    status = WalletTransferStatus.MATCHED,
                    notes = "تمت إعادة الربط بالشفت بواسطة $userName: $reason",
                    updatedAt = System.currentTimeMillis()
                )
                walletDao.updateTransaction(updated)
                auditLogDao.insertLog(
                    AuditLogEntity(
                        userId = userId,
                        userName = userName,
                        userRole = userRole,
                        action = "REASSIGN_WALLET_TX_SHIFT",
                        entityType = "WALLET_TX",
                        entityId = txId,
                        previousValue = previousShift,
                        newValue = newShiftId,
                        notes = "إعادة إسناد الحوالة: $reason"
                    )
                )
                Result.success(updated)
            }
        } catch (cancelled: kotlinx.coroutines.CancellationException) {
            throw cancelled
        } catch (error: Exception) {
            Result.failure(error)
        }
    }


}
