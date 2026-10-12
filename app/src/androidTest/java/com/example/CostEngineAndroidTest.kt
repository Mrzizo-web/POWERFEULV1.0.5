package com.example

import androidx.room.Room
import androidx.test.platform.app.InstrumentationRegistry
import com.example.data.engine.CostEngine
import com.example.data.engine.UnitConverter
import com.example.data.local.AppDatabase
import com.example.data.local.entity.MixtureEntity
import com.example.data.local.entity.MixtureItemEntity
import com.example.data.local.entity.RawMaterialEntity
import com.example.data.local.entity.RecipeEntity
import com.example.data.local.entity.RecipeItemEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test

class CostEngineAndroidTest {
    private lateinit var db: AppDatabase
    private lateinit var engine: CostEngine

    @Before
    fun setUp() = runBlocking {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        engine = CostEngine(db.rawMaterialDao(), db.recipeDao(), db.mixtureDao())
        db.rawMaterialDao().insertRawMaterial(
            RawMaterialEntity(
                id = "cost-oats",
                name = "شوفان",
                baseUnit = "g",
                currentStock = 1000.0,
                avgCostPerUnit = 0.5,
                lastPurchasePrice = 0.5
            )
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun recipeMixtureCostConvertsInputUnitsBeforeMultiplyingUnitCost() = runBlocking {
        db.mixtureDao().insertMixture(
            MixtureEntity(
                id = "cost-mixture",
                name = "خلطة شوفان",
                outputQuantity = 100.0,
                unit = "g"
            )
        )
        db.mixtureDao().insertMixtureItems(
            listOf(
                MixtureItemEntity(
                    mixtureId = "cost-mixture",
                    rawMaterialId = "cost-oats",
                    name = "شوفان",
                    quantity = 20.0,
                    unit = "g"
                )
            )
        )
        db.recipeDao().insertRecipe(
            RecipeEntity(
                id = "cost-recipe",
                productId = "cost-product",
                name = "وصفة بالخلطة"
            )
        )
        db.recipeDao().insertRecipeItems(
            listOf(
                RecipeItemEntity(
                    recipeId = "cost-recipe",
                    rawMaterialId = "",
                    mixtureId = "cost-mixture",
                    name = "خلطة شوفان",
                    amount = 0.2,
                    quantity = 0.2,
                    unit = "kg"
                )
            )
        )

        // 20g oats * 0.5 YER/g / 100g output = 0.1 YER/g;
        // 0.2kg mixture = 200g and costs 20 YER.
        assertEquals(20.0, engine.calculateRecipeTotalCost("cost-recipe"), 0.001)
    }

    @Test
    fun incompatibleRecipeUnitsAreRejectedInsteadOfReturningAFalseCost() = runBlocking {
        db.recipeDao().insertRecipe(
            RecipeEntity(id = "bad-cost-recipe", productId = "cost-product", name = "وصفة بوحدات خاطئة")
        )
        db.recipeDao().insertRecipeItems(
            listOf(
                RecipeItemEntity(
                    recipeId = "bad-cost-recipe",
                    rawMaterialId = "cost-oats",
                    name = "شوفان",
                    amount = 50.0,
                    quantity = 50.0,
                    unit = "ml"
                )
            )
        )
        val result = runCatching { engine.calculateRecipeTotalCost("bad-cost-recipe") }
        assertTrue("Incompatible volume/weight units must not produce a fake cost", result.isFailure)
    }
}
