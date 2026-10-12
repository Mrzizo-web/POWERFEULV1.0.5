package com.example.data.local.dao

import androidx.room.*
import com.example.data.local.entity.RecipeEntity
import com.example.data.local.entity.RecipeItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface RecipeDao {
    @Query("SELECT * FROM recipes ORDER BY name ASC")
    fun getAllRecipes(): Flow<List<RecipeEntity>>

    @Query("SELECT * FROM recipes")
    suspend fun getAllRecipesSync(): List<RecipeEntity>

    @Query("SELECT * FROM recipes WHERE productId = :productId AND isActive = 1 LIMIT 1")
    fun getActiveRecipeForProduct(productId: String): Flow<RecipeEntity?>

    @Query("SELECT * FROM recipes WHERE productId = :productId AND isActive = 1 LIMIT 1")
    suspend fun getActiveRecipeForProductSync(productId: String): RecipeEntity?

    @Query("SELECT * FROM recipes WHERE id = :id LIMIT 1")
    suspend fun getRecipeById(id: String): RecipeEntity?

    @Query("SELECT * FROM recipe_items WHERE recipeId = :recipeId")
    fun getRecipeItems(recipeId: String): Flow<List<RecipeItemEntity>>

    @Query("SELECT * FROM recipe_items WHERE recipeId = :recipeId")
    suspend fun getRecipeItemsSync(recipeId: String): List<RecipeItemEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecipe(recipe: RecipeEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRecipeItems(items: List<RecipeItemEntity>)

    @Query("DELETE FROM recipe_items WHERE recipeId = :recipeId")
    suspend fun deleteRecipeItems(recipeId: String)
}
