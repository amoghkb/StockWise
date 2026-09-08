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
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.firstOrNull
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

    // ===== STATE =====

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

    private val _categoryNames = MutableStateFlow<List<String>>(emptyList())
    val categoryNames: StateFlow<List<String>> = _categoryNames.asStateFlow()

    // ===== LIVE COLLECTION JOBS =====

    private var categoriesJob: Job? = null
    private var itemsJob: Job? = null
    private var vehiclesJob: Job? = null
    private var collectorsStarted = false

    // ===== INIT =====

    init {
        loadAllData()
    }

    // ===== DATA LOADING =====

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

    // ===== GET VEHICLES =====

    suspend fun getAllVehicles(): List<Vehicle> {
        return vehicleRepository.getAllActiveVehicles().firstOrNull() ?: emptyList()
    }

    // ===== GET ASSIGNED VEHICLES FOR ITEM =====

    suspend fun getAssignedVehicleIdsForItem(itemId: String): List<String> {
        return try {
            itemRepository.getAssignedVehicleIdsForItem(itemId)
        } catch (e: Exception) {
            _error.value = "Failed to load assigned vehicles: ${e.message}"
            emptyList()
        }
    }

    // ===== REMOVE ALL VEHICLES FROM ITEM =====

    fun removeAllVehiclesFromItem(itemId: String) {
        viewModelScope.launch {
            try {
                itemRepository.removeAllVehiclesFromItem(itemId)
            } catch (e: Exception) {
                _error.value = "Failed to remove vehicles: ${e.message}"
            }
        }
    }

    // ===== CATEGORY OPERATIONS =====

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

    // ===== ITEM OPERATIONS =====

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

    fun clearVehicleSaveSuccess() {
        _vehicleSaveSuccess.value = false
    }

    fun clearError() {
        _error.value = null
    }

    fun clearSaveSuccess() {
        _categorySaveSuccess.value = false
        _itemSaveSuccess.value = false
    }

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