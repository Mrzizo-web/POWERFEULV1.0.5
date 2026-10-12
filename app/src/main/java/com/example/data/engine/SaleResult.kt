package com.example.data.engine

import com.example.data.local.entity.SaleEntity

sealed class SaleResult {
    data class Success(val sale: SaleEntity, val invoiceNumber: String, val change: Double) : SaleResult()
    data class Error(val message: String) : SaleResult()
}
