package com.example.stockwise.ui.adapter

import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.stockwise.R
import com.example.stockwise.databinding.ItemProcureCardBinding
import com.example.stockwise.ui.model.ProcureItem
import com.google.android.material.card.MaterialCardView

class ProcureAdapter(
    private val onDelete: (ProcureItem) -> Unit,
    private val onQuantityChanged: (uniqueId: String, quantity: Int) -> Unit,
    private val onPurchasedChanged: (uniqueId: String, purchased: Boolean) -> Unit,
    private val onNotesChanged: (uniqueId: String, notes: String) -> Unit
) : ListAdapter<ProcureItem, ProcureAdapter.VH>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<ProcureItem>() {
            override fun areItemsTheSame(a: ProcureItem, b: ProcureItem) =
                a.uniqueId == b.uniqueId

            override fun areContentsTheSame(a: ProcureItem, b: ProcureItem) =
                a == b
        }

        private const val COLOR_DEFAULT_BG = 0xFFFFFFFF.toInt()
        private const val COLOR_DEFAULT_STROKE = 0xFFE5E7EB.toInt()
        private const val COLOR_DONE_BG = 0xFFE8F8F0.toInt()
        private const val COLOR_DONE_STROKE = 0xFFA7E3C6.toInt()
    }

    class VH(val binding: ItemProcureCardBinding) :
        RecyclerView.ViewHolder(binding.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val binding = ItemProcureCardBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return VH(binding)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val data = getItem(position)
        val b = holder.binding

        b.tvCardItemName.text = data.displayName
        b.tvCardItemSku.text = data.subtitle

        // ---- Qty ----
        (b.etCardQty.getTag(R.id.etCardQty) as? TextWatcher)?.let {
            b.etCardQty.removeTextChangedListener(it)
        }
        b.etCardQty.setText(data.quantity.toString())

        val qtyWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val q = s?.toString()?.toIntOrNull() ?: 0
                if (q != data.quantity) {
                    onQuantityChanged(data.uniqueId, q)
                }
            }
        }
        b.etCardQty.addTextChangedListener(qtyWatcher)
        b.etCardQty.setTag(R.id.etCardQty, qtyWatcher)

        // ---- Status switch ----
        b.switchCardStatus.setOnCheckedChangeListener(null)
        b.switchCardStatus.isChecked = data.isPurchased
        b.tvCardStatusLabel.text = if (data.isPurchased) "Purchased" else "To Buy"
        applyStatusTint(b, data.isPurchased)

        b.switchCardStatus.setOnCheckedChangeListener { _, checked ->
            b.tvCardStatusLabel.text = if (checked) "Purchased" else "To Buy"
            applyStatusTint(b, checked)
            // Only report the change — do NOT mutate `data` here.
            // The Fragment will mutate the live ProcureItem by uniqueId.
            onPurchasedChanged(data.uniqueId, checked)
        }

        // ---- Notes ----
        (b.etCardNotes.getTag(R.id.etCardNotes) as? TextWatcher)?.let {
            b.etCardNotes.removeTextChangedListener(it)
        }
        b.etCardNotes.setText(data.notes)

        val notesWatcher = object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                onNotesChanged(data.uniqueId, s?.toString().orEmpty())
            }
        }
        b.etCardNotes.addTextChangedListener(notesWatcher)
        b.etCardNotes.setTag(R.id.etCardNotes, notesWatcher)

        // ---- Delete ----
        b.ivCardDelete.setOnClickListener { onDelete(data) }
    }

    private fun applyStatusTint(binding: ItemProcureCardBinding, isPurchased: Boolean) {
        val card = binding.root as MaterialCardView
        if (isPurchased) {
            card.setCardBackgroundColor(COLOR_DONE_BG)
            card.strokeColor = COLOR_DONE_STROKE
        } else {
            card.setCardBackgroundColor(COLOR_DEFAULT_BG)
            card.strokeColor = COLOR_DEFAULT_STROKE
        }
    }

    override fun onViewRecycled(holder: VH) {
        super.onViewRecycled(holder)
        val b = holder.binding
        (b.etCardQty.getTag(R.id.etCardQty) as? TextWatcher)?.let {
            b.etCardQty.removeTextChangedListener(it)
        }
        (b.etCardNotes.getTag(R.id.etCardNotes) as? TextWatcher)?.let {
            b.etCardNotes.removeTextChangedListener(it)
        }
    }
}