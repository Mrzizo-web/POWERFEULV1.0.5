package com.example.data.engine

import androidx.room.withTransaction
import com.example.data.local.AppDatabase
import com.example.data.local.entity.AuditLogEntity
import com.example.data.local.entity.ShiftEntity
import com.example.data.local.entity.ShiftHandoverEntity
import com.example.data.local.entity.UserEntity
import com.example.domain.model.ShiftStatus
import com.example.domain.model.UserRole
import com.example.security.AppPermission
import com.example.security.PermissionChecker

class ShiftEngine(private val db: AppDatabase) {
    private companion object {
        const val CASH_RECONCILIATION_EPSILON = 0.01
    }

    private val shiftDao = db.shiftDao()
    private val auditLogDao = db.auditLogDao()

    suspend fun startShift(
        user: UserEntity,
        openingCash: Double,
        notes: String = ""
    ): ShiftResult = db.withTransaction {
        if (!PermissionChecker.hasPermission(user.role, AppPermission.OPERATE_SHIFT)) {
            return@withTransaction ShiftResult.Error("ليس لديك صلاحية فتح الشفت")
        }
        startShiftInternal(user, openingCash, notes)
    }

    private suspend fun startShiftInternal(
        user: UserEntity,
        openingCash: Double,
        notes: String = ""
    ): ShiftResult {
        if (!openingCash.isFinite() || openingCash < 0.0) {
            return ShiftResult.Error("النقدية الافتتاحية يجب أن تكون قيمة غير سالبة وصالحة")
        }
        val existing = shiftDao.getCurrentOpenShiftSync()
        if (existing != null) {
            return ShiftResult.Error("يوجد شفت مفتوح حالياً للمستخدم ${existing.userName}")
        }

        val lastNum = shiftDao.getLastShiftNumber() ?: 0
        val shift = ShiftEntity(
            shiftNumber = lastNum + 1,
            userId = user.id,
            userName = user.name,
            openingCash = openingCash,
            expectedCash = openingCash,
            actualCash = openingCash,
            status = ShiftStatus.OPEN,
            notes = notes
        )
        shiftDao.insertShift(shift)

        auditLogDao.insertLog(
            AuditLogEntity(
                userId = user.id,
                userName = user.name,
                userRole = user.role.name,
                action = "SHIFT_STARTED",
                entityType = "SHIFT",
                entityId = shift.id,
                newValue = "افتتاحي: $openingCash ريال",
                notes = "بدء الشفت رقم ${shift.shiftNumber}"
            )
        )

        return ShiftResult.Success(shift)
    }

    suspend fun closeShift(
        shiftId: String,
        actualCash: Double,
        handedOverCash: Double,
        leftForNextShift: Double,
        user: UserEntity,
        notes: String = ""
    ): ShiftResult = db.withTransaction {
        val shift = shiftDao.getShiftById(shiftId)
            ?: return@withTransaction ShiftResult.Error("الشفت غير موجود")
        if (!PermissionChecker.hasPermission(user.role, AppPermission.OPERATE_SHIFT)) {
            return@withTransaction ShiftResult.Error("ليس لديك صلاحية إغلاق الشفت")
        }
        if (shift.userId != user.id && user.role != UserRole.OWNER) {
            return@withTransaction ShiftResult.Error("لا يمكنك إغلاق شفت مستخدم آخر")
        }
        if (shift.status != ShiftStatus.OPEN) {
            return@withTransaction ShiftResult.Error("تم إغلاق هذا الشفت مسبقًا")
        }
        if (!actualCash.isFinite() || actualCash < 0.0 ||
            !handedOverCash.isFinite() || handedOverCash < 0.0 ||
            !leftForNextShift.isFinite() || leftForNextShift < 0.0
        ) {
            return@withTransaction ShiftResult.Error("قيم إغلاق الشفت يجب أن تكون أرقامًا غير سالبة وصالحة")
        }
        if (handedOverCash > actualCash || leftForNextShift > actualCash) {
            return@withTransaction ShiftResult.Error("مبالغ التسليم والمتبقي لا يمكن أن تتجاوز النقدية الفعلية")
        }
        if (kotlin.math.abs((handedOverCash + leftForNextShift) - actualCash) > CASH_RECONCILIATION_EPSILON) {
            return@withTransaction ShiftResult.Error(
                "يجب توزيع كامل النقدية الفعلية بين المبلغ المسلّم للإدارة والمبلغ المتروك للشفت القادم"
            )
        }

        val discrepancy = actualCash - shift.expectedCash
        val closed = shift.copy(
            endTime = System.currentTimeMillis(),
            actualCash = actualCash,
            handedOverCash = handedOverCash,
            leftForNextShiftCash = leftForNextShift,
            discrepancyAmount = discrepancy,
            status = ShiftStatus.CLOSED,
            notes = notes
        )
        shiftDao.updateShift(closed)

        auditLogDao.insertLog(
            AuditLogEntity(
                userId = user.id,
                userName = user.name,
                userRole = user.role.name,
                action = "SHIFT_CLOSED",
                entityType = "SHIFT",
                entityId = shift.id,
                previousValue = "OPEN",
                newValue = "CLOSED (عجز/فائض: $discrepancy)",
                notes = "إغلاق شفت رقم ${shift.shiftNumber}"
            )
        )

        ShiftResult.Success(closed)
    }

    suspend fun acceptHandover(
        fromShiftId: String,
        currentUser: UserEntity,
        actualReceived: Double,
        notes: String = ""
    ): ShiftResult = db.withTransaction {
        if (!PermissionChecker.hasPermission(currentUser.role, AppPermission.OPERATE_SHIFT)) {
            return@withTransaction ShiftResult.Error("ليس لديك صلاحية استلام تسليم الشفت")
        }
        if (!actualReceived.isFinite() || actualReceived < 0.0) {
            return@withTransaction ShiftResult.Error("النقدية المستلمة يجب أن تكون قيمة غير سالبة وصالحة")
        }
        val fromShift = shiftDao.getShiftById(fromShiftId)
            ?: return@withTransaction ShiftResult.Error("الشفت السابق غير موجود")
        if (fromShift.status != ShiftStatus.CLOSED) {
            return@withTransaction ShiftResult.Error("يجب إغلاق الشفت السابق قبل استلام عهدته")
        }
        if (shiftDao.getHandoverByFromShiftId(fromShiftId) != null) {
            return@withTransaction ShiftResult.Error("تم تسجيل استلام عهدة هذا الشفت مسبقًا")
        }
        val expected = fromShift.leftForNextShiftCash
        if (!expected.isFinite() || expected < 0.0) {
            return@withTransaction ShiftResult.Error("قيمة عهدة الشفت السابق غير صالحة")
        }

        // The target shift and the handover are committed together. If starting a
        // target shift fails, no orphan handover record can be left behind.
        val startResult = startShiftInternal(
            currentUser,
            actualReceived,
            "استلام من شفت " + fromShift.shiftNumber
        )
        if (startResult !is ShiftResult.Success) return@withTransaction startResult
        val newShift = startResult.shift
        val discrepancy = actualReceived - expected
        val handover = ShiftHandoverEntity(
            fromShiftId = fromShiftId,
            fromUserId = fromShift.userId,
            fromUserName = fromShift.userName,
            toShiftId = newShift.id,
            toUserId = currentUser.id,
            toUserName = currentUser.name,
            expectedLeftAmount = expected,
            actualReceivedAmount = actualReceived,
            discrepancy = discrepancy,
            notes = notes
        )
        shiftDao.insertHandover(handover)
        auditLogDao.insertLog(
            AuditLogEntity(
                userId = currentUser.id,
                userName = currentUser.name,
                userRole = currentUser.role.name,
                action = "SHIFT_HANDOVER_ACCEPTED",
                entityType = "SHIFT_HANDOVER",
                entityId = handover.id,
                newValue = "المستلم: " + actualReceived + " ريال؛ المتوقع: " + expected + " ريال",
                notes = "استلام عهدة الشفت رقم " + fromShift.shiftNumber + " إلى الشفت رقم " + newShift.shiftNumber
            )
        )
        startResult
    }
}
