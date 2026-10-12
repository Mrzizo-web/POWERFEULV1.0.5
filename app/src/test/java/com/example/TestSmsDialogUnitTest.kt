package com.example

import com.example.data.engine.WalletDirection
import com.example.data.engine.WalletParserRegistry
import org.junit.Assert.*
import org.junit.Test

class TestSmsDialogUnitTest {

    @Test
    fun testJeebDialogTemplateParsesSuccessfully() {
        val senderClean = "محمد علي"
        val amountClean = "12000"
        val body = "اضيف $amountClean ر.ي تحويل مشترك رص:75000ر.ي من $senderClean-777123456"

        val parsed = WalletParserRegistry.parseSms("JEEB", body)
        assertNotNull("Jeeb template must parse successfully", parsed)
        assertEquals("JEEB", parsed!!.walletCode)
        assertEquals(12000.0, parsed.amount, 0.001)
        assertEquals("محمد علي", parsed.sender)
        assertEquals(WalletDirection.INCOMING, parsed.direction)
    }

    @Test
    fun testFloosakDialogTemplateParsesSuccessfully() {
        val senderClean = "حاشد أحمد"
        val amountClean = "8500"
        val body = "استلمت حوالة من $senderClean بمبلغ $amountClean ر.ي رصيدك 25000 ر.ي"

        val parsed = WalletParserRegistry.parseSms("FLOOSAK", body)
        assertNotNull("Floosak template must parse successfully", parsed)
        assertEquals("FLOOSAK", parsed!!.walletCode)
        assertEquals(8500.0, parsed.amount, 0.001)
        assertEquals("حاشد أحمد", parsed.sender)
        assertEquals(WalletDirection.INCOMING, parsed.direction)
    }

    @Test
    fun testJawaliDialogTemplateParsesSuccessfully() {
        val senderClean = "عميد محمد"
        val amountClean = "16000"
        val txIdClean = "468486397181"
        val body = "لقد استلمت ر.ي $amountClean كقيمة مشتريات بمرجع $txIdClean من $senderClean"

        val parsed = WalletParserRegistry.parseSms("JAWALI", body)
        assertNotNull("Jawali template must parse successfully", parsed)
        assertEquals("JAWALI", parsed!!.walletCode)
        assertEquals(16000.0, parsed.amount, 0.001)
        assertEquals("عميد محمد", parsed.sender)
        assertEquals(WalletDirection.INCOMING, parsed.direction)
    }
}
