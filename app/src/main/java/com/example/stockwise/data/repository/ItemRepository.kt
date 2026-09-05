package com.example.stockwise.data.repository


import com.example.stockwise.AppDatabase
import com.example.stockwise.data.entities.Item
import com.example.stockwise.data.entities.ItemWithCategory
import kotlinx.coroutines.flow.Flow
import java.util.Date

class ItemRepository(private val database: AppDatabase) {

    private val itemDao = database.itemDao()

    suspend fun insertItem(item: Item): Long {
        return itemDao.insertItem(item)
    }

    suspend fun updateItem(item: Item) {
        val updatedItem = item.copy(updatedAt = Date())
        itemDao.updateItem(updatedItem)
    }

    suspend fun softDeleteItem(itemId: Long) {
        itemDao.softDeleteItem(itemId)
    }

    suspend fun hardDeleteItem(itemId: Long) {
        itemDao.hardDeleteItem(itemId)
    }

    suspend fun restoreItem(itemId: Long) {
        itemDao.restoreItem(itemId)
    }

    fun getAllActiveItemsWithCategory(): Flow<List<ItemWithCategory>> {
        return itemDao.getAllActiveItemsWithCategory()
    }

    fun getAllItemsWithCategory(): Flow<List<ItemWithCategory>> {
        return itemDao.getAllItemsWithCategory()
    }

    fun getDeletedItemsWithCategory(): Flow<List<ItemWithCategory>> {
        return itemDao.getDeletedItemsWithCategory()
    }

    suspend fun getActiveItemWithCategoryById(itemId: Long): ItemWithCategory? {
        return itemDao.getActiveItemWithCategoryById(itemId)
    }

    fun getActiveItemsByCategory(categoryId: Long): Flow<List<ItemWithCategory>> {
        return itemDao.getActiveItemsByCategory(categoryId)
    }

    fun searchActiveItems(query: String): Flow<List<ItemWithCategory>> {
        return if (query.isBlank()) {
            itemDao.getAllActiveItemsWithCategory()
        } else {
            itemDao.searchActiveItems(query)
        }
    }

    suspend fun getActiveItemCount(): Int {
        return itemDao.getActiveItemCount()
    }

    suspend fun getActiveTotalStockByCategory(categoryId: Long): Int {
        return itemDao.getActiveTotalStockByCategory(categoryId)
    }

    suspend fun getTotalActiveStock(): Int {
        return itemDao.getTotalActiveStock()
    }

    suspend fun getLowStockCount(threshold: Int = 5): Int {
        return itemDao.getLowStockCount(threshold)
    }

    fun getLowStockItems(threshold: Int = 5): Flow<List<ItemWithCategory>> {
        return itemDao.getLowStockItems(threshold)
    }
}