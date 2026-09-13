package com.example.stockwise.data.entities

import androidx.annotation.DrawableRes
import com.example.stockwise.R

enum class VehicleType(
    val displayName: String,
    @DrawableRes val iconRes: Int
) {
    BIKE(
        displayName = "Bike",
        iconRes = R.drawable.ic_bike
    ),
    AUTO(
        displayName = "Auto",
        iconRes = R.drawable.ic_auto
    ),
    CAR(
        displayName = "Car",
        iconRes = R.drawable.ic_car
    ),
    BUS(
        displayName = "Bus",
        iconRes = R.drawable.ic_bus
    ),
    TRUCK(
        displayName = "Truck",
        iconRes = R.drawable.ic_truck
    ),
    PICKUP_TRUCK(
        displayName = "Pickup Truck",
        iconRes = R.drawable.ic_pickup_truck
    ),
    TRACTOR(
        displayName = "Tractor",
        iconRes = R.drawable.ic_tractor
    );

    companion object {
        fun fromDisplayName(displayName: String): VehicleType? {
            return values().find { it.displayName.equals(displayName, ignoreCase = true) }
        }

        fun getVehicleTypesForDropdown(): List<Pair<String, VehicleType>> {
            return values().map { it.displayName to it }
        }
    }
}