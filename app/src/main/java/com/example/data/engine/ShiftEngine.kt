package com.example.data.engine

import androidx.room.withTransaction
import com.example.data.local.AppDatabase
import com.example.data.local.entity.AuditLogEntity
import com.example.data.local.entity.ShiftEntity
import com.example.data.local.entity.ShiftHandoverEntity
import com.example.data.local.entity.UserEntity
import com.example.domain.model.ShiftStatus

class ShiftEngine(private val db: AppDatabase) {
    private val shiftDao = db.shiftDao()
    private val auditLogDao = db.auditLogDao()

    suspend fun startShift(
        user: UserEntity,
        openingCash: Double,
        notes: String = ""
    ): ShiftResult = db.withTransaction {
        startShiftInternal(user, openingCash, notes)
    }

    private suspend fun startShiftInternal(
        user: UserEntity,
        openingCash: Double,
        notes: String = ""
    ): ShiftResult {
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
        val fromShift = shiftDao.getShiftById(fromShiftId)
            ?: return@withTransaction ShiftResult.Error("الشفت السابق غير موجود")

        val expected = fromShift.leftForNextShiftCash
        val discrepancy = actualReceived - expected

        val handover = ShiftHandoverEntity(
            fromShiftId = fromShiftId,
            fromUserId = fromShift.userId,
            fromUserName = fromShift.userName,
            toUserId = currentUser.id,
            toUserName = currentUser.name,
            expectedLeftAmount = expected,
            actualReceivedAmount = actualReceived,
            discrepancy = discrepancy,
            notes = notes
        )
        shiftDao.insertHandover(handover)

        startShiftInternal(
            currentUser,
            actualReceived,
            "استلام من شفت ${fromShift.shiftNumber}"
        )
    }
}
