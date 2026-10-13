package com.example.ui.wallet

import org.junit.Assert.assertEquals
import org.junit.Test

class TestWalletSmsTemplateTest {
    @Test
    fun jawaliTemplateMatchesRealNotificationShape() {
        assertEquals(
            "استلمت YER 5000 كقيمة مشتريات بمرجع TX-42 من عميل تجريبي",
            buildTestWalletSms("JAWALI", "5000", "TX-42", "عميل تجريبي")
        )
    }

    @Test
    fun floosakTemplateMatchesRealNotificationShape() {
        assertEquals(
            "استلمت حوالة من عميل تجريبي بمبلغ 5000 ر.ي رصيدك 25000 ر.ي",
            buildTestWalletSms("FLOOSAK", "5000", "TX-42", "عميل تجريبي")
        )
    }

    @Test
    fun jeebTemplateMatchesRealNotificationShape() {
        assertEquals(
            "اضيف 5000 ر.ي تحويل مشترك رص:75000ر.ي من عميل تجريبي-777123456",
            buildTestWalletSms("JEEB", "5000", "TX-42", "عميل تجريبي")
        )
    }
}
