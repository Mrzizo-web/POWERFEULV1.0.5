package com.example.ui.wallet

import com.example.data.engine.WalletParserRegistry
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertEquals
import org.junit.Test

class TestWalletSmsTemplateTest {
    @Test
    fun jawaliTemplateMatchesRealNotificationShape() {
        assertEquals(
            "استلمت YER 5000 كقيمة مشتريات بمرجع 468486397181 من عميل تجريبي",
            buildTestWalletSms("JAWALI", "5000", "468486397181", "عميل تجريبي")
        )
    }

    @Test
    fun floosakTemplateMatchesRealNotificationShape() {
        assertEquals(
            "استلمت حوالة من عميل تجريبي بمبلغ 5000 ر.ي رصيدك 25000 ر.ي",
            buildTestWalletSms("FLOOSAK", "5000", "468486397181", "عميل تجريبي")
        )
    }

    @Test
    fun jeebTemplateMatchesRealNotificationShape() {
        assertEquals(
            "اضيف 5000 ر.ي تحويل مشترك رص:75000ر.ي من عميل تجريبي-777123456",
            buildTestWalletSms("JEEB", "5000", "468486397181", "عميل تجريبي")
        )
    }

    @Test
    fun allSimulatorSamplesAreAcceptedByTheRealParserRegistry() {
        val jawaliBody = buildTestWalletSms("JAWALI", "5000", "468486397181", "عميل تجريبي")
        val jawali = WalletParserRegistry.parseSms("JAWALI", jawaliBody)
        assertNotNull("Jawali simulator sample should parse", jawali)
        assertEquals("JAWALI", jawali!!.walletCode)
        assertEquals(5000.0, jawali.amount, 0.001)
        assertEquals("468486397181", jawali.transactionId)
        assertEquals("عميل تجريبي", jawali.sender)

        val floosakBody = buildTestWalletSms("FLOOSAK", "5000", "468486397181", "عميل تجريبي")
        val floosak = WalletParserRegistry.parseSms("FLOOSAK", floosakBody)
        assertNotNull("Floosak simulator sample should parse", floosak)
        assertEquals("FLOOSAK", floosak!!.walletCode)
        assertEquals(5000.0, floosak.amount, 0.001)
        assertEquals("عميل تجريبي", floosak.sender)

        val jeebBody = buildTestWalletSms("JEEB", "5000", "468486397181", "عميل تجريبي")
        val jeeb = WalletParserRegistry.parseSms("JEEB", jeebBody)
        assertNotNull("Jeeb simulator sample should parse", jeeb)
        assertEquals("JEEB", jeeb!!.walletCode)
        assertEquals(5000.0, jeeb.amount, 0.001)
        assertEquals("عميل تجريبي", jeeb.sender)
    }
}
