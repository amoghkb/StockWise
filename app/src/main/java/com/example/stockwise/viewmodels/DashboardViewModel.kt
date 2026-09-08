package com.example.stockwise.viewmodels

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.stockwise.R
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
class DashboardViewModel @Inject constructor(
    private val itemRepository: ItemRepository,
    private val salesRepository: SalesRepository,
    private val application: Application
) : AndroidViewModel(application) {

    // State for greeting and date
    private val _greeting = MutableStateFlow("")
    val greeting: StateFlow<String> = _greeting.asStateFlow()

    private val _currentDate = MutableStateFlow("")
    val currentDate: StateFlow<String> = _currentDate.asStateFlow()

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

    // ===== NEW: Weekly Sales Data for Bar Chart =====
    private val _weeklySalesData = MutableStateFlow<List<DailySalesSummary>>(emptyList())
    val weeklySalesData: StateFlow<List<DailySalesSummary>> = _weeklySalesData.asStateFlow()

    // Loading state
    private val _isLoading = MutableStateFlow(false)
    val isLoading: StateFlow<Boolean> = _isLoading.asStateFlow()

    // Error state
    private val _error = MutableStateFlow<String?>(null)
    val error: StateFlow<String?> = _error.asStateFlow()

    init {
        setupGreetingAndDate()
        loadDashboardData()
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

    fun loadDashboardData() {
        viewModelScope.launch {
            _isLoading.value = true
            _error.value = null

            try {
                loadTodaySales()
                loadStockAlerts()
                loadTotalItems()
                loadLowStockItems()
                loadWeeklySalesData() // <-- NEW: Load weekly data
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

    // ===== NEW: Load weekly sales data for chart =====
    private suspend fun loadWeeklySalesData() {
        try {
            val weeklyData = salesRepository.getWeeklySalesBreakdown().first()

            // DEBUG: Print raw data
            android.util.Log.d("DashboardVM", "=== RAW WEEKLY DATA ===")
            android.util.Log.d("DashboardVM", "Total records: ${weeklyData.size}")
            weeklyData.forEachIndexed { index, day ->
                android.util.Log.d("DashboardVM",
                    "Record $index: Date='${day.date}', Amount=₹${day.totalAmount}, Items=${day.totalItemsSold}"
                )
            }

            val filledData = fillMissingWeekDays(weeklyData)

            // DEBUG: Print filled data
            android.util.Log.d("DashboardVM", "=== FILLED DATA ===")
            filledData.forEachIndexed { index, day ->
                android.util.Log.d("DashboardVM",
                    "Day $index: ${day.date}, Amount=₹${day.totalAmount}"
                )
            }

            _weeklySalesData.value = filledData
        } catch (e: Exception) {
            android.util.Log.e("DashboardVM", "Error loading weekly sales", e)
            _weeklySalesData.value = emptyList()
            e.printStackTrace()
        }
    }
    // Helper to fill missing days with zero values
    private fun fillMissingWeekDays(data: List<DailySalesSummary>): List<DailySalesSummary> {
        val result = mutableListOf<DailySalesSummary>()
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val dayFormat = SimpleDateFormat("EEE", Locale.US) // "Mon", "Tue", etc.

        // Create a map of date to sales amount
        // The date from database is already in "yyyy-MM-dd" format
        val dataMap = data.associateBy { it.date ?: "" }

        android.util.Log.d("DashboardVM", "DataMap keys: ${dataMap.keys}")

        // Get the last 7 days (including today)
        for (i in 6 downTo 0) {
            val calendar = Calendar.getInstance()
            calendar.add(Calendar.DAY_OF_MONTH, -i)
            val date = calendar.time

            val dateStr = dateFormat.format(date)
            val dayName = dayFormat.format(date) // This gives "Mon", "Tue", etc.

            val existingData = dataMap[dateStr]
            val amount = existingData?.totalAmount ?: 0.0

            android.util.Log.d("DashboardVM",
                "Day $i: dateStr=$dateStr, dayName=$dayName, amount=₹$amount"
            )

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
    fun refreshData() {
        loadDashboardData()
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
            stock <= 3 -> R.color.progress_red
            stock <= 5 -> R.color.progress_orange
            else -> R.color.progress_blue
        }
    }
}