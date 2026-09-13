package com.example.stockwise.ui.model

import com.example.stockwise.data.entities.Item

data class CartItem(
    val item: Item,
    var quantity: Int,
    var sellingPrice: Double
) {
    val totalPrice: Double get() = quantity * sellingPrice
    val totalCost: Double get() = quantity * item.originalPrice
    val profit: Double get() = totalPrice - totalCost
}