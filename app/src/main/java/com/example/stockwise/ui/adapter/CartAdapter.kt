package com.example.stockwise.ui.adapter

import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.stockwise.R
import com.example.stockwise.commons.toastError
import com.example.stockwise.databinding.ItemCartProductBinding
import com.example.stockwise.ui.model.CartItem

class CartAdapter(
    private val onQuantityChanged: (CartItem, Int) -> Unit,
    private val onPriceChanged: (CartItem, Double) -> Unit,
    private val onRemove: (CartItem) -> Unit,
    private val onCartUpdated: () -> Unit
) : ListAdapter<CartItem, CartAdapter.CartViewHolder>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<CartItem>() {
            override fun areItemsTheSame(a: CartItem, b: CartItem) = a.item.id == b.item.id
            override fun areContentsTheSame(a: CartItem, b: CartItem): Boolean {
                return a.quantity == b.quantity &&
                        a.sellingPrice == b.sellingPrice
            }
        }
    }

    inner class CartViewHolder(val binding: ItemCartProductBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CartViewHolder {
        val binding = ItemCartProductBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return CartViewHolder(binding)
    }

    override fun onBindViewHolder(holder: CartViewHolder, position: Int) {
        val cartItem = getItem(position)
        val b = holder.binding

        b.tvCartProductName.text = cartItem.item.name
        b.tvCartProductStock.text = "Stock: ${cartItem.item.stock}"

        // ===== Price EditText — REMOVE old watcher first =====
        (b.etCartPrice.getTag(R.id.etCartPrice) as? TextWatcher)?.let {
            b.etCartPrice.removeTextChangedListener(it)
        }
        b.etCartPrice.setTag(R.id.etCartPrice, null)

        // Set text without triggering watcher loops
        b.etCartPrice.setText(String.format("%.0f", cartItem.sellingPrice))
        
        val priceWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val price = s?.toString()?.trim()?.toDoubleOrNull() ?: 0.0
                if (price > 0 && price != cartItem.sellingPrice) {
                    onPriceChanged(cartItem, price)
                    onCartUpdated()
                }
            }
        }
        b.etCartPrice.addTextChangedListener(priceWatcher)
        b.etCartPrice.setTag(R.id.etCartPrice, priceWatcher)

        // ===== Quantity =====
        b.tvCartQuantity.text = cartItem.quantity.toString()

        // Decrease: if qty is 1 (or lower), remove the item from cart
        b.btnCartDecrease.setOnClickListener {
            if (cartItem.quantity <= 1) {
                onRemove(cartItem)
            } else {
                onQuantityChanged(cartItem, cartItem.quantity - 1)
            }
            onCartUpdated()
        }

        b.btnCartIncrease.setOnClickListener {
            if (cartItem.quantity < cartItem.item.stock) {
                onQuantityChanged(cartItem, cartItem.quantity + 1)
                onCartUpdated()
            } else {
                "Cannot exceed stock (${cartItem.item.stock})".toastError(b.root.context)
            }
        }
    }

    override fun onViewRecycled(holder: CartViewHolder) {
        super.onViewRecycled(holder)
        val b = holder.binding
        (b.etCartPrice.getTag(R.id.etCartPrice) as? TextWatcher)?.let {
            b.etCartPrice.removeTextChangedListener(it)
        }
        b.etCartPrice.setTag(R.id.etCartPrice, null)
    }
}