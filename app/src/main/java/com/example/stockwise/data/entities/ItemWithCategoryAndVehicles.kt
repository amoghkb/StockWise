package com.example.stockwise.data.entities

import androidx.room.Embedded
import androidx.room.Junction
import androidx.room.Relation

data class ItemWithCategoryAndVehicles(
    @Embedded
    val item: Item,
    val categoryName: String,
    @Relation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = ItemVehicleRelation::class,
            parentColumn = "itemId",
            entityColumn = "vehicleId"
        )
    )
    val vehicles: List<Vehicle> = emptyList()
)