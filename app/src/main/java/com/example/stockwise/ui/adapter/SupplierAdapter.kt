package com.example.stockwise.ui.fragment

import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.PopupWindow
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.stockwise.R
import com.example.stockwise.data.entities.Supplier

class SupplierAdapter(
    private var suppliers: List<Supplier>,
    private val onCallClick: (Supplier) -> Unit = {},
    private val onWhatsAppClick: (Supplier) -> Unit = {},
    private val onUpdateClick: (Supplier) -> Unit = {},
    private val onDeleteClick: (Supplier) -> Unit = {}
) : RecyclerView.Adapter<SupplierAdapter.SupplierViewHolder>() {

    inner class SupplierViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val tvInitials: TextView = itemView.findViewById(R.id.tvInitials)
        val tvCompanyName: TextView = itemView.findViewById(R.id.tvCompanyName)
        val tvContactPerson: TextView = itemView.findViewById(R.id.tvContactPerson)
        val tvPhone: TextView = itemView.findViewById(R.id.tvPhone)
        val tvAddress: TextView = itemView.findViewById(R.id.tvAddress)
        val btnCall: View? = itemView.findViewById(R.id.btnCall)
        val btnWhatsApp: View? = itemView.findViewById(R.id.btnWhatsApp)
        val btnOverflow: ImageView? = itemView.findViewById(R.id.btnOverflow)
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

        val rawAddress = supplier.address?.trim().orEmpty()
        holder.tvAddress.text = if (rawAddress.isEmpty()) {
            "Address not provided"
        } else {
            rawAddress
        }

        holder.btnCall?.setOnClickListener { onCallClick(supplier) }
        holder.btnWhatsApp?.setOnClickListener { onWhatsAppClick(supplier) }

        holder.btnOverflow?.setOnClickListener { anchor ->
            showOverflowMenu(
                anchor = anchor,
                onUpdate = { onUpdateClick(supplier) },
                onDelete = { onDeleteClick(supplier) }
            )
        }
    }
    override fun getItemCount(): Int = suppliers.size

    fun updateList(newList: List<Supplier>) {
        suppliers = newList
        notifyDataSetChanged()
    }

    // ==========================================
    // Custom overflow popup — Update + Delete
    // ==========================================
    private fun showOverflowMenu(
        anchor: View,
        onUpdate: () -> Unit,
        onDelete: () -> Unit
    ) {
        val context = anchor.context

        val menuView = LayoutInflater.from(context)
            .inflate(R.layout.popup_supplier_menu, null)

        // Convert 140dp to pixels
        val density = context.resources.displayMetrics.density
        val popupWidth = (140 * density).toInt()

        val popup = PopupWindow(
            menuView,
            popupWidth,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true
        ).apply {
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            elevation = 8f
            isOutsideTouchable = true
        }

        menuView.findViewById<View>(R.id.menuUpdate).setOnClickListener {
            popup.dismiss()
            onUpdate()
        }

        menuView.findViewById<View>(R.id.menuDelete).setOnClickListener {
            popup.dismiss()
            onDelete()
        }

        val xOffset = -(popupWidth - anchor.width)
        val yOffset = 8

        popup.showAsDropDown(anchor, xOffset, yOffset)
    }
}