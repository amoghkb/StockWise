package com.example.stockwise.ui.adapter

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.stockwise.R
import com.example.stockwise.data.entities.ItemWithCategoryAndVehicles
import com.example.stockwise.databinding.ItemLowStockBinding

class LowStockAdapter(
    private val getStockProgress: (ItemWithCategoryAndVehicles) -> Int,
    private val onItemClick: ((ItemWithCategoryAndVehicles) -> Unit)? = null
) : ListAdapter<ItemWithCategoryAndVehicles, LowStockAdapter.LowStockViewHolder>(
    DIFF_CALLBACK
) {

    companion object {
        private val DIFF_CALLBACK = object : DiffUtil.ItemCallback<ItemWithCategoryAndVehicles>() {
            override fun areItemsTheSame(
                oldItem: ItemWithCategoryAndVehicles,
                newItem: ItemWithCategoryAndVehicles
            ): Boolean = oldItem.item.id == newItem.item.id

            override fun areContentsTheSame(
                oldItem: ItemWithCategoryAndVehicles,
                newItem: ItemWithCategoryAndVehicles
            ): Boolean {
                return oldItem.item.stock == newItem.item.stock &&
                        oldItem.item.name == newItem.item.name &&
                        oldItem.item.imageUri == newItem.item.imageUri
            }
        }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): LowStockViewHolder {
        val binding = ItemLowStockBinding.inflate(
            LayoutInflater.from(parent.context),
            parent,
            false
        )
        return LowStockViewHolder(binding, onItemClick)
    }

    override fun onBindViewHolder(holder: LowStockViewHolder, position: Int) {
        val item = getItem(position)
        val progress = getStockProgress(item)
        holder.bind(item, progress)
    }

    class LowStockViewHolder(
        private val binding: ItemLowStockBinding,
        private val onItemClick: ((ItemWithCategoryAndVehicles) -> Unit)?
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: ItemWithCategoryAndVehicles, progress: Int) {
            binding.apply {
                tvItemName.text = item.item.name
                tvStockCount.text = item.item.stock.toString()
                tvStockLabel.text = "left"

                progressBar.progress = progress

                // Set progress color based on stock level
                val color = when {
                    item.item.stock <= 3 -> R.color.progress_red
                    item.item.stock <= 5 -> R.color.progress_orange
                    else -> R.color.progress_blue
                }
                progressBar.progressTintList = ContextCompat.getColorStateList(
                    binding.root.context,
                    color
                )

                // Set click listener
                root.setOnClickListener {
                    onItemClick?.invoke(item)
                }
            }
        }
    }
}