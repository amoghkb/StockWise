package com.example.stockwise.viewmodels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.stockwise.data.entities.DailySalesSummary
import com.example.stockwise.data.entities.Item
import com.example.stockwise.data.entities.ItemWithCategoryAndVehicles
import com.example.stockwise.data.entities.ProcurementEntity
import com.example.stockwise.data.repository.ItemRepository
import com.example.stockwise.data.repository.ProcurementRepository
import com.example.stockwise.data.repository.SalesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import javax.inject.Inject

@HiltViewModel
class SharedDataViewModel @Inject constructor(
    private val itemRepository: ItemRepository,
    private val salesRepository: SalesRepository,
    private val procurementRepository: ProcurementRepository
) : ViewModel() {

    // ===== DASHBOARD DATA =====

    private val _todaySales = MutableStateFlow("₹0")
    val todaySales: StateFlow<String> = _todaySales.asStateFlow()

    private val _stockAlertsCount = MutableStateFlow("0")
    val stockAlertsCount: StateFlow<String> = _stockAlertsCount.asStateFlow()

    private val _totalItems = MutableStateFlow("0")
    val totalItems: StateFlow<String> = _totalItems.asStateFlow()

    private val _lowStockItems = MutableStateFlow<List<ItemWithCategoryAndVehicles>>(emptyList())
    val lowStockItems: StateFlow<List<ItemWithCategoryAndVehicles>> = _lowStockItems.asStateFlow()

    private val _weeklySalesData = MutableStateFlow<List<DailySalesSummary>>(emptyList())
    val weeklySalesData: StateFlow<List<DailySalesSummary>> = _weeklySalesData.asStateFlow()

    private val _greeting = MutableStateFlow("")
    val greeting: StateFlow<String> = _greeting.asStateFlow()

    private val _currentDate = MutableStateFlow("")
    val currentDate: StateFlow<String> = _currentDate.asStateFlow()

    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    private val _lastRefreshTime = MutableStateFlow<Long>(0)
    val lastRefreshTime: StateFlow<Long> = _lastRefreshTime.asStateFlow()

    private val _saleSuccessEvent = MutableStateFlow<String?>(null)
    val saleSuccessEvent: StateFlow<String?> = _saleSuccessEvent.asStateFlow()

    fun clearSaleSuccessEvent() { _saleSuccessEvent.value = null }

    init {
        setupGreetingAndDate()
        refreshDashboardData()
    }

    private fun setupGreetingAndDate() {
        val calendar = Calendar.getInstance()
        val hour = calendar.get(Calendar.HOUR_OF_DAY)

        val greetingText = when (hour) {
            in 0..11 -> "Good Morning"
            in 12..16 -> "Good Afternoon"
            in 17..20 -> "Good Evening"
            else -> "Good Night"
        }
        _greeting.value = "$greetingText, Bala!"

        val dateFormat = SimpleDateFormat("EEEE, MMM d", Locale.getDefault())
        _currentDate.value = dateFormat.format(Date())
    }

    fun refreshDashboardData() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            try {
                _lowStockItems.value = emptyList()
                _weeklySalesData.value = emptyList()
                _todaySales.value = "₹0"
                _stockAlertsCount.value = "0"
                _totalItems.value = "0"

                loadTodaySales()
                loadStockAlerts()
                loadTotalItems()
                loadLowStockItems()
                loadWeeklySalesData()
                _lastRefreshTime.value = System.currentTimeMillis()
            } catch (e: Exception) {
                _error.value = e.message ?: "Error loading dashboard data"
                e.printStackTrace()
            } finally {
                _isLoading.value = false
            }
        }
    }

    private suspend fun loadTodaySales() {
        try {
            val stats = salesRepository.getTodayStats()
            val totalAmount = stats?.totalAmount ?: 0.0
            _todaySales.value = "₹${String.format(Locale.getDefault(), "%.0f", totalAmount)}"
        } catch (e: Exception) {
            _todaySales.value = "₹0"
            e.printStackTrace()
        }
    }

    private suspend fun loadStockAlerts() {
        try {
            val items = itemRepository.getAllActiveItemsWithVehicles().first()
            val alertCount = items.count { it.item.stock < 10 }
            _stockAlertsCount.value = alertCount.toString()
        } catch (e: Exception) {
            _stockAlertsCount.value = "0"
            e.printStackTrace()
        }
    }

    private suspend fun loadTotalItems() {
        try {
            val count = itemRepository.getActiveItemCount()
            _totalItems.value = count.toString()
        } catch (e: Exception) {
            _totalItems.value = "0"
            e.printStackTrace()
        }
    }

    private suspend fun loadLowStockItems() {
        try {
            val items = itemRepository.getLowStockItemsWithVehicles(threshold = 10).first()
            _lowStockItems.value = items
        } catch (e: Exception) {
            _lowStockItems.value = emptyList()
            e.printStackTrace()
        }
    }

    private suspend fun loadWeeklySalesData() {
        try {
            val weeklyData = salesRepository.getWeeklySalesBreakdown().first()
            val filledData = fillMissingWeekDays(weeklyData)
            _weeklySalesData.value = filledData
        } catch (e: Exception) {
            _weeklySalesData.value = emptyList()
            e.printStackTrace()
        }
    }

    private fun fillMissingWeekDays(data: List<DailySalesSummary>): List<DailySalesSummary> {
        val result = mutableListOf<DailySalesSummary>()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val dayFormat = SimpleDateFormat("EEE", Locale.US)

        val dataMap = data.associateBy { it.date ?: "" }

        for (i in 6 downTo 0) {
            val calendar = Calendar.getInstance()
            calendar.add(Calendar.DAY_OF_MONTH, -i)
            val date = calendar.time

            val dateStr = dateFormat.format(date)
            val dayName = dayFormat.format(date)

            val existingData = dataMap[dateStr]
            val amount = existingData?.totalAmount ?: 0.0

            result.add(
                DailySalesSummary(
                    date = dayName,
                    totalAmount = amount,
                    totalItemsSold = existingData?.totalItemsSold ?: 0,
                    totalProfit = existingData?.totalProfit ?: 0.0
                )
            )
        }
        return result
    }

    fun getStockProgress(item: ItemWithCategoryAndVehicles): Int {
        val currentStock = item.item.stock
        if (currentStock <= 0) return 0
        val maxStock = 10
        val progress = (currentStock.toFloat() / maxStock * 100).toInt()
        return progress.coerceIn(0, 100)
    }

    fun getStockProgressColor(item: ItemWithCategoryAndVehicles): Int {
        val stock = item.item.stock
        return when {
            stock <= 3 -> com.example.stockwise.R.color.progress_red
            stock <= 5 -> com.example.stockwise.R.color.progress_orange
            else -> com.example.stockwise.R.color.progress_blue
        }
    }

    fun forceRefresh() {
        refreshDashboardData()
    }

    // ============================================================
    // CART SALE SUPPORT METHODS
    // ============================================================

    suspend fun getAllItemsOnce(): List<Item> {
        return try {
            itemRepository.getAllActiveItemsWithCategory().first().map { it.item }
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    suspend fun recordSale(
        itemId: String,
        itemName: String,
        quantity: Int,
        sellingPrice: Double,
        originalPrice: Double
    ) {
        salesRepository.recordSale(
            itemId = itemId,
            itemName = itemName,
            quantity = quantity,
            sellingPrice = sellingPrice,
            originalPrice = originalPrice
        )
    }

    suspend fun updateItemStock(itemId: String, newStock: Int) {
        try {
            val item = itemRepository.getItemById(itemId) ?: return
            itemRepository.updateItem(
                item.copy(
                    stock = newStock,
                    updatedAt = Date()
                )
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun completeCartSale(cartItems: List<Pair<Item, Pair<Int, Double>>>): Double {
        var totalRevenue = 0.0

        for ((item, qtyPrice) in cartItems) {
            val (quantity, price) = qtyPrice
            try {
                salesRepository.recordSale(
                    itemId = item.id,
                    itemName = item.name,
                    quantity = quantity,
                    sellingPrice = price,
                    originalPrice = item.originalPrice
                )

                val freshItem = itemRepository.getItemById(item.id)
                if (freshItem != null) {
                    itemRepository.updateItem(
                        freshItem.copy(
                            stock = (freshItem.stock - quantity).coerceAtLeast(0),
                            updatedAt = Date()
                        )
                    )
                }

                totalRevenue += quantity * price
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        refreshDashboardData()
        _saleSuccessEvent.value = "Sale completed: ₹${
            String.format(Locale.getDefault(), "%.0f", totalRevenue)
        }"
        return totalRevenue
    }

    // ============================================================
    // PROCUREMENT SUPPORT
    // ============================================================

    suspend fun getProcurementItemsOnce(dateKey: String): List<ProcurementEntity> {
        return try {
            procurementRepository.getItemsForDateOnce(dateKey)
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    fun getProcurementItemsFlow(dateKey: String): Flow<List<ProcurementEntity>> =
        procurementRepository.getItemsForDate(dateKey)

    suspend fun addProcurementItem(entity: ProcurementEntity): Long {
        return try {
            procurementRepository.addOrUpdate(entity)
        } catch (e: Exception) {
            e.printStackTrace()
            -1L
        }
    }

    suspend fun insertProcurementItem(entity: ProcurementEntity): Long {
        return try {
            procurementRepository.insert(entity)
        } catch (e: Exception) {
            e.printStackTrace()
            -1L
        }
    }

    suspend fun updateProcurementItem(entity: ProcurementEntity) {
        try {
            procurementRepository.updateItem(entity)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun deleteProcurementItem(entity: ProcurementEntity) {
        try {
            procurementRepository.deleteItem(entity)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun deleteProcurementById(id: Long) {
        try {
            procurementRepository.deleteById(id)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun deleteProcurementForDate(dateKey: String) {
        try {
            procurementRepository.deleteForDate(dateKey)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    suspend fun countProcurementForDate(dateKey: String): Int {
        return try {
            procurementRepository.countForDate(dateKey)
        } catch (e: Exception) {
            0
        }
    }

    fun getAllProcurementDateKeys(): Flow<List<String>> =
        procurementRepository.getAllActiveDateKeys()
}