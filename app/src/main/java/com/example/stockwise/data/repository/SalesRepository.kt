package com.example.stockwise.data.repository

import com.example.stockwise.data.dao.SaleDao
import com.example.stockwise.data.entities.Sale
import com.example.stockwise.data.entities.SalesSummary
import com.example.stockwise.data.entities.DailySalesSummary
import com.example.stockwise.data.entities.TopSellingItem
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SalesRepository @Inject constructor(
    private val saleDao: SaleDao
) {

    suspend fun recordSale(
        itemId: String,
        itemName: String,
        quantity: Int,
        sellingPrice: Double,
        originalPrice: Double
    ) {
        val totalAmount = quantity * sellingPrice
        val totalCost = quantity * originalPrice
        val profit = totalAmount - totalCost

        val sale = Sale(
            itemId = itemId,
            itemName = itemName,
            quantity = quantity,
            sellingPrice = sellingPrice,
            originalPrice = originalPrice,
            totalAmount = totalAmount,
            totalCost = totalCost,
            profit = profit,
            saleDate = System.currentTimeMillis(),
            createdAt = System.currentTimeMillis()
        )
        saleDao.insertSale(sale)
    }

    suspend fun recordMultipleSales(sales: List<Sale>) {
        saleDao.insertSales(sales)
    }

    fun getTodaySales(): Flow<List<Sale>> = saleDao.getTodaySales()

    suspend fun getTodaySalesSummary(): SalesSummary? = saleDao.getTodaySalesSummary()

    suspend fun getTodayStats(): DailySalesSummary? = saleDao.getTodayStats()

    suspend fun getWeeklySalesSummary(): SalesSummary? = saleDao.getWeeklySalesSummary()

    suspend fun getWeeklyStats(): DailySalesSummary? = saleDao.getWeeklyStats()

    fun getWeeklySalesBreakdown(): Flow<List<DailySalesSummary>> = saleDao.getWeeklySalesBreakdown()

    suspend fun getMonthlySalesSummary(): SalesSummary? = saleDao.getMonthlySalesSummary()

    suspend fun getMonthlyStats(): DailySalesSummary? = saleDao.getMonthlyStats()

    fun getMonthlySalesBreakdown(): Flow<List<DailySalesSummary>> = saleDao.getMonthlySalesBreakdown()

    suspend fun getTopSellingItems(limit: Int = 10): List<TopSellingItem> =
        saleDao.getTopSellingItems(limit)

    suspend fun getSalesSummaryForDateRange(startDate: Date, endDate: Date): SalesSummary? {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return saleDao.getSalesSummaryForDateRange(
            sdf.format(startDate),
            sdf.format(endDate)
        )
    }

    fun getSalesBreakdownForDateRange(startDate: Date, endDate: Date): Flow<List<DailySalesSummary>> {
        val sdf = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        return saleDao.getSalesBreakdownForDateRange(
            sdf.format(startDate),
            sdf.format(endDate)
        )
    }

    suspend fun getSalesByItem(itemId: String): List<Sale> {
        return saleDao.getSalesByItem(itemId).firstOrNull() ?: emptyList()
    }

    suspend fun getAllSales(): List<Sale> {
        return saleDao.getAllSales()
    }

    suspend fun getTotalSalesCount(): Int {
        return saleDao.getTotalSalesCount()
    }
}