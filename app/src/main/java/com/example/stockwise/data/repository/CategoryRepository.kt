package com.example.stockwise.data.repository

import com.example.stockwise.AppDatabase
import com.example.stockwise.data.entities.Category
import kotlinx.coroutines.flow.Flow
import java.util.Date
import java.util.UUID

class CategoryRepository(private val database: AppDatabase) {

    private val categoryDao = database.categoryDao()
    private val itemDao = database.itemDao()

    suspend fun insertCategory(category: Category): String {
        categoryDao.insertCategory(category)
        return category.id
    }



    suspend fun updateCategory(category: Category) {
        val updatedCategory = category.copy(updatedAt = Date())
        categoryDao.updateCategory(updatedCategory)
    }

    suspend fun softDeleteCategory(categoryId: String) {
        categoryDao.softDeleteCategory(categoryId)
    }

    fun getAllActiveCategories(): Flow<List<Category>> {
        return categoryDao.getAllActiveCategories()
    }

    fun getDeletedCategories(): Flow<List<Category>> {
        return categoryDao.getDeletedCategories()
    }

    suspend fun getActiveCategoryById(categoryId: String): Category? {
        return categoryDao.getActiveCategoryById(categoryId)
    }

    fun searchActiveCategories(query: String): Flow<List<Category>> {
        return if (query.isBlank()) {
            categoryDao.getAllActiveCategories()
        } else {
            categoryDao.searchActiveCategories(query)
        }
    }

    suspend fun getActiveItemCountForCategory(categoryId: String): Int {
        return categoryDao.getActiveItemCountForCategory(categoryId)
    }

    suspend fun getActiveCategoryCount(): Int {
        return categoryDao.getActiveCategoryCount()
    }
}