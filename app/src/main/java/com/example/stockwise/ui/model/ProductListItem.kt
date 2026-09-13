package com.example.stockwise.ui.model

import com.example.stockwise.data.entities.ItemWithCategory

/**
 * Flattened row model for the Products RecyclerView.
 * Using a flat list + DiffUtil instead of nested ViewGroups is what
 * makes large catalogs fast: only rows that actually changed get rebound.
 */
sealed class ProductListItem {

    data class CategoryHeader(
        val categoryId: String,
        val categoryName: String,
        val itemCount: Int,
        val isExpanded: Boolean
    ) : ProductListItem()

    data class ProductRow(
        val itemWithCategory: ItemWithCategory
    ) : ProductListItem()

    data class EmptyCategoryMessage(val categoryId: String) : ProductListItem()

    object NoCategoriesMessage : ProductListItem()

    data class NoResultsMessage(val query: String) : ProductListItem()
}