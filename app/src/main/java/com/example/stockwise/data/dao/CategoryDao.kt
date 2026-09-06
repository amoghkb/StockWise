package com.example.stockwise.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.stockwise.data.entities.Category
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCategory(category: Category): Long

    @Update
    suspend fun updateCategory(category: Category)

    @Query("UPDATE categories SET isDeleted = 1, deletedAt = :deletedAt WHERE id = :categoryId")
    suspend fun softDeleteCategory(categoryId: String, deletedAt: Long = System.currentTimeMillis())

    @Query("SELECT * FROM categories WHERE isDeleted = 0 ORDER BY name ASC")
    fun getAllActiveCategories(): Flow<List<Category>>

    @Query("SELECT * FROM categories WHERE isDeleted = 1 ORDER BY deletedAt DESC")
    fun getDeletedCategories(): Flow<List<Category>>

    @Query("SELECT * FROM categories WHERE id = :categoryId AND isDeleted = 0")
    suspend fun getActiveCategoryById(categoryId: String): Category?

    @Query("SELECT COUNT(*) FROM items WHERE categoryId = :categoryId AND isDeleted = 0")
    suspend fun getActiveItemCountForCategory(categoryId: String): Int

    @Query("""
        SELECT * FROM categories 
        WHERE isDeleted = 0 
        AND (name LIKE '%' || :query || '%' 
        OR description LIKE '%' || :query || '%')
        ORDER BY name ASC
    """)
    fun searchActiveCategories(query: String): Flow<List<Category>>

    @Query("SELECT COUNT(*) FROM categories WHERE isDeleted = 0")
    suspend fun getActiveCategoryCount(): Int
}