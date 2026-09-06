package com.example.stockwise.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.stockwise.data.entities.Category
import com.example.stockwise.data.entities.Item
import com.example.stockwise.data.entities.ItemWithCategory
import com.example.stockwise.data.repository.CategoryRepository
import com.example.stockwise.data.repository.ItemRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
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

    private val _categories = MutableStateFlow<List<Category>>(emptyList())
    val categories: StateFlow<List<Category>> = _categories.asStateFlow()

    private val _items = MutableStateFlow<List<ItemWithCategory>>(emptyList())
    val items: StateFlow<List<ItemWithCategory>> = _items.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _categorySaveSuccess = MutableStateFlow(false)
    val categorySaveSuccess: StateFlow<Boolean> = _categorySaveSuccess.asStateFlow()

    private val _itemSaveSuccess = MutableStateFlow(false)
    val itemSaveSuccess: StateFlow<Boolean> = _itemSaveSuccess.asStateFlow()

    private val _categoryNames = MutableStateFlow<List<String>>(emptyList())
    val categoryNames: StateFlow<List<String>> = _categoryNames.asStateFlow()

    // ===== LIVE COLLECTION JOBS =====

    private var categoriesJob: Job? = null
    private var itemsJob: Job? = null

    // Guards against loadAllData() being called again (e.g. Fragment view
    // recreated while this ViewModel survives) when collectors are ALREADY
    // running — in that case there's nothing to do, so we skip the
    // cancel+relaunch dance entirely and avoid manufacturing a cancellation
    // in the first place.
    private var collectorsStarted = false

    // ===== INIT =====

    init {
        loadAllData()
    }

    // ===== DATA LOADING (live + non-blocking + cancellation-safe) =====

    fun loadAllData() {
        if (collectorsStarted) return
        collectorsStarted = true
        observeCategories()
        observeItems()
    }

    private fun observeCategories() {
        categoriesJob?.cancel()
        categoriesJob = viewModelScope.launch {
            _isLoading.value = true
            try {
                categoryRepository.getAllActiveCategories().collect { categoryList ->
                    _categories.value = categoryList
                    _categoryNames.value = categoryList.map { it.name }
                    _isLoading.value = false
                }
            } catch (e: CancellationException) {
                // Coroutine was intentionally cancelled (e.g. this job was
                // restarted via observeCategories() being called again, or
                // the ViewModel is being cleared). This is NOT an error —
                // it's cooperative cancellation working as intended, so it
                // must be rethrown, never surfaced to the user.
                throw e
            } catch (e: Exception) {
                _error.value = "Failed to load categories: ${e.message}"
                _isLoading.value = false
            }
        }
    }

    private fun observeItems() {
        itemsJob?.cancel()
        itemsJob = viewModelScope.launch {
            try {
                itemRepository.getAllActiveItemsWithCategory().collect { itemList ->
                    _items.value = itemList
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _error.value = "Failed to load items: ${e.message}"
            }
        }
    }

    /** Manual refresh hooks (e.g. pull-to-refresh) — force-restart the live collectors. */
    fun refreshCategories() = observeCategories()
    fun refreshItems() = observeItems()

    // Kept for source compatibility with any existing callers; now just
    // delegates to the guarded loader so repeated calls are harmless no-ops
    // once collectors are already running.
    fun loadCategories() {
        if (!collectorsStarted) { loadAllData(); return }
        observeCategories()
    }

    fun loadItems() {
        if (!collectorsStarted) { loadAllData(); return }
        observeItems()
    }

    // ===== FILTER BY SINGLE CATEGORY (kept for compatibility, currently
    // unused by the Fragment — filtering is done locally against the live
    // lists above) =====

    fun filterByCategory(categoryName: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                if (categoryName == "All Categories") {
                    itemRepository.getAllActiveItemsWithCategory().collect { itemList ->
                        _items.value = itemList
                    }
                } else {
                    val category = _categories.value.find { it.name.equals(categoryName, ignoreCase = true) }
                    if (category != null) {
                        itemRepository.getActiveItemsByCategory(category.id).collect { itemList ->
                            _items.value = itemList
                        }
                    } else {
                        _error.value = "Category not found"
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _error.value = "Failed to filter items: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    fun filterByCategories(categoryNames: List<String>) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                if (categoryNames.isEmpty() || categoryNames.contains("All Categories")) {
                    itemRepository.getAllActiveItemsWithCategory().collect { itemList ->
                        _items.value = itemList
                    }
                } else {
                    itemRepository.getAllActiveItemsWithCategory().collect { allItems ->
                        val filteredItems = allItems.filter { itemWithCategory ->
                            categoryNames.contains(itemWithCategory.categoryName)
                        }
                        _items.value = filteredItems
                    }
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _error.value = "Failed to filter items: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    // ===== CATEGORY OPERATIONS =====
    // No explicit reload calls after mutations — the live Room Flow already
    // pushes updated data to observeCategories()/observeItems() the instant
    // the DB changes.

    fun saveCategory(name: String, description: String?) {
        viewModelScope.launch {
            _isLoading.value = true
            _categorySaveSuccess.value = false
            try {
                if (name.isBlank()) {
                    _error.value = "Category name cannot be empty"
                    return@launch
                }

                val existingCategory = _categories.value.find { it.name.equals(name, ignoreCase = true) }
                if (existingCategory != null) {
                    _error.value = "Category '$name' already exists"
                    return@launch
                }

                categoryRepository.insertCategory(name, description)
                _categorySaveSuccess.value = true
            } catch (e: CancellationException) {
                throw e
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
            } catch (e: CancellationException) {
                throw e
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
                val itemCount = categoryRepository.getActiveItemCountForCategory(categoryId)
                if (itemCount > 0) {
                    _error.value = "Cannot delete category with existing items. Move or delete items first."
                    return@launch
                }
                categoryRepository.softDeleteCategory(categoryId)
            } catch (e: CancellationException) {
                throw e
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
            } catch (e: CancellationException) {
                throw e
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
            } catch (e: CancellationException) {
                throw e
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
            } catch (e: CancellationException) {
                throw e
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
            } catch (e: CancellationException) {
                throw e
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
            } catch (e: CancellationException) {
                throw e
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
        observeItems()
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