package com.example.data.engine

enum class InsightType(val titleAr: String) {
    SALES("تحليل المبيعات"),
    INVENTORY("تنبيه المخزون"),
    COST("تحسين التكلفة"),
    GENERAL("رؤى عامة")
}

data class AiInsight(
    val title: String,
    val description: String,
    val type: InsightType,
    val score: Double = 1.0,
    val timestamp: Long = System.currentTimeMillis()
)
