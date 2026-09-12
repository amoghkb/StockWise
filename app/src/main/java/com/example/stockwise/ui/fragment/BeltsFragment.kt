package com.example.stockwise.ui.fragment

import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.stockwise.R
import com.example.stockwise.commons.ReusableBottomSheet
import com.example.stockwise.commons.toastError
import com.example.stockwise.commons.toastSuccess
import com.example.stockwise.data.entities.Supplier
import com.example.stockwise.databinding.AddSupplierSheetBinding
import com.example.stockwise.viewmodels.SharedDataViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

@AndroidEntryPoint
class BeltsFragment : Fragment() {

    private val sharedViewModel: SharedDataViewModel by activityViewModels()

    private lateinit var rvSuppliers: RecyclerView
    private lateinit var adapter: SupplierAdapter
    private lateinit var tvTotalVendors: TextView
    private lateinit var tvActivePartners: TextView
    private lateinit var etSearch: EditText

    private var currentSheetBinding: AddSupplierSheetBinding? = null
    private var currentSheet: ReusableBottomSheet? = null

    private var collectJob: Job? = null

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_belts, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // 1. Initialize Views
        rvSuppliers = view.findViewById(R.id.rvSuppliers)
        tvTotalVendors = view.findViewById(R.id.tvTotalVendors)
        tvActivePartners = view.findViewById(R.id.tvActivePartners)
        etSearch = view.findViewById(R.id.etSearch)

        // 2. Setup RecyclerView with action callbacks
        rvSuppliers.layoutManager = LinearLayoutManager(requireContext())
        adapter = SupplierAdapter(
            suppliers = emptyList(),
            onCallClick = { supplier -> makeCall(supplier.phoneNumber) },
            onWhatsAppClick = { supplier -> openWhatsApp(supplier.phoneNumber) }
        )
        rvSuppliers.adapter = adapter

        // 3. Observe DB → UI
        observeSuppliers("")

        // 4. Search Listener
        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                observeSuppliers(s?.toString().orEmpty())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        // 5. Add Supplier Button
        view.findViewById<View>(R.id.btnAddSupplier).setOnClickListener {
            openAddSupplierSheet()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        collectJob?.cancel()
        collectJob = null
        currentSheetBinding = null
        currentSheet = null
    }

    // ==========================================
    // ACTION HANDLERS — CALL & WHATSAPP
    // ==========================================

    /**
     * Opens the phone dialer with the number pre-filled.
     * Using ACTION_DIAL avoids needing CALL_PHONE permission —
     * the user just taps the green call button.
     */
    private fun makeCall(rawNumber: String) {
        val cleanNumber = sanitizePhoneNumber(rawNumber)
        if (cleanNumber.isBlank()) {
            "No phone number available".toastError(requireContext())
            return
        }

        try {
            val intent = Intent(Intent.ACTION_DIAL).apply {
                data = Uri.parse("tel:$cleanNumber")
            }
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            "No dialer app found".toastError(requireContext())
        } catch (e: Exception) {
            "Failed to open dialer: ${e.message}".toastError(requireContext())
        }
    }

    /**
     * Opens WhatsApp chat with this number.
     * Tries the official WhatsApp intent first; if WhatsApp isn't installed,
     * falls back to opening wa.me in a browser.
     */
    private fun openWhatsApp(rawNumber: String) {
        val cleanNumber = sanitizePhoneNumber(rawNumber)
        if (cleanNumber.isBlank()) {
            "No phone number available".toastError(requireContext())
            return
        }

        // wa.me expects an international number WITHOUT "+" and WITHOUT spaces
        val waNumber = cleanNumber.removePrefix("+")
        val waUrl = "https://wa.me/$waNumber"

        try {
            // Try WhatsApp's official package intent first
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse(waUrl)
                setPackage("com.whatsapp")
            }
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            // WhatsApp not installed → try WhatsApp Business, then browser
            try {
                val businessIntent = Intent(Intent.ACTION_VIEW).apply {
                    data = Uri.parse(waUrl)
                    setPackage("com.whatsapp.w4b")
                }
                startActivity(businessIntent)
            } catch (e2: ActivityNotFoundException) {
                try {
                    val browserIntent = Intent(Intent.ACTION_VIEW, Uri.parse(waUrl))
                    startActivity(browserIntent)
                } catch (e3: Exception) {
                    "WhatsApp not installed".toastError(requireContext())
                }
            }
        } catch (e: Exception) {
            "Failed to open WhatsApp: ${e.message}".toastError(requireContext())
        }
    }

    /**
     * Cleans a phone number for tel: / wa.me usage.
     * Keeps digits and a single leading '+'. Removes dashes, spaces, brackets.
     */
    private fun sanitizePhoneNumber(input: String): String {
        if (input.isBlank() || input == "—") return ""
        val trimmed = input.trim()
        val hasPlus = trimmed.startsWith("+")
        val digitsOnly = trimmed.filter { it.isDigit() }
        return if (hasPlus) "+$digitsOnly" else digitsOnly
    }

    // ==========================================
    // OBSERVE DB
    // ==========================================

    private fun observeSuppliers(query: String) {
        collectJob?.cancel()
        collectJob = viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                sharedViewModel.getSuppliersFlow(query).collect { suppliers ->
                    tvTotalVendors.text = suppliers.size.toString()
                    tvActivePartners.text = "${suppliers.size} active partners"
                    adapter.updateList(suppliers)
                }
            }
        }
    }

    // ==========================================
    // ADD SUPPLIER BOTTOM SHEET
    // ==========================================

    private fun openAddSupplierSheet() {
        val sheet = ReusableBottomSheet.newInstance(layoutRes = R.layout.add_supplier_sheet)

        sheet.setContentBinder { content ->
            val binding = AddSupplierSheetBinding.bind(content)
            currentSheetBinding = binding
            currentSheet = sheet

            binding.btnClose.setOnClickListener { dismissSheet() }
            binding.btnCancel.setOnClickListener { dismissSheet() }
            binding.btnSaveSupplier.setOnClickListener { saveSupplier() }
        }

        sheet.show(parentFragmentManager, "AddSupplierSheet")
    }

    private fun saveSupplier() {
        val binding = currentSheetBinding ?: return

        val name = binding.etSupplierName.text.toString().trim()
        val contact = binding.etContactPerson.text.toString().trim()
        val phone = binding.etPhoneNumber.text.toString().trim()
        val address = binding.etAddress.text.toString().trim()

        if (name.isEmpty()) {
            "Please enter supplier name".toastError(requireContext())
            return
        }

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                sharedViewModel.addSupplier(
                    companyName = name,
                    category = "General",
                    contactPerson = contact,
                    phoneNumber = phone,
                    address = address
                )

                if (etSearch.text.isNotEmpty()) {
                    etSearch.text.clear()
                }

                "Supplier added successfully!".toastSuccess(requireContext())
                dismissSheet()
            } catch (e: Exception) {
                "Failed to save supplier: ${e.message}".toastError(requireContext())
            }
        }
    }

    private fun dismissSheet() {
        val sheet = currentSheet
        currentSheetBinding = null
        currentSheet = null
        sheet?.dismiss()
    }
}