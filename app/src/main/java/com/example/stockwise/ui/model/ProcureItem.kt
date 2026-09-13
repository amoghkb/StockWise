package com.example.stockwise.ui.model

import com.example.stockwise.data.entities.Item
import java.util.Date

data class ProcureItem(
    val rowId: Long? = null,             // DB row id (null = new / not yet saved)
    val item: Item? = null,              // null for custom items
    val customName: String? = null,      // set only for custom items
    var quantity: Int = 1,
    var isPurchased: Boolean = false,
    var notes: String = "",
    var isCustom: Boolean = false,
    val createdAt: Date? = null          // preserve original creation time
) {
    val displayName: String
        get() = if (isCustom) customName.orEmpty() else item?.name.orEmpty()

    val subtitle: String
        get() = if (isCustom) {
            "Custom item"
        } else {
            val idShort = item?.id?.take(8).orEmpty()
            val stock = item?.stock ?: 0
            "SKU: $idShort • Current Stock: $stock"
        }

    val uniqueId: String
        get() = if (isCustom) "custom_${customName.orEmpty()}" else item?.id.orEmpty()
}