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
        holder.tvAddress.text = supplier.address

        holder.btnCall?.setOnClickListener { onCallClick(supplier) }
        holder.btnWhatsApp?.setOnClickListener { onWhatsAppClick(supplier) }

        // 3-dot overflow → custom white popup with Delete
        holder.btnOverflow?.setOnClickListener { anchor ->
            showOverflowMenu(anchor) {
                onDeleteClick(supplier)
            }
        }
    }

    override fun getItemCount(): Int = suppliers.size

    fun updateList(newList: List<Supplier>) {
        suppliers = newList
        notifyDataSetChanged()
    }

    // ==========================================
    // Custom overflow popup — white bg, small, aligned to the right
    // ==========================================
    private fun showOverflowMenu(anchor: View, onDelete: () -> Unit) {
        val context = anchor.context

        // Inflate the small menu layout
        val menuView = LayoutInflater.from(context)
            .inflate(R.layout.popup_supplier_menu, null)

        // Create the popup
        val popup = PopupWindow(
            menuView,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            ViewGroup.LayoutParams.WRAP_CONTENT,
            true // focusable so it dismisses on outside tap
        ).apply {
            // Transparent outer background so our rounded drawable shows
            setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            elevation = 8f
            isOutsideTouchable = true
        }

        // Wire up the Delete row
        menuView.findViewById<View>(R.id.menuDelete).setOnClickListener {
            popup.dismiss()
            onDelete()
        }

        // Show it aligned to the right edge of the anchor, just below it
        menuView.measure(
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED),
            View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
        )
        val popupWidth = menuView.measuredWidth
        val xOffset = -(popupWidth - anchor.width) // align right edges
        val yOffset = 8 // small gap below the 3-dot

        popup.showAsDropDown(anchor, xOffset, yOffset)
    }

    companion object {
        private const val MENU_DELETE = 1
    }
}