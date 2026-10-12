package com.example

import com.example.data.engine.UnitConverter
import org.junit.Assert.*
import org.junit.Test

class RecipeAndSalesPureUnitTest {

    @Test
    fun testUnitConverterWeightAndVolumeBidirectional() {
        // Grams to Kilograms and vice versa (English)
        assertEquals(1.0, UnitConverter.convert(1000.0, "g", "kg"), 0.0001)
        assertEquals(0.5, UnitConverter.convert(500.0, "g", "kg"), 0.0001)
        assertEquals(1000.0, UnitConverter.convert(1.0, "kg", "g"), 0.0001)
        assertEquals(250.0, UnitConverter.convert(0.25, "kg", "g"), 0.0001)

        // Milliliters to Liters and vice versa (English)
        assertEquals(1.0, UnitConverter.convert(1000.0, "ml", "l"), 0.0001)
        assertEquals(0.25, UnitConverter.convert(250.0, "ml", "l"), 0.0001)
        assertEquals(1000.0, UnitConverter.convert(1.0, "l", "ml"), 0.0001)
        assertEquals(390.0, UnitConverter.convert(0.39, "l", "ml"), 0.0001)

        // Arabic unit representations (جرام / كجم & مل / لتر)
        assertEquals(1.0, UnitConverter.convert(1000.0, "جرام", "كجم"), 0.0001)
        assertEquals(1000.0, UnitConverter.convert(1.0, "كجم", "جرام"), 0.0001)
        assertEquals(1.0, UnitConverter.convert(1000.0, "مل", "لتر"), 0.0001)
        assertEquals(1000.0, UnitConverter.convert(1.0, "لتر", "مل"), 0.0001)
    }

    @Test
    fun testPowerFullRecipeThreePortionsCalculationMultiplication() {
        val singlePortion = mapOf(
            "banana" to 100.0, // g
            "milk" to 130.0,   // ml
            "dates" to 50.0,   // g
            "nuts" to 20.0,    // g
            "oats" to 30.0,    // g
            "honey" to 20.0    // g
        )

        val portionsSold = 3
        val expectedTotalDeduction = singlePortion.mapValues { it.value * portionsSold }

        assertEquals(300.0, expectedTotalDeduction["banana"]!!, 0.001)
        assertEquals(390.0, expectedTotalDeduction["milk"]!!, 0.001)
        assertEquals(150.0, expectedTotalDeduction["dates"]!!, 0.001)
        assertEquals(60.0, expectedTotalDeduction["nuts"]!!, 0.001)
        assertEquals(90.0, expectedTotalDeduction["oats"]!!, 0.001)
        assertEquals(60.0, expectedTotalDeduction["honey"]!!, 0.001)
    }

    @Test(expected = IllegalArgumentException::class)
    fun testIncompatibleUnitConversionIsRejected() {
        UnitConverter.convert(100.0, "g", "ml")
    }

    @Test(expected = IllegalArgumentException::class)
    fun testUnknownUnitIsRejected() {
        UnitConverter.convert(100.0, "scoop", "g")
    }
}
