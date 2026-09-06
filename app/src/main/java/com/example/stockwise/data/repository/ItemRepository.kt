package com.example.stockwise.data.repository

import com.example.stockwise.AppDatabase
import com.example.stockwise.data.entities.Item
import com.example.stockwise.data.entities.ItemWithCategory
import kotlinx.coroutines.flow.Flow
import java.util.Date
import java.util.UUID

class ItemRepository(private val database: AppDatabase) {

    private val itemDao = database.itemDao()

    suspend fun insertItem(item: Item): String {
        itemDao.insertItem(item)
        return item.id
    }

    suspend fun insertItem(
        categoryId: String,
        name: String,
        originalPrice: Double,
        sellingPrice: Double,
        stock: Int,
        description: String? = null,
        imageUri: String? = null
    ): String {
        val item = Item(
            id = UUID.randomUUID().toString(),
            categoryId = categoryId,
            name = name,
            originalPrice = originalPrice,
            sellingPrice = sellingPrice,
            stock = stock,
            description = description,
            imageUri = imageUri,
            createdAt = Date(),
            updatedAt = Date()
        )
        itemDao.insertItem(item)
        return item.id
    }

    suspend fun updateItem(item: Item) {
        val updatedItem = item.copy(updatedAt = Date())
        itemDao.updateItem(updatedItem)
    }

    suspend fun softDeleteItem(itemId: String) {
        itemDao.softDeleteItem(itemId)
    }

    fun getAllActiveItemsWithCategory(): Flow<List<ItemWithCategory>> {
        return itemDao.getAllActiveItemsWithCategory()
    }

    fun getDeletedItemsWithCategory(): Flow<List<ItemWithCategory>> {
        return itemDao.getDeletedItemsWithCategory()
    }

    suspend fun getActiveItemWithCategoryById(itemId: String): ItemWithCategory? {
        return itemDao.getActiveItemWithCategoryById(itemId)
    }

    fun getActiveItemsByCategory(categoryId: String): Flow<List<ItemWithCategory>> {
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

    suspend fun getActiveTotalStockByCategory(categoryId: String): Int {
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