package com.example.stockwise.data.repository

import com.example.stockwise.AppDatabase
import com.example.stockwise.data.entities.Category
import kotlinx.coroutines.flow.Flow
import java.util.Date

class CategoryRepository(private val database: AppDatabase) {

    private val categoryDao = database.categoryDao()
    private val itemDao = database.itemDao()

    suspend fun insertCategory(category: Category): Long {
        return categoryDao.insertCategory(category)
    }

    suspend fun updateCategory(category: Category) {
        val updatedCategory = category.copy(updatedAt = Date())
        categoryDao.updateCategory(updatedCategory)
    }

    // Soft delete category and all its items
    suspend fun softDeleteCategory(categoryId: Long) {
        categoryDao.softDeleteCategory(categoryId)
        // Soft delete all items in this category
        itemDao.getActiveItemsByCategory(categoryId).collect { items ->
            items.forEach { itemWithCategory ->
                itemDao.softDeleteItem(itemWithCategory.item.id)
            }
        }
    }

    // Hard delete category
    suspend fun hardDeleteCategory(categoryId: Long) {
        // First hard delete all items in this category
        itemDao.hardDeleteItemsByCategory(categoryId)
        // Then hard delete the category
        categoryDao.hardDeleteCategory(categoryId)
    }

    suspend fun restoreCategory(categoryId: Long) {
        categoryDao.restoreCategory(categoryId)
        // Restore all items in this category
        // Note: You might want to handle this differently
    }

    fun getAllActiveCategories(): Flow<List<Category>> {
        return categoryDao.getAllActiveCategories()
    }

    fun getAllCategories(): Flow<List<Category>> {
        return categoryDao.getAllCategories()
    }

    fun getDeletedCategories(): Flow<List<Category>> {
        return categoryDao.getDeletedCategories()
    }

    suspend fun getActiveCategoryById(categoryId: Long): Category? {
        return categoryDao.getActiveCategoryById(categoryId)
    }

    fun searchActiveCategories(query: String): Flow<List<Category>> {
        return if (query.isBlank()) {
            categoryDao.getAllActiveCategories()
        } else {
            categoryDao.searchActiveCategories(query)
        }
    }

    suspend fun getActiveItemCountForCategory(categoryId: Long): Int {
        return categoryDao.getActiveItemCountForCategory(categoryId)
    }

    suspend fun getActiveCategoryCount(): Int {
        return categoryDao.getActiveCategoryCount()
    }
}