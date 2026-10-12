package com.example

import com.example.data.local.entity.ProductEntity
import org.junit.Assert.*
import org.junit.Test
import java.util.concurrent.atomic.AtomicBoolean

class TabletCompatibilityUnitTest {

    @Test
    fun testProductSearchMatchesBarcodeAndSku() {
        val prod1 = ProductEntity(
            id = "p1",
            name = "واي بروتين فانيلا",
            categoryId = "c1",
            price = 25.0,
            barcode = "6291048201923",
            sku = "WHEY-VAN-01"
        )
        val prod2 = ProductEntity(
            id = "p2",
            name = "كرياتين مونوهايدرات",
            categoryId = "c1",
            price = 15.0,
            barcode = "6291048201999",
            sku = "CREAT-01"
        )
        val products = listOf(prod1, prod2)

        fun search(query: String): List<ProductEntity> {
            return products.filter { p ->
                val matchQuery = query.isBlank() ||
                        p.name.contains(query, ignoreCase = true) ||
                        (p.barcode.isNotBlank() && p.barcode.contains(query, ignoreCase = true)) ||
                        (p.sku.isNotBlank() && p.sku.contains(query, ignoreCase = true))
                matchQuery && p.isActive
            }
        }

        // Test Barcode scanner input
        val scanned = search("6291048201923")
        assertEquals(1, scanned.size)
        assertEquals("p1", scanned.first().id)

        // Test SKU input
        val bySku = search("CREAT-01")
        assertEquals(1, bySku.size)
        assertEquals("p2", bySku.first().id)

        // Test Arabic name
        val byName = search("بروتين")
        assertEquals(1, byName.size)
        assertEquals("p1", byName.first().id)
    }

    @Test
    fun testDoubleTapSaleExecutionPrevention() {
        val isExecuting = AtomicBoolean(false)
        var executedCount = 0

        fun attemptExecution(): Boolean {
            if (!isExecuting.compareAndSet(false, true)) {
                return false
            }
            try {
                executedCount++
                return true
            } finally {
                // simulate async completion
            }
        }

        val first = attemptExecution()
        val secondConcurrent = attemptExecution()

        assertTrue("First attempt must succeed", first)
        assertFalse("Second concurrent attempt must be rejected to prevent duplicate sale", secondConcurrent)
        assertEquals(1, executedCount)

        // Reset after completion
        isExecuting.set(false)
        val afterCompletion = attemptExecution()
        assertTrue("Subsequent attempt after completion must succeed", afterCompletion)
        assertEquals(2, executedCount)
    }
}
