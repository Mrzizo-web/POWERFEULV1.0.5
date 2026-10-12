package com.example

import com.example.data.engine.CartItem
import com.example.data.local.entity.ProductEntity
import com.example.security.PasswordHasher
import org.junit.Assert.*
import org.junit.Test

class OwnerAndSalesUnitTest {

    @Test
    fun testOwnerChosenPinVerification() {
        val chosenPin = "4826"
        val credentials = PasswordHasher.DEFAULT.hash(chosenPin)
        assertTrue(PasswordHasher.DEFAULT.verify(chosenPin, credentials.saltHex, credentials.hashHex))
        assertFalse(PasswordHasher.DEFAULT.verify("0000", credentials.saltHex, credentials.hashHex))
        assertFalse(PasswordHasher.DEFAULT.verify("4827", credentials.saltHex, credentials.hashHex))
    }

    @Test
    fun testFinancialCartCalculationsIntegrity() {
        val p1 = ProductEntity(id = "p1", name = "Product 1", categoryId = "cat1", price = 25.0, costPrice = 10.0)
        val p2 = ProductEntity(id = "p2", name = "Product 2", categoryId = "cat1", price = 15.0, costPrice = 5.0)

        val items = listOf(
            CartItem(product = p1, quantity = 2, unitPrice = 25.0),
            CartItem(product = p2, quantity = 3, unitPrice = 15.0)
        )

        val totalAmount = items.sumOf { it.totalPrice }
        val totalCost = items.sumOf { it.product.costPrice * it.quantity }
        val discount = 5.0
        val finalAmount = (totalAmount - discount).coerceAtLeast(0.0)
        val profit = finalAmount - totalCost

        assertEquals(95.0, totalAmount, 0.001)
        assertEquals(35.0, totalCost, 0.001)
        assertEquals(90.0, finalAmount, 0.001)
        assertEquals(55.0, profit, 0.001)
    }

    @Test
    fun testShiftExpectedCashFormula() {
        val openingCash = 1000.0
        val cashSales = 5000.0
        val manualCashIn = 500.0
        val manualCashOut = 200.0
        val expenses = 300.0

        val expectedCash = openingCash + cashSales + manualCashIn - manualCashOut - expenses
        assertEquals(6000.0, expectedCash, 0.001)

        val actualCash = 5950.0
        val difference = actualCash - expectedCash
        assertEquals(-50.0, difference, 0.001)
    }
}
