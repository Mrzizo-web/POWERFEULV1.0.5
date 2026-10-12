package com.example.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.example.data.local.dao.*
import com.example.data.local.entity.*
import com.example.data.local.migration.MIGRATION_1_2
import com.example.data.local.migration.MIGRATION_2_3

@Database(
    entities = [
        UserEntity::class,
        CategoryEntity::class,
        ProductEntity::class,
        RawMaterialEntity::class,
        RecipeEntity::class,
        RecipeItemEntity::class,
        MixtureEntity::class,
        MixtureItemEntity::class,
        SaleEntity::class,
        SaleItemEntity::class,
        ShiftEntity::class,
        ShiftCashMovementEntity::class,
        ShiftHandoverEntity::class,
        StockAdjustmentEntity::class,
        StockCountEntity::class,
        SupplierEntity::class,
        CustomerEntity::class,
        DebtTransactionEntity::class,
        ExpenseEntity::class,
        InventoryTransactionEntity::class,
        PurchaseEntity::class,
        PurchaseItemEntity::class,
        WasteTransactionEntity::class,
        CafeteriaSettingEntity::class,
        AuditLogEntity::class,
        WalletEntity::class,
        WalletSmsEntity::class,
        WalletTransactionEntity::class,
        GatewayQueueEntity::class
    ],
    version = 3,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun categoryDao(): CategoryDao
    abstract fun productDao(): ProductDao
    abstract fun rawMaterialDao(): RawMaterialDao
    abstract fun recipeDao(): RecipeDao
    abstract fun mixtureDao(): MixtureDao
    abstract fun saleDao(): SaleDao
    abstract fun shiftDao(): ShiftDao
    abstract fun customerDao(): CustomerDao
    abstract fun debtTransactionDao(): DebtTransactionDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun inventoryTransactionDao(): InventoryTransactionDao
    abstract fun purchaseDao(): PurchaseDao
    abstract fun supplierDao(): SupplierDao
    abstract fun stockAdjustmentDao(): StockAdjustmentDao
    abstract fun wasteDao(): WasteDao
    abstract fun settingsDao(): SettingsDao
    abstract fun auditLogDao(): AuditLogDao
    abstract fun walletDao(): WalletDao
    abstract fun gatewayQueueDao(): GatewayQueueDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "power_feul_pos_db"
                )
                     .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
