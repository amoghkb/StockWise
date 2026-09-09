package com.example.stockwise.viewmodels


import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.stockwise.data.entities.DailySalesSummary
import com.example.stockwise.data.entities.ItemWithCategoryAndVehicles
import com.example.stockwise.data.repository.ItemRepository
import com.example.stockwise.data.repository.SalesRepository
import dagger.hilt.android.lifecycle.HiltViewModel
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
    private val salesRepository: SalesRepository
) : ViewModel() {

    // ===== DASHBOARD DATA =====

    // Today's Sales
    private val _todaySales = MutableStateFlow("₹0")
    val todaySales: StateFlow<String> = _todaySales.asStateFlow()

    // Stock Alerts count (items with stock < 10)
    private val _stockAlertsCount = MutableStateFlow("0")
    val stockAlertsCount: StateFlow<String> = _stockAlertsCount.asStateFlow()

    // Total Items count
    private val _totalItems = MutableStateFlow("0")
    val totalItems: StateFlow<String> = _totalItems.asStateFlow()

    // Low Stock Items (stock < 10)
    private val _lowStockItems = MutableStateFlow<List<ItemWithCategoryAndVehicles>>(emptyList())
    val lowStockItems: StateFlow<List<ItemWithCategoryAndVehicles>> = _lowStockItems.asStateFlow()

    // Weekly Sales Data for Bar Chart
    private val _weeklySalesData = MutableStateFlow<List<DailySalesSummary>>(emptyList())
    val weeklySalesData: StateFlow<List<DailySalesSummary>> = _weeklySalesData.asStateFlow()

    // Greeting and Date
    private val _greeting = MutableStateFlow("")
    val greeting: StateFlow<String> = _greeting.asStateFlow()

    private val _currentDate = MutableStateFlow("")
    val currentDate: StateFlow<String> = _currentDate.asStateFlow()

    // Loading state
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // Error state
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    // Last refresh timestamp (for debugging)
    private val _lastRefreshTime = MutableStateFlow<Long>(0)
    val lastRefreshTime: StateFlow<Long> = _lastRefreshTime.asStateFlow()

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

    /**
     * Refresh all dashboard data
     * This can be called from any fragment when data changes
     */
    fun refreshDashboardData() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            try {
                // CLEAR DATA FIRST - This will trigger the observer with empty state
                _lowStockItems.value = emptyList()
                _weeklySalesData.value = emptyList()
                _todaySales.value = "₹0"
                _stockAlertsCount.value = "0"
                _totalItems.value = "0"

                // Now load fresh data
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

    /**
     * Get stock progress for an item (0-100%)
     */
    fun getStockProgress(item: ItemWithCategoryAndVehicles): Int {
        val currentStock = item.item.stock
        if (currentStock <= 0) return 0
        val maxStock = 10
        val progress = (currentStock.toFloat() / maxStock * 100).toInt()
        return progress.coerceIn(0, 100)
    }

    /**
     * Get stock progress color based on stock level
     */
    fun getStockProgressColor(item: ItemWithCategoryAndVehicles): Int {
        val stock = item.item.stock
        return when {
            stock <= 3 -> com.example.stockwise.R.color.progress_red
            stock <= 5 -> com.example.stockwise.R.color.progress_orange
            else -> com.example.stockwise.R.color.progress_blue
        }
    }

    /**
     * Force refresh data (useful after sales or item updates)
     */
    fun forceRefresh() {
        refreshDashboardData()
    }
}