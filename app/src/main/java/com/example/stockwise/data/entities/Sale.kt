package com.example.stockwise.data.entities

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import java.util.Date
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
    val saleDate: Date = Date(),
    val createdAt: Date = Date()
)

data class SalesSummary(
    val totalItemsSold: Int,
    val totalAmount: Double,
    val totalCost: Double,
    val totalProfit: Double,
    val averageProfitMargin: Double
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