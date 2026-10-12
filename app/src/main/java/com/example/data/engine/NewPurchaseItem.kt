package com.example.data.engine

data class NewPurchaseItem(
    val rawMaterialId: String,
    val rawMaterialName: String,
    val quantity: Double,
    val unit: String,
    val unitPrice: Double
) {
    val totalPrice: Double get() = quantity * unitPrice
}
