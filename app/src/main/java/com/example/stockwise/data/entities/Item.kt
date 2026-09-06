package com.example.stockwise.data.entities

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import java.util.Date
import java.util.UUID

@Entity(
    tableName = "items",
    foreignKeys = [
        ForeignKey(
            entity = Category::class,
            parentColumns = ["id"],
            childColumns = ["categoryId"],
            onDelete = ForeignKey.CASCADE
        )
    ]
)
data class Item(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val categoryId: String,
    val name: String,
    val originalPrice: Double,
    val sellingPrice: Double,
    val stock: Int,
    val description: String? = null,
    val imageUri: String? = null,
    val createdAt: Date = Date(),
    val updatedAt: Date = Date(),
    val isDeleted: Boolean = false,
    val deletedAt: Date? = null
)

data class ItemWithCategory(
    @Embedded
    val item: Item,
    val categoryName: String
)