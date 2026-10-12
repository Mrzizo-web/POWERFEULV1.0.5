package com.example.data.engine

object UnitConverter {
    fun convert(amount: Double, fromUnit: String, toUnit: String): Double {
        val from = fromUnit.trim().lowercase()
        val to = toUnit.trim().lowercase()
        if (from == to) return amount

        return when {
            (from == "kg" || from == "كجم") && (to == "g" || to == "جرام") -> amount * 1000.0
            (from == "g" || from == "جرام") && (to == "kg" || to == "كجم") -> amount / 1000.0
            (from == "l" || from == "لتر") && (to == "ml" || to == "مل") -> amount * 1000.0
            (from == "ml" || from == "مل") && (to == "l" || to == "لتر") -> amount / 1000.0
            else -> amount
        }
    }
}
