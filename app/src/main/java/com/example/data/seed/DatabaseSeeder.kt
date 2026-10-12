package com.example.data.seed

import com.example.data.local.AppDatabase
import com.example.data.local.entity.*
import com.example.domain.model.CustomerStatus
import com.example.domain.model.UserRole
import com.example.security.PasswordHasher

object DatabaseSeeder {
    suspend fun seedIfNeeded(db: AppDatabase) {
        // Ensure an owner account exists even if an earlier launch skipped seeding.
        val ownerId = "user-owner-ziad"
        val existingOwner = db.userDao().getUserById(ownerId)
        val existingUserCount = db.userDao().countUsers()
        if (existingOwner == null && existingUserCount == 0) {
            // Never ship or seed a universal PIN. The first login screen guides the
            // owner through choosing a 4-6 digit PIN, which is salted and hashed
            // before the session is opened.
            db.userDao().insertUser(
                UserEntity(
                    id = ownerId,
                    name = "زياد قروش",
                    username = "ziad",
                    pinHash = "",
                    pinSalt = "",
                    role = UserRole.OWNER,
                    phone = "",
                    isActive = true
                )
            )
        }

        // Force re-enrollment for a few known PINs shipped by older builds.
        // This only clears exact known legacy defaults; custom credentials are preserved.
        if (existingOwner != null && existingOwner.role == UserRole.OWNER) {
            val knownLegacyPins = listOf("1234", "775152", "775152713")
            val hasKnownDefault = knownLegacyPins.any { pin ->
                existingOwner.pinHash.isNotBlank() &&
                    existingOwner.pinSalt.isNotBlank() &&
                    PasswordHasher.DEFAULT.verify(pin, existingOwner.pinSalt, existingOwner.pinHash)
            }
            if (hasKnownDefault) {
                db.userDao().updateUser(
                    existingOwner.copy(
                        pinHash = "",
                        pinSalt = "",
                        failedAttempts = 0,
                        lockedUntil = null,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
        }

        // Seed Wallets if not present
        val wallets = db.walletDao().getAllWalletsSync()
        if (wallets.isEmpty()) {
            db.walletDao().insertWallet(
                WalletEntity(
                    id = "wallet-jeeb",
                    code = "JEEB",
                    name = "جيب",
                    enabled = true,
                    iconName = "account_balance_wallet"
                )
            )
            db.walletDao().insertWallet(
                WalletEntity(
                    id = "wallet-floosak",
                    code = "FLOOSAK",
                    name = "فلوسك",
                    enabled = true,
                    iconName = "payments"
                )
            )
            db.walletDao().insertWallet(
                WalletEntity(
                    id = "wallet-jawali",
                    code = "JAWALI",
                    name = "جوالي",
                    enabled = true,
                    iconName = "phone_android"
                )
            )
        }

        // Seed categories & products if none
        val products = db.productDao().getAllProductsSync()
        if (products.isEmpty()) {
            val cat1 = CategoryEntity(id = "cat-shakes", name = "المشروبات والبروتينات", iconName = "local_cafe", displayOrder = 1)
            val cat2 = CategoryEntity(id = "cat-supplements", name = "المكملات الغذائية", iconName = "fitness_center", displayOrder = 2)
            val cat3 = CategoryEntity(id = "cat-snacks", name = "سناكات ووجبات صحية", iconName = "restaurant", displayOrder = 3)

            db.categoryDao().insertCategory(cat1)
            db.categoryDao().insertCategory(cat2)
            db.categoryDao().insertCategory(cat3)

            // Raw Materials
            val rawWhey = RawMaterialEntity(
                id = "mat-whey",
                name = "بودرة واي بروتين فانيلا",
                baseUnit = "جرام",
                currentStock = 5000.0,
                minStock = 500.0,
                avgCostPerUnit = 0.15,
                lastPurchasePrice = 0.15
            )
            val rawMilk = RawMaterialEntity(
                id = "mat-milk",
                name = "حليب خالي الدسم",
                baseUnit = "مل",
                currentStock = 10000.0,
                minStock = 1000.0,
                avgCostPerUnit = 0.01,
                lastPurchasePrice = 0.01
            )
            val rawCreatine = RawMaterialEntity(
                id = "mat-creatine",
                name = "بودرة كرياتين مونوهايدرات",
                baseUnit = "جرام",
                currentStock = 3000.0,
                minStock = 300.0,
                avgCostPerUnit = 0.10,
                lastPurchasePrice = 0.10
            )
            db.rawMaterialDao().insertRawMaterial(rawWhey)
            db.rawMaterialDao().insertRawMaterial(rawMilk)
            db.rawMaterialDao().insertRawMaterial(rawCreatine)

            // Products
            val p1 = ProductEntity(
                id = "prod-whey-shake",
                name = "شيك واي بروتين سوبر",
                categoryId = "cat-shakes",
                price = 25.0,
                costPrice = 8.0,
                recipeId = "recipe-shake"
            )
            val p2 = ProductEntity(
                id = "prod-creatine-shot",
                name = "جرعة كرياتين بطاقة",
                categoryId = "cat-supplements",
                price = 15.0,
                costPrice = 3.0,
                recipeId = "recipe-creatine"
            )
            val p3 = ProductEntity(
                id = "prod-water",
                name = "مياه معدنية 500مل",
                categoryId = "cat-shakes",
                price = 3.0,
                costPrice = 1.0
            )
            db.productDao().insertProduct(p1)
            db.productDao().insertProduct(p2)
            db.productDao().insertProduct(p3)

            // Recipes
            val r1 = RecipeEntity(
                id = "recipe-shake",
                productId = "prod-whey-shake",
                name = "وصفة شيك بروتين سوبر",
                calculatedCost = 8.0
            )
            db.recipeDao().insertRecipe(r1)
            db.recipeDao().insertRecipeItems(
                listOf(
                    RecipeItemEntity(
                        recipeId = "recipe-shake",
                        rawMaterialId = "mat-whey",
                        name = "بودرة واي بروتين",
                        amount = 35.0, quantity = 35.0,
                        unit = "جرام"
                    ),
                    RecipeItemEntity(
                        recipeId = "recipe-shake",
                        rawMaterialId = "mat-milk",
                        name = "حليب خالي الدسم",
                        amount = 250.0, quantity = 250.0,
                        unit = "مل"
                    )
                )
            )

            val r2 = RecipeEntity(
                id = "recipe-creatine",
                productId = "prod-creatine-shot",
                name = "وصفة شوت كرياتين",
                calculatedCost = 3.0
            )
            db.recipeDao().insertRecipe(r2)
            db.recipeDao().insertRecipeItems(
                listOf(
                    RecipeItemEntity(
                        recipeId = "recipe-creatine",
                        rawMaterialId = "mat-creatine",
                        name = "بودرة كرياتين",
                        amount = 5.0, quantity = 5.0,
                        unit = "جرام"
                    )
                )
            )

            // Customer
            db.customerDao().insertCustomer(
                CustomerEntity(
                    id = "cust-1",
                    name = "كابتن أحمد ناصر",
                    phone = "777000111",
                    creditLimit = 500.0,
                    currentDebt = 0.0,
                    allowDebt = true,
                    status = CustomerStatus.ACTIVE
                )
            )
        }
    }
}
