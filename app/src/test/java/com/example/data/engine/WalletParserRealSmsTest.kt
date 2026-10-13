package com.example.data.engine

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class WalletParserRealSmsTest {
    @Test
    fun jeebIncomingTemplateParsesExactAmountAndSender() {
        val parsed = WalletParserRegistry.parseSms(
            "KURAMI",
            "اضيف 5000 ر.ي تحويل مشترك رص:72758.47ر.ي من عبدالله الجميلي-77366225"
        )
        assertNotNull(parsed)
        assertEquals("JEEB", parsed!!.walletCode)
        assertEquals(5000.0, parsed.amount, 0.0001)
        assertEquals("عبدالله الجميلي", parsed.sender)
        assertEquals(WalletDirection.INCOMING, parsed.direction)
    }

    @Test
    fun jeebOutgoingTemplateIsNeverTreatedAsIncoming() {
        val parsed = WalletParserRegistry.parseSms(
            "KURAMI",
            "خصم 2290 ر.ي التحويل الى Wenet Pay رص:53118.47ر.ي الى 22208 فلوسك"
        )
        assertNotNull(parsed)
        assertEquals("JEEB", parsed!!.walletCode)
        assertEquals(WalletDirection.OUTGOING, parsed.direction)
    }

    @Test
    fun floosakIncomingUsesTransferAmountNotBalance() {
        val parsed = WalletParserRegistry.parseSms(
            "YKB",
            "استلمت حوالة من حاشد بزدان بمبلغ 800.00 ر.ي رصيدك 1500.00 ر.ي"
        )
        assertNotNull(parsed)
        assertEquals("FLOOSAK", parsed!!.walletCode)
        assertEquals(800.0, parsed.amount, 0.0001)
        assertEquals("حاشد بزدان", parsed.sender)
        assertEquals(WalletDirection.INCOMING, parsed.direction)
    }

    @Test
    fun jawaliPurchaseTemplateWithoutOptionalLeadingلقدIsAccepted() {
        val parsed = WalletParserRegistry.parseSms(
            "JAWALI",
            "استلمت YER 16000 كقيمة مشتريات بمرجع 468486397181 من عميد محمد احمد احمد"
        )
        assertNotNull(parsed)
        assertEquals("JAWALI", parsed!!.walletCode)
        assertEquals(16000.0, parsed.amount, 0.0001)
        assertEquals("468486397181", parsed.transactionId)
        assertEquals("عميد محمد احمد احمد", parsed.sender)
    }

    @Test
    fun jawaliPurchaseAlsoAcceptsPreviousTemplateAndLocalizedDigits() {
        val previous = WalletParserRegistry.parseSms(
            "JAWALI",
            "لقد استلمت YER 16000 كقيمة مشتريات بمرجع 468486397181 من عميد محمد احمد احمد"
        )
        val localized = WalletParserRegistry.parseSms(
            "JAWALI",
            "استلمت YER ١٦٠٠٠ كقيمة مشتريات بمرجع ٤٦٨٤٨٦٣٩٧١٨١ من عميد محمد احمد احمد"
        )
        assertNotNull(previous)
        assertNotNull(localized)
        assertEquals(16000.0, localized!!.amount, 0.0001)
        assertEquals("468486397181", localized.transactionId)
    }

    @Test
    fun unrelatedOrMalformedMessagesAreRejected() {
        assertNull(WalletParserRegistry.parseSms("JAWALI", "تم استلام رمز التحقق 123456"))
        assertNull(WalletParserRegistry.parseSms("JAWALI", "استلمت YER كقيمة مشتريات بمرجع 123 من مرسل"))
    }
}
