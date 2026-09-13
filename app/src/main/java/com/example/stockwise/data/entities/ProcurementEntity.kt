package com.example.stockwise.data.entities


import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import java.util.Date

@Entity(
    tableName = "procurement_items",
    indices = [
        Index("dateKey"),
        Index("itemId")
    ]
)
data class ProcurementEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,

    /** "yyyy-MM-dd" key — used to group items by date */
    val dateKey: String,

    /** Null for custom items */
    val itemId: String? = null,

    /** Set only for custom items */
    val customName: String? = null,

    val quantity: Int = 1,
    val isPurchased: Boolean = false,
    val notes: String = "",
    val isCustom: Boolean = false,

    val createdAt: Date = Date(),
    val updatedAt: Date = Date()
)