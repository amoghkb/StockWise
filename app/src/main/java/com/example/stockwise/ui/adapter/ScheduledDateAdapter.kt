package com.example.stockwise.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.stockwise.R
import com.example.stockwise.commons.parseDateKey
import java.text.SimpleDateFormat
import java.util.Locale

/**
 * Adapter for the "Scheduled Procurements" list below the calendar.
 * Each row = one dateKey ("yyyy-MM-dd") that has procurement items.
 */
class ScheduledDateAdapter(
    private val onClick: (dateKey: String) -> Unit
) : ListAdapter<String, ScheduledDateAdapter.VH>(DIFF) {

    companion object {
        private val DIFF = object : DiffUtil.ItemCallback<String>() {
            override fun areItemsTheSame(oldItem: String, newItem: String) =
                oldItem == newItem

            override fun areContentsTheSame(oldItem: String, newItem: String) =
                oldItem == newItem
        }
    }

    private val headerFmt = SimpleDateFormat("EEE, MMM d, yyyy", Locale.getDefault())

    class VH(view: View) : RecyclerView.ViewHolder(view) {
        val tvTitle: TextView = view.findViewById(R.id.tvCardTitle)
        val tvSubtitle: TextView = view.findViewById(R.id.tvCardSubtitle)
        val tvStatus: TextView = view.findViewById(R.id.tvCardStatus)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_scheduled_procurement, parent, false)
        return VH(v)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        val dateKey = getItem(position)
        val cal = parseDateKey(dateKey)

        holder.tvTitle.text = headerFmt.format(cal.time)
        holder.tvSubtitle.text = "Tap to view items"
        holder.tvStatus.text = "Scheduled"

        // Capture the dateKey directly — no position lookup needed.
        holder.itemView.setOnClickListener {
            onClick(dateKey)
        }
    }
}