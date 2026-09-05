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

    // Soft delete - mark as deleted
    @Query("UPDATE categories SET isDeleted = 1, deletedAt = :deletedAt WHERE id = :categoryId")
    suspend fun softDeleteCategory(categoryId: Long, deletedAt: Long = System.currentTimeMillis())

    // Hard delete - permanently remove
    @Query("DELETE FROM categories WHERE id = :categoryId")
    suspend fun hardDeleteCategory(categoryId: Long)

    // Restore soft deleted
    @Query("UPDATE categories SET isDeleted = 0, deletedAt = NULL WHERE id = :categoryId")
    suspend fun restoreCategory(categoryId: Long)

    // Get all active categories (not deleted)
    @Query("SELECT * FROM categories WHERE isDeleted = 0 ORDER BY name ASC")
    fun getAllActiveCategories(): Flow<List<Category>>

    // Get all categories including deleted
    @Query("SELECT * FROM categories ORDER BY name ASC")
    fun getAllCategories(): Flow<List<Category>>

    // Get only deleted categories
    @Query("SELECT * FROM categories WHERE isDeleted = 1 ORDER BY deletedAt DESC")
    fun getDeletedCategories(): Flow<List<Category>>

    @Query("SELECT * FROM categories WHERE id = :categoryId AND isDeleted = 0")
    suspend fun getActiveCategoryById(categoryId: Long): Category?

    @Query("SELECT * FROM categories WHERE id = :categoryId")
    suspend fun getCategoryById(categoryId: Long): Category?

    @Query("SELECT COUNT(*) FROM items WHERE categoryId = :categoryId AND isDeleted = 0")
    suspend fun getActiveItemCountForCategory(categoryId: Long): Int

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