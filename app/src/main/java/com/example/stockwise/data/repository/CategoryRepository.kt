package com.example.stockwise.data.repository

import com.example.stockwise.data.dao.CategoryDao
import com.example.stockwise.data.dao.ItemDao
import com.example.stockwise.data.entities.Category
import kotlinx.coroutines.flow.Flow
import java.util.Date
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CategoryRepository @Inject constructor(
    private val categoryDao: CategoryDao,
    private val itemDao: ItemDao
) {

    suspend fun insertCategory(category: Category): String {
        categoryDao.insertCategory(category)
        return category.id
    }

    suspend fun insertCategory(
        name: String,
        description: String? = null
    ): String {
        val category = Category(
            id = UUID.randomUUID().toString(),
            name = name,
            description = description,
            createdAt = Date(),
            updatedAt = Date()
        )
        categoryDao.insertCategory(category)
        return category.id
    }

    suspend fun updateCategory(category: Category) {
        val updatedCategory = category.copy(updatedAt = Date())
        categoryDao.updateCategory(updatedCategory)
    }

    suspend fun softDeleteCategory(categoryId: String) {
        categoryDao.softDeleteCategory(categoryId, System.currentTimeMillis())
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

    /** NEW: lookup used by the "move to Default" flow. */
    suspend fun getCategoryByName(name: String): Category? {
        return categoryDao.getActiveCategoryByName(name)
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