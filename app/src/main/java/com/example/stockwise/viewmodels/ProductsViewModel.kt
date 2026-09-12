package com.example.stockwise.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.stockwise.data.entities.Category
import com.example.stockwise.data.entities.Item
import com.example.stockwise.data.entities.ItemWithCategory
import com.example.stockwise.data.entities.Vehicle
import com.example.stockwise.data.entities.VehicleType
import com.example.stockwise.data.repository.CategoryRepository
import com.example.stockwise.data.repository.ItemRepository
import com.example.stockwise.data.repository.SalesRepository
import com.example.stockwise.data.repository.VehicleRepository
import com.example.stockwise.ui.model.ProductListItem
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.Date
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class ProductsViewModel @Inject constructor(
    private val categoryRepository: CategoryRepository,
    private val itemRepository: ItemRepository,
    private val vehicleRepository: VehicleRepository,
    private val salesRepository: SalesRepository
) : ViewModel() {

    companion object {
        /** Fallback category name for items whose parent category is deleted. */
        const val DEFAULT_CATEGORY_NAME = "Default"
    }

    // ===== RAW STATE =====

    private val _categories = MutableStateFlow<List<Category>>(emptyList())
    val categories: StateFlow<List<Category>> = _categories.asStateFlow()

    private val _items = MutableStateFlow<List<ItemWithCategory>>(emptyList())
    val items: StateFlow<List<ItemWithCategory>> = _items.asStateFlow()

    private val _allVehicles = MutableStateFlow<List<Vehicle>>(emptyList())
    val allVehicles: StateFlow<List<Vehicle>> = _allVehicles.asStateFlow()

    private val _vehicleSaveSuccess = MutableStateFlow(false)
    val vehicleSaveSuccess: StateFlow<Boolean> = _vehicleSaveSuccess.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _categorySaveSuccess = MutableStateFlow(false)
    val categorySaveSuccess: StateFlow<Boolean> = _categorySaveSuccess.asStateFlow()

    private val _itemSaveSuccess = MutableStateFlow(false)
    val itemSaveSuccess: StateFlow<Boolean> = _itemSaveSuccess.asStateFlow()

    private val _categoryDeleteSuccess = MutableStateFlow(false)
    val categoryDeleteSuccess: StateFlow<Boolean> = _categoryDeleteSuccess.asStateFlow()

    private val _categoryNames = MutableStateFlow<List<String>>(emptyList())
    val categoryNames: StateFlow<List<String>> = _categoryNames.asStateFlow()

    // ===== SEARCH / UI STATE =====

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _expandedCategories = MutableStateFlow<Set<String>>(emptySet())

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    fun toggleCategoryExpansion(categoryId: String) {
        _expandedCategories.value = if (_expandedCategories.value.contains(categoryId)) {
            _expandedCategories.value - categoryId
        } else {
            _expandedCategories.value + categoryId
        }
    }

    @OptIn(FlowPreview::class)
    val displayItems: StateFlow<List<ProductListItem>> = combine(
        _categories,
        _items,
        _searchQuery.debounce(250L),
        _expandedCategories
    ) { cats, itemList, query, expanded ->
        buildDisplayList(cats, itemList, query, expanded)
    }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private fun buildDisplayList(
        categories: List<Category>,
        items: List<ItemWithCategory>,
        query: String,
        expanded: Set<String>
    ): List<ProductListItem> {
        if (categories.isEmpty()) return listOf(ProductListItem.NoCategoriesMessage)

        val trimmedQuery = query.trim()
        val itemsByCategory = items.groupBy { it.item.categoryId }
        val result = mutableListOf<ProductListItem>()
        var anyCategoryShown = false

        categories.forEach { category ->
            val categoryMatches = trimmedQuery.isEmpty() ||
                    category.name.contains(trimmedQuery, ignoreCase = true)

            val itemsInCategory = itemsByCategory[category.id].orEmpty()

            val itemsToShow = if (categoryMatches) {
                itemsInCategory
            } else {
                itemsInCategory.filter { itemWithCategory ->
                    val item = itemWithCategory.item
                    item.name.contains(trimmedQuery, ignoreCase = true) ||
                            item.id.take(8).contains(trimmedQuery, ignoreCase = true)
                }
            }

            if (trimmedQuery.isNotEmpty() && !categoryMatches && itemsToShow.isEmpty()) {
                return@forEach
            }

            anyCategoryShown = true
            val isExpanded = trimmedQuery.isNotEmpty() || expanded.contains(category.id)

            result.add(
                ProductListItem.CategoryHeader(
                    categoryId = category.id,
                    categoryName = category.name,
                    itemCount = itemsToShow.size,
                    isExpanded = isExpanded
                )
            )

            if (isExpanded) {
                if (itemsToShow.isEmpty()) {
                    result.add(ProductListItem.EmptyCategoryMessage(category.id))
                } else {
                    itemsToShow.forEach { result.add(ProductListItem.ProductRow(it)) }
                }
            }
        }

        if (!anyCategoryShown) {
            return listOf(ProductListItem.NoResultsMessage(trimmedQuery))
        }

        return result
    }

    // ===== LIVE COLLECTION JOBS =====

    private var categoriesJob: Job? = null
    private var itemsJob: Job? = null
    private var vehiclesJob: Job? = null
    private var collectorsStarted = false

    init {
        loadAllData()
    }

    fun loadAllData() {
        if (collectorsStarted) return
        collectorsStarted = true
        observeCategories()
        observeItems()
        observeVehicles()
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

    private fun observeVehicles() {
        vehiclesJob?.cancel()
        vehiclesJob = viewModelScope.launch {
            try {
                vehicleRepository.getAllActiveVehicles().collect { vehicleList ->
                    _allVehicles.value = vehicleList
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _error.value = "Failed to load vehicles: ${e.message}"
            }
        }
    }

    suspend fun getAllVehicles(): List<Vehicle> {
        return vehicleRepository.getAllActiveVehicles().firstOrNull() ?: emptyList()
    }

    suspend fun getAssignedVehicleIdsForItem(itemId: String): List<String> {
        return try {
            itemRepository.getAssignedVehicleIdsForItem(itemId)
        } catch (e: Exception) {
            _error.value = "Failed to load assigned vehicles: ${e.message}"
            emptyList()
        }
    }

    fun removeAllVehiclesFromItem(itemId: String) {
        viewModelScope.launch {
            try {
                itemRepository.removeAllVehiclesFromItem(itemId)
            } catch (e: Exception) {
                _error.value = "Failed to remove vehicles: ${e.message}"
            }
        }
    }

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

    fun getCategoryByName(name: String): Category? {
        return _categories.value.find { it.name.equals(name, ignoreCase = true) }
    }

    fun getItemById(itemId: String): Item? {
        return _items.value.find { it.item.id == itemId }?.item
    }

    suspend fun saveItemAndGetId(
        categoryId: String,
        name: String,
        originalPrice: Double,
        sellingPrice: Double,
        stock: Int,
        description: String? = null,
        imageUri: String? = null
    ): String? {
        return try {
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
            itemRepository.insertItem(item)
            _itemSaveSuccess.value = true
            item.id
        } catch (e: Exception) {
            _error.value = "Failed to save item: ${e.message}"
            null
        }
    }

    suspend fun updateItem(item: Item) {
        try {
            itemRepository.updateItem(item)
            _itemSaveSuccess.value = true
        } catch (e: Exception) {
            _error.value = "Failed to update item: ${e.message}"
        }
    }

    fun assignVehiclesToItem(itemId: String, vehicleIds: List<String>) {
        viewModelScope.launch {
            try {
                itemRepository.assignVehiclesToItem(itemId, vehicleIds)
                _itemSaveSuccess.value = true
            } catch (e: Exception) {
                _error.value = "Failed to assign vehicles: ${e.message}"
            }
        }
    }

    fun saveVehicle(
        name: String,
        company: String? = null,
        type: VehicleType? = null,
        model: String? = null,
        description: String? = null
    ) {
        viewModelScope.launch {
            try {
                _isLoading.value = true
                vehicleRepository.insertVehicle(
                    name = name,
                    company = company,
                    type = type,
                    model = model,
                    description = description
                )
                _vehicleSaveSuccess.value = true
            } catch (e: Exception) {
                _error.value = e.message ?: "Failed to save vehicle"
            } finally {
                _isLoading.value = false
            }
        }
    }

    // ==============================
    // CATEGORY DELETE (soft delete)
    // ==============================

    /**
     * Soft-deletes the category. All of its active items get their categoryId
     * reassigned to the "Default" category (which is created if missing).
     */
    fun deleteCategoryAndMoveItems(categoryId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val defaultCategoryId = ensureDefaultCategoryExists()

                val itemsInCategory = _items.value.filter { it.item.categoryId == categoryId }
                itemsInCategory.forEach { itemWithCategory ->
                    val moved = itemWithCategory.item.copy(
                        categoryId = defaultCategoryId,
                        updatedAt = Date()
                    )
                    itemRepository.updateItem(moved)
                }

                categoryRepository.softDeleteCategory(categoryId)
                _expandedCategories.value = _expandedCategories.value - categoryId

                _categoryDeleteSuccess.value = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _error.value = "Failed to delete category: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Soft-deletes the category AND every item inside it.
     */
    fun deleteCategoryAndItems(categoryId: String) {
        viewModelScope.launch {
            _isLoading.value = true
            try {
                val itemsInCategory = _items.value.filter { it.item.categoryId == categoryId }
                itemsInCategory.forEach { itemWithCategory ->
                    itemRepository.softDeleteItem(itemWithCategory.item.id)
                }

                categoryRepository.softDeleteCategory(categoryId)
                _expandedCategories.value = _expandedCategories.value - categoryId

                _categoryDeleteSuccess.value = true
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _error.value = "Failed to delete category: ${e.message}"
            } finally {
                _isLoading.value = false
            }
        }
    }

    /**
     * Returns the id of the "Default" category, creating it if it doesn't exist.
     */
    private suspend fun ensureDefaultCategoryExists(): String {
        // Fast path: currently loaded list.
        _categories.value.find { it.name.equals(DEFAULT_CATEGORY_NAME, ignoreCase = true) }
            ?.let { return it.id }

        // Repository lookup.
        categoryRepository.getCategoryByName(DEFAULT_CATEGORY_NAME)?.let { return it.id }

        // Create it.
        val newId = UUID.randomUUID().toString()
        val defaultCategory = Category(
            id = newId,
            name = DEFAULT_CATEGORY_NAME,
            description = "Auto-created fallback category",
            createdAt = Date(),
            updatedAt = Date()
        )
        categoryRepository.insertCategory(defaultCategory)
        return newId
    }

    fun clearCategoryDeleteSuccess() {
        _categoryDeleteSuccess.value = false
    }

    // ==============================
    // CLEAR HELPERS
    // ==============================


    suspend fun softDeleteItem(itemId: String) {
        try {
            itemRepository.softDeleteItem(itemId)
        } catch (e: Exception) {
            _error.value = "Failed to delete item: ${e.message}"
            throw e
        }
    }
    fun clearVehicleSaveSuccess() { _vehicleSaveSuccess.value = false }
    fun clearError() { _error.value = null }
    fun clearSaveSuccess() {
        _categorySaveSuccess.value = false
        _itemSaveSuccess.value = false
    }

    // ==============================
    // SALES
    // ==============================

    suspend fun recordSale(
        itemId: String,
        itemName: String,
        quantity: Int,
        sellingPrice: Double,
        originalPrice: Double
    ) {
        try {
            salesRepository.recordSale(
                itemId = itemId,
                itemName = itemName,
                quantity = quantity,
                sellingPrice = sellingPrice,
                originalPrice = originalPrice
            )
        } catch (e: Exception) {
            _error.value = "Failed to record sale: ${e.message}"
        }
    }

    suspend fun getTodayStats() = salesRepository.getTodayStats()
    suspend fun getWeeklyStats() = salesRepository.getWeeklyStats()
    suspend fun getMonthlyStats() = salesRepository.getMonthlyStats()
    suspend fun getTodaySalesSummary() = salesRepository.getTodaySalesSummary()
    suspend fun getWeeklySalesSummary() = salesRepository.getWeeklySalesSummary()
    suspend fun getMonthlySalesSummary() = salesRepository.getMonthlySalesSummary()
    suspend fun getTopSellingItems(limit: Int = 10) = salesRepository.getTopSellingItems(limit)
    fun getWeeklySalesBreakdown() = salesRepository.getWeeklySalesBreakdown()
    fun getMonthlySalesBreakdown() = salesRepository.getMonthlySalesBreakdown()
}