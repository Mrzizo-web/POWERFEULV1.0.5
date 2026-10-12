package com.example.data.engine

import com.example.data.local.entity.ProductEntity

data class CartItem(
    val product: ProductEntity,
    val quantity: Int = 1,
    val unitPrice: Double = product.price,
    val customNotes: String = ""
) {
    val totalPrice: Double get() = quantity * unitPrice
}
