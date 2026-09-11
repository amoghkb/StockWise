package com.example.stockwise.ui.adapter


import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.stockwise.R
import com.example.stockwise.data.entities.Item
import com.example.stockwise.databinding.ItemCartSearchResultBinding
import java.io.File

class CartSearchAdapter(
    private val onAdd: (Item) -> Unit
) : ListAdapter<Item, CartSearchAdapter.SearchViewHolder>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<Item>() {
            override fun areItemsTheSame(a: Item, b: Item) = a.id == b.id
            override fun areContentsTheSame(a: Item, b: Item) = a == b
        }
    }

    inner class SearchViewHolder(val binding: ItemCartSearchResultBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SearchViewHolder {
        val binding = ItemCartSearchResultBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return SearchViewHolder(binding)
    }

    override fun onBindViewHolder(holder: SearchViewHolder, position: Int) {
        val item = getItem(position)
        val b = holder.binding

        b.tvSearchResultName.text = item.name
        b.tvSearchResultPrice.text =
            "₹${String.format("%.0f", item.sellingPrice)} • Stock: ${item.stock}"

        val imgUri = item.imageUri
        if (!imgUri.isNullOrEmpty()) {
            val file = File(imgUri)
            if (file.exists()) {
                Glide.with(b.root.context)
                    .load(file)
                    .placeholder(R.drawable.ic_image)
                    .error(R.drawable.ic_image)
                    .centerCrop()
                    .into(b.ivSearchResultImage)
            } else {
                b.ivSearchResultImage.setImageResource(R.drawable.ic_image)
            }
        } else {
            b.ivSearchResultImage.setImageResource(R.drawable.ic_image)
        }

        b.root.setOnClickListener { onAdd(item) }
        b.ivSearchResultAdd.setOnClickListener { onAdd(item) }
    }
}