package com.example.stockwise.data.entities

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.Date
import java.util.UUID

@Entity(tableName = "suppliers")
data class Supplier(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),

    val initials: String,
    val companyName: String,
    val category: String,
    val contactPerson: String,
    val phoneNumber: String,
    val address: String,

    val createdAt: Date = Date(),
    val updatedAt: Date = Date(),
    val isDeleted: Boolean = false,
    val deletedAt: Date? = null
)