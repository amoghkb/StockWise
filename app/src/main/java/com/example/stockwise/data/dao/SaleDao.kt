package com.example.stockwise.data.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.stockwise.data.entities.Sale
import com.example.stockwise.data.entities.SalesSummary
import com.example.stockwise.data.entities.DailySalesSummary
import com.example.stockwise.data.entities.TopSellingItem
import kotlinx.coroutines.flow.Flow

@Dao
interface SaleDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSale(sale: Sale)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSales(sales: List<Sale>)

    @Query("SELECT * FROM sales WHERE id = :saleId")
    suspend fun getSaleById(saleId: String): Sale?

    @Query("SELECT * FROM sales WHERE itemId = :itemId ORDER BY saleDate DESC")
    fun getSalesByItem(itemId: String): Flow<List<Sale>>

    // ==============================
    // TODAY'S SALES
    // ==============================

    @Query("""
        SELECT * FROM sales 
        WHERE DATE(saleDate) = DATE('now', 'localtime')
        ORDER BY saleDate DESC
    """)
    fun getTodaySales(): Flow<List<Sale>>

    @Query("""
        SELECT 
            COUNT(*) as totalItemsSold,
            SUM(totalAmount) as totalAmount,
            SUM(totalCost) as totalCost,
            SUM(profit) as totalProfit,
            CASE 
                WHEN SUM(totalAmount) > 0 
                THEN (SUM(profit) * 100.0 / SUM(totalAmount)) 
                ELSE 0.0 
            END as averageProfitMargin
        FROM sales 
        WHERE DATE(saleDate) = DATE('now', 'localtime')
    """)
    suspend fun getTodaySalesSummary(): SalesSummary?

    @Query("""
        SELECT 
            SUM(quantity) as totalItemsSold,
            SUM(totalAmount) as totalAmount,
            SUM(profit) as totalProfit
        FROM sales 
        WHERE DATE(saleDate) = DATE('now', 'localtime')
    """)
    suspend fun getTodayStats(): DailySalesSummary?

    // ==============================
    // WEEKLY SALES (Last 7 days)
    // ==============================

    @Query("""
        SELECT 
            COUNT(*) as totalItemsSold,
            SUM(totalAmount) as totalAmount,
            SUM(totalCost) as totalCost,
            SUM(profit) as totalProfit,
            CASE 
                WHEN SUM(totalAmount) > 0 
                THEN (SUM(profit) * 100.0 / SUM(totalAmount)) 
                ELSE 0.0 
            END as averageProfitMargin
        FROM sales 
        WHERE DATE(saleDate) >= DATE('now', '-7 days', 'localtime')
    """)
    suspend fun getWeeklySalesSummary(): SalesSummary?

    @Query("""
        SELECT 
            SUM(quantity) as totalItemsSold,
            SUM(totalAmount) as totalAmount,
            SUM(profit) as totalProfit
        FROM sales 
        WHERE DATE(saleDate) >= DATE('now', '-7 days', 'localtime')
    """)
    suspend fun getWeeklyStats(): DailySalesSummary?

    @Query("""
        SELECT 
            DATE(saleDate) as date,
            SUM(quantity) as totalItemsSold,
            SUM(totalAmount) as totalAmount,
            SUM(profit) as totalProfit
        FROM sales 
        WHERE DATE(saleDate) >= DATE('now', '-7 days', 'localtime')
        GROUP BY DATE(saleDate)
        ORDER BY date ASC
    """)
    fun getWeeklySalesBreakdown(): Flow<List<DailySalesSummary>>

    // ==============================
    // MONTHLY SALES (Last 30 days)
    // ==============================

    @Query("""
        SELECT 
            COUNT(*) as totalItemsSold,
            SUM(totalAmount) as totalAmount,
            SUM(totalCost) as totalCost,
            SUM(profit) as totalProfit,
            CASE 
                WHEN SUM(totalAmount) > 0 
                THEN (SUM(profit) * 100.0 / SUM(totalAmount)) 
                ELSE 0.0 
            END as averageProfitMargin
        FROM sales 
        WHERE DATE(saleDate) >= DATE('now', '-30 days', 'localtime')
    """)
    suspend fun getMonthlySalesSummary(): SalesSummary?

    @Query("""
        SELECT 
            SUM(quantity) as totalItemsSold,
            SUM(totalAmount) as totalAmount,
            SUM(profit) as totalProfit
        FROM sales 
        WHERE DATE(saleDate) >= DATE('now', '-30 days', 'localtime')
    """)
    suspend fun getMonthlyStats(): DailySalesSummary?

    @Query("""
        SELECT 
            DATE(saleDate) as date,
            SUM(quantity) as totalItemsSold,
            SUM(totalAmount) as totalAmount,
            SUM(profit) as totalProfit
        FROM sales 
        WHERE DATE(saleDate) >= DATE('now', '-30 days', 'localtime')
        GROUP BY DATE(saleDate)
        ORDER BY date ASC
    """)
    fun getMonthlySalesBreakdown(): Flow<List<DailySalesSummary>>

    // ==============================
    // TOP SELLING ITEMS
    // ==============================

    @Query("""
        SELECT 
            itemId,
            itemName,
            SUM(quantity) as totalQuantity,
            SUM(totalAmount) as totalAmount,
            SUM(profit) as totalProfit
        FROM sales 
        WHERE DATE(saleDate) >= DATE('now', '-30 days', 'localtime')
        GROUP BY itemId, itemName
        ORDER BY totalQuantity DESC
        LIMIT :limit
    """)
    suspend fun getTopSellingItems(limit: Int = 10): List<TopSellingItem>

    // ==============================
    // CUSTOM DATE RANGE
    // ==============================

    @Query("""
        SELECT 
            COUNT(*) as totalItemsSold,
            SUM(totalAmount) as totalAmount,
            SUM(totalCost) as totalCost,
            SUM(profit) as totalProfit,
            CASE 
                WHEN SUM(totalAmount) > 0 
                THEN (SUM(profit) * 100.0 / SUM(totalAmount)) 
                ELSE 0.0 
            END as averageProfitMargin
        FROM sales 
        WHERE DATE(saleDate) >= DATE(:startDate) AND DATE(saleDate) <= DATE(:endDate)
    """)
    suspend fun getSalesSummaryForDateRange(startDate: String, endDate: String): SalesSummary?

    @Query("""
        SELECT 
            DATE(saleDate) as date,
            SUM(quantity) as totalItemsSold,
            SUM(totalAmount) as totalAmount,
            SUM(profit) as totalProfit
        FROM sales 
        WHERE DATE(saleDate) >= DATE(:startDate) AND DATE(saleDate) <= DATE(:endDate)
        GROUP BY DATE(saleDate)
        ORDER BY date ASC
    """)
    fun getSalesBreakdownForDateRange(startDate: String, endDate: String): Flow<List<DailySalesSummary>>

    // ==============================
    // CLEANUP
    // ==============================

    @Query("DELETE FROM sales WHERE DATE(saleDate) < DATE('now', '-1 year')")
    suspend fun deleteOldSales()
}