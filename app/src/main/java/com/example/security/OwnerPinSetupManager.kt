package com.example.security

import androidx.room.withTransaction
import com.example.data.local.AppDatabase
import com.example.data.local.entity.AuditLogEntity
import com.example.data.local.entity.UserEntity
import com.example.domain.model.UserRole

/**
 * One-time first-run setup for the seeded owner. It refuses to overwrite an
 * existing credential and never records the PIN in audit logs.
 */
class OwnerPinSetupManager(
    private val db: AppDatabase,
    private val passwordHasher: PasswordHasher = PasswordHasher.DEFAULT
) {
    suspend fun setInitialPin(userId: String, pin: String): UserEntity {
        require(pin.matches(Regex("[0-9]{4,6}"))) {
            "رمز PIN يجب أن يتكون من 4 إلى 6 أرقام"
        }
        return db.withTransaction {
            val owner = db.userDao().getUserById(userId)
                ?: throw IllegalStateException("حساب المالك المطلوب غير موجود")
            check(owner.role == UserRole.OWNER && owner.isActive) {
                "إعداد PIN الأول متاح لحساب مالك نشط فقط"
            }
            check(owner.pinHash.isBlank() && owner.pinSalt.isBlank()) {
                "تم إعداد PIN لهذا الحساب مسبقًا؛ استخدم PIN الحالي لتسجيل الدخول"
            }
            check(db.userDao().countActiveOwners() == 1) {
                "تعذر إعداد PIN الأول: يجب وجود حساب مالك نشط واحد فقط"
            }

            val hash = passwordHasher.hash(pin)
            val now = System.currentTimeMillis()
            val updated = owner.copy(
                pinHash = hash.hashHex,
                pinSalt = hash.saltHex,
                failedAttempts = 0,
                lockedUntil = null,
                lastLoginAt = now,
                updatedAt = now
            )
            db.userDao().updateUser(updated)
            db.auditLogDao().insertLog(
                AuditLogEntity(
                    userId = updated.id,
                    userName = updated.name,
                    userRole = updated.role.name,
                    action = "INITIAL_OWNER_PIN_SET",
                    entityType = "USER",
                    entityId = updated.id,
                    notes = "تم إعداد رمز PIN الأول للمالك باستخدام تجزئة وملح عشوائيين"
                )
            )
            updated
        }
    }

    companion object {
        const val INITIAL_OWNER_ID = "user-owner-ziad"
    }
}
