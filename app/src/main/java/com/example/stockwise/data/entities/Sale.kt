package com.example.stockwise.data.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID

@Entity(
    tableName = "sales",
    foreignKeys = [
        ForeignKey(
            entity = Item::class,
            parentColumns = ["id"],
            childColumns = ["itemId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class Sale(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val itemId: String,
    val itemName: String,
    val quantity: Int,
    val sellingPrice: Double,
    val originalPrice: Double,
    val totalAmount: Double,
    val totalCost: Double,
    val profit: Double,
    val saleDate: Long = System.currentTimeMillis(),
    val createdAt: Long = System.currentTimeMillis() 
) {
    // Helper to get formatted date for display
    fun getFormattedDate(): String {
        val sdf = SimpleDateFormat("dd MMM yyyy, HH:mm", Locale.getDefault())
        return sdf.format(Date(saleDate))
    }

    // Helper to check if sale is from today
    fun isToday(): Boolean {
        val today = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date())
        val saleDay = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault()).format(Date(saleDate))
        return today == saleDay
    }
}

data class SalesSummary(
    val totalItemsSold: Int = 0,
    val totalAmount: Double = 0.0,
    val totalCost: Double = 0.0,
    val totalProfit: Double = 0.0,
    val averageProfitMargin: Double = 0.0
)

data class DailySalesSummary(
    val date: String? = null,
    val totalItemsSold: Int = 0,
    val totalAmount: Double = 0.0,
    val totalProfit: Double = 0.0
)

data class TopSellingItem(
    val itemId: String,
    val itemName: String,
    val totalQuantity: Int,
    val totalAmount: Double,
    val totalProfit: Double
)