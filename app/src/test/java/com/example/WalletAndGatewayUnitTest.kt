package com.example

import com.example.data.engine.*
import org.junit.Assert.*
import org.junit.Test

class WalletAndGatewayUnitTest {

    @Test
    fun testJaibIncomingRealSamples() {
        val parser = JaibParser()

        val first = "اضيف 5000 ر.ي تحويل مشترك رص:72758.47ر.ي من عبدالله الجميلي-77366225"
        val parsedFirst = parser.parse("Jaib", first)
        assertNotNull(parsedFirst)
        assertEquals("JEEB", parsedFirst?.walletCode)
        assertEquals(5000.0, parsedFirst?.amount ?: 0.0, 0.001)
        assertEquals("عبدالله الجميلي", parsedFirst?.sender)
        assertEquals(WalletDirection.INCOMING, parsedFirst?.direction)

        val second = "اضيف 55000 ر.ي تحويل مشترك رص:55408.47ر.ي من ياسمين محسن علي قروش-7851"
        val parsedSecond = parser.parse("Jaib", second)
        assertNotNull(parsedSecond)
        assertEquals(55000.0, parsedSecond?.amount ?: 0.0, 0.001)
        assertEquals("ياسمين محسن علي قروش", parsedSecond?.sender)
    }

    @Test
    fun testJaibOutgoingIsNotIncoming() {
        val parser = JaibParser()
        val sms = "خصم 2290 ر.ي التحويل الى Wenet Pay رص:53118.47ر.ي الى 22208 فلوسك"

        val parsed = parser.parse("Jaib", sms)
        assertNotNull(parsed)
        assertEquals("JEEB", parsed?.walletCode)
        assertEquals(2290.0, parsed?.amount ?: 0.0, 0.001)
        assertEquals(WalletDirection.OUTGOING, parsed?.direction)
    }

    @Test
    fun testFloosakRealIncomingSample() {
        val parser = FloosakParser()
        val sms = "استلمت حوالة من حاشد بزدان بمبلغ 800.00 ر.ي رصيدك 800.00 ر.ي"

        val parsed = parser.parse("Floosak", sms)
        assertNotNull(parsed)
        assertEquals("FLOOSAK", parsed?.walletCode)
        assertEquals(800.0, parsed?.amount ?: 0.0, 0.001)
        assertEquals("حاشد بزدان", parsed?.sender)
        assertEquals(WalletDirection.INCOMING, parsed?.direction)
        assertEquals("", parsed?.transactionId)
    }

    @Test
    fun testJawaliRealSamples() {
        val parser = JawaliParser()

        val purchase = "لقد استلمت YER 16000 كقيمة مشتريات بمرجع 468486397181 من عميد محمد احمد احمد"
        val parsedPurchase = parser.parse("Jawali", purchase)
        assertNotNull(parsedPurchase)
        assertEquals("JAWALI", parsedPurchase?.walletCode)
        assertEquals(16000.0, parsedPurchase?.amount ?: 0.0, 0.001)
        assertEquals("468486397181", parsedPurchase?.transactionId)
        assertEquals("عميد محمد احمد احمد", parsedPurchase?.sender)

        val direct = "استلمت مبلغ YER 8000 من 77545229 رصيدك هو 54000"
        val parsedDirect = parser.parse("Jawali", direct)
        assertNotNull(parsedDirect)
        assertEquals(8000.0, parsedDirect?.amount ?: 0.0, 0.001)
        assertEquals("77545229", parsedDirect?.sender)
        assertEquals("", parsedDirect?.transactionId)
    }

    @Test
    fun testRegistrySupportsOnlyThreeWallets() {
        val jaib = WalletParserRegistry.parseSms(
            "unknown",
            "اضيف 5000 ر.ي تحويل مشترك رص:72758.47ر.ي من عبدالله الجميلي-77366225"
        )
        val floosak = WalletParserRegistry.parseSms(
            "unknown",
            "استلمت حوالة من حاشد بزدان بمبلغ 800.00 ر.ي رصيدك 800.00 ر.ي"
        )
        val jawali = WalletParserRegistry.parseSms(
            "unknown",
            "لقد استلمت YER 16000 كقيمة مشتريات بمرجع 468486397181 من عميد محمد احمد احمد"
        )

        assertEquals("JEEB", jaib?.walletCode)
        assertEquals("FLOOSAK", floosak?.walletCode)
        assertEquals("JAWALI", jawali?.walletCode)

        val hawalaty = WalletParserRegistry.parseSms(
            "Hawalaty",
            "حوالة واردة بمبلغ 12000 ريال من صرافة النجم"
        )
        assertNull(hawalaty)
    }

    @Test
    fun testInvalidSmsDoesNotParse() {
        val invalid = WalletParserRegistry.parseSms(
            "Telecom",
            "عرض باقات الإنترنت الأسبوعية بـ 500 ريال"
        )
        assertNull(invalid)
    }

    @Test
    fun testUnitConverter() {
        assertEquals(1000.0, UnitConverter.convert(1.0, "KG", "G"), 0.001)
        assertEquals(0.5, UnitConverter.convert(500.0, "G", "KG"), 0.001)
        assertEquals(1000.0, UnitConverter.convert(1.0, "L", "ML"), 0.001)
        assertEquals(0.25, UnitConverter.convert(250.0, "ML", "L"), 0.001)
    }
}
