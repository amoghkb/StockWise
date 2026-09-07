package com.example.stockwise.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import com.example.stockwise.data.entities.Item
import com.example.stockwise.data.entities.ItemWithCategory
import kotlinx.coroutines.flow.Flow

@Dao
interface ItemDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertItem(item: Item): Long

    @Update
    suspend fun updateItem(item: Item)

    @Query("UPDATE items SET isDeleted = 1, deletedAt = :deletedAt WHERE id = :itemId")
    suspend fun softDeleteItem(itemId: String, deletedAt: Long = System.currentTimeMillis())

    @Query("""
        SELECT items.*, categories.name as categoryName 
        FROM items 
        INNER JOIN categories ON items.categoryId = categories.id 
        WHERE items.isDeleted = 0 
        ORDER BY items.name ASC
    """)
    fun getAllActiveItemsWithCategory(): Flow<List<ItemWithCategory>>

    @Query("""
        SELECT items.*, categories.name as categoryName 
        FROM items 
        INNER JOIN categories ON items.categoryId = categories.id 
        WHERE items.isDeleted = 1 
        ORDER BY items.deletedAt DESC
    """)
    fun getDeletedItemsWithCategory(): Flow<List<ItemWithCategory>>

    @Query("""
        SELECT items.*, categories.name as categoryName 
        FROM items 
        INNER JOIN categories ON items.categoryId = categories.id 
        WHERE items.id = :itemId AND items.isDeleted = 0
    """)
    suspend fun getActiveItemWithCategoryById(itemId: String): ItemWithCategory?

    @Query("""
        SELECT items.*, categories.name as categoryName 
        FROM items 
        INNER JOIN categories ON items.categoryId = categories.id 
        WHERE items.categoryId = :categoryId AND items.isDeleted = 0
    """)
    fun getActiveItemsByCategory(categoryId: String): Flow<List<ItemWithCategory>>

    @Query("""
        SELECT items.*, categories.name as categoryName 
        FROM items 
        INNER JOIN categories ON items.categoryId = categories.id 
        WHERE items.isDeleted = 0 
        AND (items.name LIKE '%' || :query || '%' 
        OR items.description LIKE '%' || :query || '%'
        OR categories.name LIKE '%' || :query || '%')
        ORDER BY items.name ASC
    """)
    fun searchActiveItems(query: String): Flow<List<ItemWithCategory>>

    @Query("SELECT COUNT(*) FROM items WHERE isDeleted = 0")
    suspend fun getActiveItemCount(): Int

    @Query("SELECT SUM(stock) FROM items WHERE categoryId = :categoryId AND isDeleted = 0")
    suspend fun getActiveTotalStockByCategory(categoryId: String): Int

    @Query("SELECT SUM(stock) FROM items WHERE isDeleted = 0")
    suspend fun getTotalActiveStock(): Int

    @Query("SELECT COUNT(*) FROM items WHERE stock <= :threshold AND isDeleted = 0")
    suspend fun getLowStockCount(threshold: Int = 5): Int

    @Query("""
        SELECT items.*, categories.name as categoryName 
        FROM items 
        INNER JOIN categories ON items.categoryId = categories.id 
        WHERE items.isDeleted = 0 AND items.stock <= :threshold
        ORDER BY items.stock ASC
    """)
    fun getLowStockItems(threshold: Int = 5): Flow<List<ItemWithCategory>>

    @Query("SELECT * FROM items WHERE id = :itemId AND isDeleted = 0")
    suspend fun getItemById(itemId: String): Item?
}