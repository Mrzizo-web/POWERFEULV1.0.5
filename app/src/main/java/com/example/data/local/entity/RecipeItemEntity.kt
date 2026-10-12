package com.example.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "recipe_items")
data class RecipeItemEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val recipeId: String,
    val rawMaterialId: String,
    val mixtureId: String = "",
    val name: String = "",
    val amount: Double = 0.0, // used in calculations
    val quantity: Double = 0.0,
    val unit: String = "",
    val costContribution: Double = 0.0
)
