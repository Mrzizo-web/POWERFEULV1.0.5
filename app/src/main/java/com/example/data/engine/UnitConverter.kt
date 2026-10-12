package com.example.data.engine

/**
 * Converts only between explicitly supported compatible units.
 * Unknown or incompatible units are rejected instead of silently reusing the amount.
 */
object UnitConverter {
    private enum class Family { MASS, VOLUME, COUNT }

    private data class UnitDef(val family: Family, val factorToBase: Double)

    private fun definition(unit: String): UnitDef {
        val normalized = unit.trim().lowercase()
            .replace(" ", "")
            .replace("ـ", "")
        return when (normalized) {
            "g", "gram", "grams", "جرام", "غرام", "جم" -> UnitDef(Family.MASS, 1.0)
            "kg", "kilogram", "kilograms", "كجم", "كيلوجرام", "كيلوغرام" -> UnitDef(Family.MASS, 1000.0)
            "ml", "milliliter", "milliliters", "millilitre", "millilitres", "مل", "ملل" -> UnitDef(Family.VOLUME, 1.0)
            "l", "liter", "liters", "litre", "litres", "لتر" -> UnitDef(Family.VOLUME, 1000.0)
            "piece", "pieces", "pc", "pcs", "حبة", "حبات", "قطعة", "قطع" -> UnitDef(Family.COUNT, 1.0)
            else -> throw IllegalArgumentException("وحدة قياس غير مدعومة: $unit")
        }
    }

    fun convert(amount: Double, fromUnit: String, toUnit: String): Double {
        require(amount.isFinite() && amount >= 0.0) { "الكمية يجب أن تكون رقمًا محدودًا وغير سالبة" }
        val from = definition(fromUnit)
        val to = definition(toUnit)
        require(from.family == to.family) {
            "لا يمكن تحويل وحدة $fromUnit إلى $toUnit؛ الوحدتان غير متوافقتين"
        }
        return amount * from.factorToBase / to.factorToBase
    }
}
