package com.example.data.local

import androidx.room.TypeConverter
import com.example.domain.model.*

class Converters {
    @TypeConverter
    fun fromUserRole(value: UserRole): String = value.name

    @TypeConverter
    fun toUserRole(value: String): UserRole = try {
        UserRole.valueOf(value)
    } catch (e: Exception) {
        UserRole.CASHIER
    }

    @TypeConverter
    fun fromPaymentMethod(value: PaymentMethod): String = value.name

    @TypeConverter
    fun toPaymentMethod(value: String): PaymentMethod = try {
        PaymentMethod.valueOf(value)
    } catch (e: Exception) {
        PaymentMethod.CASH
    }

    @TypeConverter
    fun fromInventoryTxType(value: InventoryTxType): String = value.name

    @TypeConverter
    fun toInventoryTxType(value: String): InventoryTxType = try {
        InventoryTxType.valueOf(value)
    } catch (e: Exception) {
        InventoryTxType.SALE
    }

    @TypeConverter
    fun fromShiftStatus(value: ShiftStatus): String = value.name

    @TypeConverter
    fun toShiftStatus(value: String): ShiftStatus = try {
        ShiftStatus.valueOf(value)
    } catch (e: Exception) {
        ShiftStatus.OPEN
    }

    @TypeConverter
    fun fromWasteReason(value: WasteReason): String = value.name

    @TypeConverter
    fun toWasteReason(value: String): WasteReason = try {
        WasteReason.valueOf(value)
    } catch (e: Exception) {
        WasteReason.OTHER
    }

    @TypeConverter
    fun fromCustomerStatus(value: CustomerStatus): String = value.name

    @TypeConverter
    fun toCustomerStatus(value: String): CustomerStatus = try {
        CustomerStatus.valueOf(value)
    } catch (e: Exception) {
        CustomerStatus.ACTIVE
    }

    @TypeConverter
    fun fromWalletTransferStatus(value: WalletTransferStatus): String = value.name

    @TypeConverter
    fun toWalletTransferStatus(value: String): WalletTransferStatus = try {
        WalletTransferStatus.valueOf(value)
    } catch (e: Exception) {
        WalletTransferStatus.RECEIVED
    }

    @TypeConverter
    fun fromSmsProcessingStatus(value: SmsProcessingStatus): String = value.name

    @TypeConverter
    fun toSmsProcessingStatus(value: String): SmsProcessingStatus = try {
        SmsProcessingStatus.valueOf(value)
    } catch (e: Exception) {
        SmsProcessingStatus.PROCESSED
    }
}
