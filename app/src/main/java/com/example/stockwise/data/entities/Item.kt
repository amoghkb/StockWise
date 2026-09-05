package com.example.stockwise.data.entities


import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.PrimaryKey
import java.util.Date

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
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val categoryId: Long,
    val name: String,
    val originalPrice: Double,
    val sellingPrice: Double,
    val stock: Int,
    val description: String? = null,
    val imageUri: String? = null,
    val createdAt: Date = Date(),
    val updatedAt: Date = Date(),
    val isDeleted: Boolean = false,  // Soft delete flag
    val deletedAt: Date? = null      // When soft deleted
)

// Data class for Item with Category Name (for joining)
data class ItemWithCategory(
    @Embedded
    val item: Item,
    val categoryName: String
)
