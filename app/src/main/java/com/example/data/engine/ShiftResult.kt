package com.example.data.engine

import com.example.data.local.entity.ShiftEntity

sealed class ShiftResult {
    data class Success(val shift: ShiftEntity) : ShiftResult()
    data class Error(val message: String) : ShiftResult()
}
