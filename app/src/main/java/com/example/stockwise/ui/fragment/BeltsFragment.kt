package com.example.stockwise.ui.fragment

import android.app.Activity
import android.content.ActivityNotFoundException
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import android.widget.TextView
import androidx.appcompat.widget.AppCompatButton
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
    private lateinit var ivClearSearch: ImageView

    private var currentSheetBinding: AddSupplierSheetBinding? = null
    private var currentSheet: ReusableBottomSheet? = null

    /**
     * When non-null, the bottom sheet is in "edit" mode and Save will update
     * this supplier instead of inserting a new one.
     */
    private var supplierBeingEdited: Supplier? = null

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
        ivClearSearch = view.findViewById(R.id.ivClearSearch)

        // 2. Setup RecyclerView with action callbacks
        rvSuppliers.layoutManager = LinearLayoutManager(requireContext())
        adapter = SupplierAdapter(
            suppliers = emptyList(),
            onCallClick = { supplier -> makeCall(supplier.phoneNumber) },
            onWhatsAppClick = { supplier -> openWhatsApp(supplier.phoneNumber) },
            onUpdateClick = { supplier -> openEditSupplierSheet(supplier) },
            onDeleteClick = { supplier -> confirmDeleteSupplier(supplier) }
        )
        rvSuppliers.adapter = adapter

        // 3. Observe DB → UI
        observeSuppliers("")

        // 4. Search setup (with clear button)
        setupSearch()

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
        supplierBeingEdited = null
    }

    // ==========================================
    // SEARCH — with clear (×) button
    // ==========================================

    private fun setupSearch() {
        ivClearSearch.alpha = 0f
        ivClearSearch.visibility = View.GONE

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (s.isNullOrEmpty()) {
                    hideClearButtonWithFade(ivClearSearch)
                } else {
                    showClearButtonWithFade(ivClearSearch)
                }
            }

            override fun afterTextChanged(s: Editable?) {
                observeSuppliers(s?.toString().orEmpty())
            }
        })

        ivClearSearch.setOnClickListener {
            animateClearTap(ivClearSearch)
            etSearch.text?.clear()
            observeSuppliers("")
            hideClearButtonWithFade(ivClearSearch)
            hideKeyboard()
        }

        ivClearSearch.setOnTouchListener { _, event -> handleClearTouch(ivClearSearch, event) }
    }

    // ==========================================
    // SHARED CLEAR-BUTTON HELPERS
    // ==========================================

    private fun showClearButtonWithFade(view: View) {
        if (view.visibility == View.VISIBLE && view.alpha == 1f) return
        view.visibility = View.VISIBLE
        view.animate()
            .alpha(1f)
            .setDuration(200)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()
    }

    private fun hideClearButtonWithFade(view: View) {
        if (view.visibility != View.VISIBLE) return
        view.animate()
            .alpha(0f)
            .setDuration(200)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .withEndAction { view.visibility = View.GONE }
            .start()
    }

    private fun animateClearTap(view: View) {
        view.animate()
            .scaleX(0.7f)
            .scaleY(0.7f)
            .setDuration(100)
            .withEndAction {
                view.animate()
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(100)
                    .start()
            }
            .start()
    }

    private fun handleClearTouch(view: View, event: MotionEvent): Boolean {
        when (event.action) {
            MotionEvent.ACTION_DOWN ->
                view.animate().scaleX(0.85f).scaleY(0.85f).setDuration(50).start()
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL ->
                view.animate().scaleX(1f).scaleY(1f).setDuration(50).start()
        }
        return false
    }

    private fun hideKeyboard() {
        val imm = requireContext()
            .getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(etSearch.windowToken, 0)
    }

    // ==========================================
    // ACTION HANDLERS — CALL & WHATSAPP
    // ==========================================

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

    private fun openWhatsApp(rawNumber: String) {
        val cleanNumber = sanitizePhoneNumber(rawNumber)
        if (cleanNumber.isBlank()) {
            "No phone number available".toastError(requireContext())
            return
        }

        val waNumber = cleanNumber.removePrefix("+")
        val waUrl = "https://wa.me/$waNumber"

        try {
            val intent = Intent(Intent.ACTION_VIEW).apply {
                data = Uri.parse(waUrl)
                setPackage("com.whatsapp")
            }
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
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

    private fun sanitizePhoneNumber(input: String): String {
        if (input.isBlank() || input == "—") return ""
        val trimmed = input.trim()
        val hasPlus = trimmed.startsWith("+")
        val digitsOnly = trimmed.filter { it.isDigit() }
        return if (hasPlus) "+$digitsOnly" else digitsOnly
    }

    // ==========================================
    // DELETE SUPPLIER (custom dialog + soft delete)
    // ==========================================

    private fun confirmDeleteSupplier(supplier: Supplier) {
        val dialogView = layoutInflater.inflate(R.layout.dialog_confirm_delete, null)
        val dialog = android.app.Dialog(requireContext()).apply {
            setContentView(dialogView)
            window?.setBackgroundDrawableResource(android.R.color.transparent)
            window?.setLayout(
                (resources.displayMetrics.widthPixels * 0.88f).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setCancelable(true)
        }

        dialogView.findViewById<TextView>(R.id.tvDialogTitle).text = "Delete Supplier"

        dialogView.findViewById<TextView>(R.id.tvDialogMessage).text =
            "Are you sure you want to delete \"${supplier.companyName}\"? This action cannot be undone."

        dialogView.findViewById<AppCompatButton>(R.id.btnCancel)
            .setOnClickListener { dialog.dismiss() }

        dialogView.findViewById<AppCompatButton>(R.id.btnDelete)
            .setOnClickListener {
                dialog.dismiss()
                softDeleteSupplier(supplier)
            }

        dialog.show()
    }

    private fun softDeleteSupplier(supplier: Supplier) {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                sharedViewModel.deleteSupplier(supplier)
                "Supplier deleted".toastSuccess(requireContext())
            } catch (e: Exception) {
                "Failed to delete: ${e.message}".toastError(requireContext())
            }
        }
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
    // ADD / EDIT SUPPLIER BOTTOM SHEET
    // ==========================================

    private fun openAddSupplierSheet() {
        supplierBeingEdited = null
        showSupplierSheet(prefill = null)
    }

    private fun openEditSupplierSheet(supplier: Supplier) {
        supplierBeingEdited = supplier
        showSupplierSheet(prefill = supplier)
    }

    /**
     * Shows the same bottom sheet used for adding, optionally pre-filled
     * with an existing supplier's values.
     */
    private fun showSupplierSheet(prefill: Supplier?) {
        val sheet = ReusableBottomSheet.newInstance(layoutRes = R.layout.add_supplier_sheet)

        sheet.setContentBinder { content ->
            val binding = AddSupplierSheetBinding.bind(content)
            currentSheetBinding = binding
            currentSheet = sheet

            if (prefill != null) {
                binding.etSupplierName.setText(prefill.companyName)
                binding.etContactPerson.setText(
                    prefill.contactPerson.takeUnless { it == "—" } ?: ""
                )
                binding.etPhoneNumber.setText(
                    prefill.phoneNumber.takeUnless { it == "—" } ?: ""
                )
                binding.etAddress.setText(prefill.address.orEmpty())
            }

            binding.btnClose.setOnClickListener { dismissSheet() }
            binding.btnCancel.setOnClickListener { dismissSheet() }
            binding.btnSaveSupplier.setOnClickListener { saveSupplier() }
        }

        sheet.show(parentFragmentManager, "SupplierSheet")
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

        val editing = supplierBeingEdited

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                if (editing != null) {
                    sharedViewModel.updateSupplier(
                        supplier = editing,
                        companyName = name,
                        category = editing.category,
                        contactPerson = contact,
                        phoneNumber = phone,
                        address = address
                    )
                } else {
                    sharedViewModel.addSupplier(
                        companyName = name,
                        category = "General",
                        contactPerson = contact,
                        phoneNumber = phone,
                        address = address
                    )
                }

                if (etSearch.text.isNotEmpty()) {
                    etSearch.text.clear()
                }

                val msg = if (editing != null) "Supplier updated" else "Supplier added successfully!"
                msg.toastSuccess(requireContext())
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
        supplierBeingEdited = null
        sheet?.dismiss()
    }
}