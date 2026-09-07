package com.example.stockwise.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date
import java.util.UUID

@Entity(tableName = "vehicles")
data class Vehicle(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val company: String? = null,
    val type: VehicleType? = null,
    val model: String? = null,
    val description: String? = null,
    val createdAt: Date = Date(),
    val updatedAt: Date = Date(),
    val isDeleted: Boolean = false,
    val deletedAt: Date? = null
)

