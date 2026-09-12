package com.example.stockwise.ui.fragment

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.stockwise.R
import com.example.stockwise.data.entities.Supplier

class SupplierAdapter(
    private var suppliers: List<Supplier>,
    private val onCallClick: (Supplier) -> Unit = {},
    private val onWhatsAppClick: (Supplier) -> Unit = {}
) : RecyclerView.Adapter<SupplierAdapter.SupplierViewHolder>() {

    inner class SupplierViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvInitials: TextView = itemView.findViewById(R.id.tvInitials)
        val tvCompanyName: TextView = itemView.findViewById(R.id.tvCompanyName)
        val tvContactPerson: TextView = itemView.findViewById(R.id.tvContactPerson)
        val tvPhone: TextView = itemView.findViewById(R.id.tvPhone)
        val tvAddress: TextView = itemView.findViewById(R.id.tvAddress)
        val btnCall: View? = itemView.findViewById(R.id.btnCall)
        val btnWhatsApp: View? = itemView.findViewById(R.id.btnWhatsApp)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SupplierViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_supplier, parent, false)
        return SupplierViewHolder(view)
    }

    override fun onBindViewHolder(holder: SupplierViewHolder, position: Int) {
        val supplier = suppliers[position]

        holder.tvInitials.text = supplier.initials
        holder.tvCompanyName.text = supplier.companyName
        holder.tvContactPerson.text = supplier.contactPerson
        holder.tvPhone.text = supplier.phoneNumber
        holder.tvAddress.text = supplier.address

        holder.btnCall?.setOnClickListener { onCallClick(supplier) }
        holder.btnWhatsApp?.setOnClickListener { onWhatsAppClick(supplier) }
    }

    override fun getItemCount(): Int = suppliers.size

    fun updateList(newList: List<Supplier>) {
        suppliers = newList
        notifyDataSetChanged()
    }
}