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
        WHERE date(saleDate / 1000, 'unixepoch', 'localtime') = date('now', 'localtime')
        ORDER BY saleDate DESC
    """)
    fun getTodaySales(): Flow<List<Sale>>

    @Query("""
        SELECT 
            COALESCE(COUNT(*), 0) as totalItemsSold,
            COALESCE(SUM(totalAmount), 0) as totalAmount,
            COALESCE(SUM(totalCost), 0) as totalCost,
            COALESCE(SUM(profit), 0) as totalProfit,
            CASE 
                WHEN COALESCE(SUM(totalAmount), 0) > 0 
                THEN (COALESCE(SUM(profit), 0) * 100.0 / COALESCE(SUM(totalAmount), 0)) 
                ELSE 0.0 
            END as averageProfitMargin
        FROM sales 
        WHERE date(saleDate / 1000, 'unixepoch', 'localtime') = date('now', 'localtime')
    """)
    suspend fun getTodaySalesSummary(): SalesSummary?

    @Query("""
        SELECT 
            COALESCE(SUM(quantity), 0) as totalItemsSold,
            COALESCE(SUM(totalAmount), 0) as totalAmount,
            COALESCE(SUM(profit), 0) as totalProfit
        FROM sales 
        WHERE date(saleDate / 1000, 'unixepoch', 'localtime') = date('now', 'localtime')
    """)
    suspend fun getTodayStats(): DailySalesSummary?

    // ==============================
    // WEEKLY SALES (Last 7 days)
    // ==============================

    @Query("""
        SELECT 
            COALESCE(COUNT(*), 0) as totalItemsSold,
            COALESCE(SUM(totalAmount), 0) as totalAmount,
            COALESCE(SUM(totalCost), 0) as totalCost,
            COALESCE(SUM(profit), 0) as totalProfit,
            CASE 
                WHEN COALESCE(SUM(totalAmount), 0) > 0 
                THEN (COALESCE(SUM(profit), 0) * 100.0 / COALESCE(SUM(totalAmount), 0)) 
                ELSE 0.0 
            END as averageProfitMargin
        FROM sales 
        WHERE date(saleDate / 1000, 'unixepoch', 'localtime') >= date('now', 'localtime', '-7 days')
    """)
    suspend fun getWeeklySalesSummary(): SalesSummary?

    @Query("""
        SELECT 
            COALESCE(SUM(quantity), 0) as totalItemsSold,
            COALESCE(SUM(totalAmount), 0) as totalAmount,
            COALESCE(SUM(profit), 0) as totalProfit
        FROM sales 
        WHERE date(saleDate / 1000, 'unixepoch', 'localtime') >= date('now', 'localtime', '-7 days')
    """)
    suspend fun getWeeklyStats(): DailySalesSummary?

    @Query("""
    SELECT 
        date(saleDate / 1000, 'unixepoch', 'localtime') as date,
        COALESCE(SUM(quantity), 0) as totalItemsSold,
        COALESCE(SUM(totalAmount), 0) as totalAmount,
        COALESCE(SUM(profit), 0) as totalProfit
    FROM sales 
    WHERE date(saleDate / 1000, 'unixepoch', 'localtime') >= date('now', 'localtime', '-6 days')
    GROUP BY date(saleDate / 1000, 'unixepoch', 'localtime')
    ORDER BY date ASC
""")
    fun getWeeklySalesBreakdown(): Flow<List<DailySalesSummary>>
    // ==============================
    // MONTHLY SALES (Last 30 days)
    // ==============================

    @Query("""
        SELECT 
            COALESCE(COUNT(*), 0) as totalItemsSold,
            COALESCE(SUM(totalAmount), 0) as totalAmount,
            COALESCE(SUM(totalCost), 0) as totalCost,
            COALESCE(SUM(profit), 0) as totalProfit,
            CASE 
                WHEN COALESCE(SUM(totalAmount), 0) > 0 
                THEN (COALESCE(SUM(profit), 0) * 100.0 / COALESCE(SUM(totalAmount), 0)) 
                ELSE 0.0 
            END as averageProfitMargin
        FROM sales 
        WHERE date(saleDate / 1000, 'unixepoch', 'localtime') >= date('now', 'localtime', '-30 days')
    """)
    suspend fun getMonthlySalesSummary(): SalesSummary?

    @Query("""
        SELECT 
            COALESCE(SUM(quantity), 0) as totalItemsSold,
            COALESCE(SUM(totalAmount), 0) as totalAmount,
            COALESCE(SUM(profit), 0) as totalProfit
        FROM sales 
        WHERE date(saleDate / 1000, 'unixepoch', 'localtime') >= date('now', 'localtime', '-30 days')
    """)
    suspend fun getMonthlyStats(): DailySalesSummary?

    @Query("""
        SELECT 
            date(saleDate / 1000, 'unixepoch', 'localtime') as date,
            COALESCE(SUM(quantity), 0) as totalItemsSold,
            COALESCE(SUM(totalAmount), 0) as totalAmount,
            COALESCE(SUM(profit), 0) as totalProfit
        FROM sales 
        WHERE date(saleDate / 1000, 'unixepoch', 'localtime') >= date('now', 'localtime', '-30 days')
        GROUP BY date(saleDate / 1000, 'unixepoch', 'localtime')
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
            COALESCE(SUM(quantity), 0) as totalQuantity,
            COALESCE(SUM(totalAmount), 0) as totalAmount,
            COALESCE(SUM(profit), 0) as totalProfit
        FROM sales 
        WHERE date(saleDate / 1000, 'unixepoch', 'localtime') >= date('now', 'localtime', '-30 days')
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
            COALESCE(COUNT(*), 0) as totalItemsSold,
            COALESCE(SUM(totalAmount), 0) as totalAmount,
            COALESCE(SUM(totalCost), 0) as totalCost,
            COALESCE(SUM(profit), 0) as totalProfit,
            CASE 
                WHEN COALESCE(SUM(totalAmount), 0) > 0 
                THEN (COALESCE(SUM(profit), 0) * 100.0 / COALESCE(SUM(totalAmount), 0)) 
                ELSE 0.0 
            END as averageProfitMargin
        FROM sales 
        WHERE date(saleDate / 1000, 'unixepoch', 'localtime') >= date(:startDate, 'localtime') 
        AND date(saleDate / 1000, 'unixepoch', 'localtime') <= date(:endDate, 'localtime')
    """)
    suspend fun getSalesSummaryForDateRange(startDate: String, endDate: String): SalesSummary?

    @Query("""
        SELECT 
            date(saleDate / 1000, 'unixepoch', 'localtime') as date,
            COALESCE(SUM(quantity), 0) as totalItemsSold,
            COALESCE(SUM(totalAmount), 0) as totalAmount,
            COALESCE(SUM(profit), 0) as totalProfit
        FROM sales 
        WHERE date(saleDate / 1000, 'unixepoch', 'localtime') >= date(:startDate, 'localtime') 
        AND date(saleDate / 1000, 'unixepoch', 'localtime') <= date(:endDate, 'localtime')
        GROUP BY date(saleDate / 1000, 'unixepoch', 'localtime')
        ORDER BY date ASC
    """)
    fun getSalesBreakdownForDateRange(startDate: String, endDate: String): Flow<List<DailySalesSummary>>

    // ==============================
    // HELPER METHODS
    // ==============================

    @Query("SELECT * FROM sales ORDER BY saleDate DESC")
    suspend fun getAllSales(): List<Sale>

    @Query("DELETE FROM sales WHERE date(saleDate / 1000, 'unixepoch', 'localtime') < date('now', 'localtime', '-1 year')")
    suspend fun deleteOldSales()

    @Query("SELECT COUNT(*) FROM sales")
    suspend fun getTotalSalesCount(): Int
}