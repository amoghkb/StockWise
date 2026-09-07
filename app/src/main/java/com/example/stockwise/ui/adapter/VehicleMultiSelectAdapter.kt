package com.example.stockwise.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.CheckBox
import android.widget.TextView
import com.example.stockwise.R
import com.example.stockwise.data.entities.Vehicle

class VehicleMultiSelectAdapter(
    private val context: android.content.Context,
    private var vehicles: List<Vehicle>,
    private var selectedVehicles: MutableList<Vehicle>,
    private val onVehicleToggle: (Vehicle, Boolean) -> Unit
) : BaseAdapter() {

    override fun getCount(): Int = vehicles.size

    override fun getItem(position: Int): Vehicle = vehicles[position]

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view = convertView ?: LayoutInflater.from(context)
            .inflate(R.layout.item_vehicle_multiselect, parent, false)

        val vehicle = getItem(position)
        val isSelected = selectedVehicles.contains(vehicle)

        val checkbox: CheckBox = view.findViewById(R.id.cbSelectVehicle)
        val tvName: TextView = view.findViewById(R.id.tvVehicleName)
        val tvDetails: TextView = view.findViewById(R.id.tvVehicleDetails)

        tvName.text = vehicle.name

        val details = mutableListOf<String>()
        vehicle.company?.let { details.add(it) }
        vehicle.model?.let { details.add(it) }
        tvDetails.text = if (details.isNotEmpty()) details.joinToString(" • ") else ""
        tvDetails.visibility = if (details.isNotEmpty()) View.VISIBLE else View.GONE

        // avoid re-triggering the listener while we just set the checked state
        checkbox.setOnCheckedChangeListener(null)
        checkbox.isChecked = isSelected

        view.setOnClickListener {
            val newState = !checkbox.isChecked
            checkbox.isChecked = newState
            onVehicleToggle(vehicle, newState)
        }

        checkbox.setOnCheckedChangeListener { _, isChecked ->
            onVehicleToggle(vehicle, isChecked)
        }

        return view
    }

    fun updateList(newVehicles: List<Vehicle>) {
        vehicles = newVehicles
        notifyDataSetChanged()
    }

    fun updateSelection(selected: List<Vehicle>) {
        // snapshot first: `selected` may be the SAME object as `selectedVehicles`
        // (it is, in ProductsFragment) — clearing before copying would wipe it.
        val snapshot = ArrayList(selected)
        selectedVehicles.clear()
        selectedVehicles.addAll(snapshot)
        notifyDataSetChanged()
    }
}