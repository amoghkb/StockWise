package com.example.stockwise.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.stockwise.data.entities.Category
import com.example.stockwise.data.entities.Item
import com.example.stockwise.data.entities.ItemWithCategory
import com.example.stockwise.data.repository.CategoryRepository
import com.example.stockwise.data.repository.ItemRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class ProductsViewModel @Inject constructor(
    private val categoryRepository: CategoryRepository,
    private val itemRepository: ItemRepository
) : ViewModel() {

    // ===== STATE =====

    // Categories
    private val _categories = MutableStateFlow<List<Category>>(emptyList())
    val categories: StateFlow<List<Category>> = _categories.asStateFlow()

    // Items
    private val _items = MutableStateFlow<List<ItemWithCategory>>(emptyList())
    val items: StateFlow<List<ItemWithCategory>> = _items.asStateFlow()

    // Loading states
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // Error states
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // Success states
    private val _categorySaveSuccess = MutableStateFlow(false)
    val categorySaveSuccess: StateFlow<Boolean> = _categorySaveSuccess.asStateFlow()

    private val _itemSaveSuccess = MutableStateFlow(false)
    val itemSaveSuccess: StateFlow<Boolean> = _itemSaveSuccess.asStateFlow()

    // Category dropdown items (just names for display)
    private val _categoryNames = MutableStateFlow<List<String>>(emptyList())
    val categoryNames: StateFlow<List<String>> = _categoryNames.asStateFlow()

    // ===== INIT =====

    init {
        loadAllData()
    }

    // ===== DATA LOADING =====

    fun loadAllData() {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // Load categories
                categoryRepository.getAllActiveCategories().collect { categoryList ->
                    _categories.value = categoryList
                    _categoryNames.value = categoryList.map { it.name }
                }

                // Load items
                itemRepository.getAllActiveItemsWithCategory().collect { itemList ->
                    _items.value = itemList
                }
            } catch (e: Exception) {
                _error.value = "Failed to load data: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun loadCategories() {
        viewModelScope.launch {
            try {
                categoryRepository.getAllActiveCategories().collect { categoryList ->
                    _categories.value = categoryList
                    _categoryNames.value = categoryList.map { it.name }
                }
            } catch (e: Exception) {
                _error.value = "Failed to load categories: ${e.message}"
            }
        }
    }

    fun loadItems() {
        viewModelScope.launch {
            try {
                itemRepository.getAllActiveItemsWithCategory().collect { itemList ->
                    _items.value = itemList
                }
            } catch (e: Exception) {
                _error.value = "Failed to load items: ${e.message}"
            }
        }
    }

    // ===== CATEGORY OPERATIONS =====

    fun saveCategory(name: String, description: String?) {
        viewModelScope.launch {
            _isLoading.value = true
            _categorySaveSuccess.value = false
            try {
                // Validation
                if (name.isBlank()) {
                    _error.value = "Category name cannot be empty"
                    return@launch
                }

                // Check for duplicate category name
                val existingCategory = _categories.value.find { it.name.equals(name, ignoreCase = true) }
                if (existingCategory != null) {
                    _error.value = "Category '$name' already exists"
                    return@launch
                }

                // Use the convenience method in repository
                categoryRepository.insertCategory(name, description)
                _categorySaveSuccess.value = true
                loadCategories() // Refresh categories
            } catch (e: Exception) {
                _error.value = "Failed to save category: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun updateCategory(category: Category) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                categoryRepository.updateCategory(category)
                loadCategories()
            } catch (e: Exception) {
                _error.value = "Failed to update category: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun deleteCategory(categoryId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                // Check if category has items before deleting
                val itemCount = categoryRepository.getActiveItemCountForCategory(categoryId)
                if (itemCount > 0) {
                    _error.value = "Cannot delete category with existing items. Move or delete items first."
                    return@launch
                }
                categoryRepository.softDeleteCategory(categoryId)
                loadCategories()
            } catch (e: Exception) {
                _error.value = "Failed to delete category: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun getCategoryById(categoryId: String): Category? {
        return _categories.value.find { it.id == categoryId }
    }

    fun getCategoryByName(name: String): Category? {
        return _categories.value.find { it.name.equals(name, ignoreCase = true) }
    }

    // ===== ITEM OPERATIONS =====

    fun saveItem(
        categoryId: String,
        name: String,
        originalPrice: Double,
        sellingPrice: Double,
        stock: Int,
        description: String? = null,
        imageUri: String? = null
    ) {
        viewModelScope.launch {
            _isLoading.value = true
            _itemSaveSuccess.value = false
            try {
                // Validate inputs
                if (name.isBlank()) {
                    _error.value = "Item name cannot be empty"
                    return@launch
                }

                val category = getCategoryById(categoryId)
                if (category == null) {
                    _error.value = "Selected category not found"
                    return@launch
                }

                if (originalPrice < 0) {
                    _error.value = "Original price cannot be negative"
                    return@launch
                }

                if (sellingPrice < 0) {
                    _error.value = "Selling price cannot be negative"
                    return@launch
                }

                if (stock < 0) {
                    _error.value = "Stock quantity cannot be negative"
                    return@launch
                }

                itemRepository.insertItem(
                    categoryId = categoryId,
                    name = name,
                    originalPrice = originalPrice,
                    sellingPrice = sellingPrice,
                    stock = stock,
                    description = description,
                    imageUri = imageUri
                )
                _itemSaveSuccess.value = true
                loadItems() // Refresh items
            } catch (e: Exception) {
                _error.value = "Failed to save item: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun updateItem(item: Item) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                itemRepository.updateItem(item)
                loadItems()
            } catch (e: Exception) {
                _error.value = "Failed to update item: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun deleteItem(itemId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                itemRepository.softDeleteItem(itemId)
                loadItems()
            } catch (e: Exception) {
                _error.value = "Failed to delete item: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun searchItems(query: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                itemRepository.searchActiveItems(query).collect { itemList ->
                    _items.value = itemList
                }
            } catch (e: Exception) {
                _error.value = "Failed to search items: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun getLowStockItems(threshold: Int = 5) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                itemRepository.getLowStockItems(threshold).collect { itemList ->
                    _items.value = itemList
                }
            } catch (e: Exception) {
                _error.value = "Failed to load low stock items: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun getItemCount(): Int {
        return _items.value.size
    }

    fun getTotalStock(): Int {
        return _items.value.sumOf { it.item.stock }
    }

    // ===== UTILITY FUNCTIONS =====

    fun clearError() {
        _error.value = null
    }

    fun clearSaveSuccess() {
        _categorySaveSuccess.value = false
        _itemSaveSuccess.value = false
    }

    fun resetSearch() {
        loadItems()
    }

    // ===== VALIDATION HELPERS =====

    fun validateItemInput(
        name: String,
        categoryName: String,
        originalPriceStr: String,
        sellingPriceStr: String,
        stockStr: String
    ): ValidationResult {
        val errors = mutableListOf<String>()

        if (name.isBlank()) {
            errors.add("Please enter an item name")
        }

        if (categoryName.isBlank()) {
            errors.add("Please select a category")
        } else {
            val category = getCategoryByName(categoryName)
            if (category == null) {
                errors.add("Selected category not found")
            }
        }

        val originalPrice = originalPriceStr.toDoubleOrNull()
        if (originalPrice == null || originalPrice < 0) {
            errors.add("Please enter a valid original price")
        }

        val sellingPrice = sellingPriceStr.toDoubleOrNull()
        if (sellingPrice == null || sellingPrice < 0) {
            errors.add("Please enter a valid selling price")
        }

        val stock = stockStr.toIntOrNull()
        if (stock == null || stock < 0) {
            errors.add("Please enter a valid stock quantity")
        }

        return ValidationResult(
            isValid = errors.isEmpty(),
            errors = errors,
            originalPrice = originalPrice ?: 0.0,
            sellingPrice = sellingPrice ?: 0.0,
            stock = stock ?: 0,
            category = getCategoryByName(categoryName)
        )
    }

    data class ValidationResult(
        val isValid: Boolean,
        val errors: List<String> = emptyList(),
        val originalPrice: Double = 0.0,
        val sellingPrice: Double = 0.0,
        val stock: Int = 0,
        val category: Category? = null
    )
}