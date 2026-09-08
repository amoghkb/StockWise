package com.example.stockwise.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.example.stockwise.R
import com.example.stockwise.data.entities.ItemWithCategory
import com.example.stockwise.ui.model.ProductListItem
import java.io.File
import java.util.Locale

class ProductsAdapter(
    private val onHeaderClick: (categoryId: String) -> Unit,
    private val onItemClick: (ItemWithCategory) -> Unit
) : ListAdapter<ProductListItem, RecyclerView.ViewHolder>(DIFF_CALLBACK) {

    companion object {
        private const val TYPE_HEADER = 0
        private const val TYPE_PRODUCT = 1
        private const val TYPE_MESSAGE = 2

        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<ProductListItem>() {
            override fun areItemsTheSame(old: ProductListItem, new: ProductListItem): Boolean = when {
                old is ProductListItem.CategoryHeader && new is ProductListItem.CategoryHeader ->
                    old.categoryId == new.categoryId
                old is ProductListItem.ProductRow && new is ProductListItem.ProductRow ->
                    old.itemWithCategory.item.id == new.itemWithCategory.item.id
                old is ProductListItem.EmptyCategoryMessage && new is ProductListItem.EmptyCategoryMessage ->
                    old.categoryId == new.categoryId
                old is ProductListItem.NoCategoriesMessage && new is ProductListItem.NoCategoriesMessage -> true
                old is ProductListItem.NoResultsMessage && new is ProductListItem.NoResultsMessage -> true
                else -> false
            }

            override fun areContentsTheSame(old: ProductListItem, new: ProductListItem): Boolean = old == new
        }
    }

    override fun getItemViewType(position: Int): Int = when (getItem(position)) {
        is ProductListItem.CategoryHeader -> TYPE_HEADER
        is ProductListItem.ProductRow -> TYPE_PRODUCT
        else -> TYPE_MESSAGE
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val inflater = LayoutInflater.from(parent.context)
        return when (viewType) {
            TYPE_HEADER -> HeaderViewHolder(
                inflater.inflate(R.layout.item_category_header, parent, false),
                onHeaderClick
            )
            TYPE_PRODUCT -> ProductViewHolder(
                inflater.inflate(R.layout.category_item_layout, parent, false),
                onItemClick
            )
            else -> MessageViewHolder(
                inflater.inflate(R.layout.item_message, parent, false)
            )
        }
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = getItem(position)) {
            is ProductListItem.CategoryHeader -> (holder as HeaderViewHolder).bind(item)
            is ProductListItem.ProductRow -> (holder as ProductViewHolder).bind(item.itemWithCategory)
            is ProductListItem.EmptyCategoryMessage ->
                (holder as MessageViewHolder).bind("No items in this category")
            is ProductListItem.NoCategoriesMessage ->
                (holder as MessageViewHolder).bind("No categories available.\nTap + to add products.")
            is ProductListItem.NoResultsMessage ->
                (holder as MessageViewHolder).bind("No results for \"${item.query}\"")
        }
    }

    class HeaderViewHolder(
        itemView: View,
        private val onHeaderClick: (String) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {
        private val tvName: TextView = itemView.findViewById(R.id.tvCategoryName)
        private val tvCount: TextView = itemView.findViewById(R.id.tvCategoryCount)
        private val chevron: ImageView = itemView.findViewById(R.id.chevronCategory)

        fun bind(header: ProductListItem.CategoryHeader) {
            tvName.text = header.categoryName
            tvCount.text = "${header.itemCount} items"
            chevron.setImageResource(
                if (header.isExpanded) R.drawable.ic_up_arrow else R.drawable.ic_down_arrow
            )
            itemView.setOnClickListener { onHeaderClick(header.categoryId) }
        }
    }

    class ProductViewHolder(
        itemView: View,
        private val onItemClick: (ItemWithCategory) -> Unit
    ) : RecyclerView.ViewHolder(itemView) {

        private val tvName: TextView = itemView.findViewById(R.id.tvItemName)
        private val tvSku: TextView = itemView.findViewById(R.id.tvItemSku)
        private val tvPrice: TextView = itemView.findViewById(R.id.tvItemPrice)
        private val tvQty: TextView = itemView.findViewById(R.id.tvItemQty)
        private val ivImage: ImageView = itemView.findViewById(R.id.ivItemImage)

        private val context = itemView.context
        private val imageSizePx = (48 * context.resources.displayMetrics.density).toInt()
        private val cornerRadiusPx = (8 * context.resources.displayMetrics.density).toInt()

        init {
            // Static styling that doesn't depend on data — do it once here, not per bind.
            tvName.setTextAppearance(R.style.BodyText)
            tvSku.setTextAppearance(R.style.BodyText)
            tvPrice.setTextAppearance(R.style.BodyText)
            tvQty.setTextAppearance(R.style.BodyText)
            tvPrice.setTextColor(ContextCompat.getColor(context, R.color.stock_wise_primary))

            val lp = ivImage.layoutParams
            lp.width = imageSizePx
            lp.height = imageSizePx
            ivImage.layoutParams = lp
            ivImage.scaleType = ImageView.ScaleType.CENTER_CROP

            itemView.isClickable = true
            itemView.isFocusable = true
            itemView.background = ContextCompat.getDrawable(context, R.drawable.ripple_effect)
        }

        fun bind(itemWithCategory: ItemWithCategory) {
            val item = itemWithCategory.item

            tvName.text = item.name
            tvSku.text = "SKU: ${item.id.take(8).uppercase()}"
            tvPrice.text = "\u20B9${String.format(Locale.getDefault(), "%.2f", item.sellingPrice)}"
            tvQty.text = "Qty: ${item.stock}"

            val imagePath = item.imageUri
            if (!imagePath.isNullOrEmpty()) {
                // No File.exists() check here — Glide already does file I/O off the
                // main thread and falls back to .error() if the file is missing.
                Glide.with(context)
                    .load(File(imagePath))
                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                    .placeholder(R.drawable.ic_image)
                    .error(R.drawable.ic_image)
                    .transform(RoundedCorners(cornerRadiusPx))
                    .centerCrop()
                    .override(imageSizePx, imageSizePx)
                    .into(ivImage)
            } else {
                Glide.with(context).clear(ivImage)
                ivImage.setImageResource(R.drawable.ic_image)
            }

            itemView.setOnClickListener { onItemClick(itemWithCategory) }
        }
    }

    class MessageViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvMessage: TextView = itemView.findViewById(R.id.tvMessage)
        fun bind(text: String) {
            tvMessage.text = text
        }
    }
}