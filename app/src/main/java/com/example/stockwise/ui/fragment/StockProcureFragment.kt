package com.example.stockwise.ui.fragment

import android.annotation.SuppressLint
import android.content.Context
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.ImageView
import androidx.appcompat.widget.AppCompatButton
import androidx.core.view.isVisible
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.stockwise.R
import com.example.stockwise.commons.makeDateKey
import com.example.stockwise.commons.toastError
import com.example.stockwise.data.entities.Item
import com.example.stockwise.data.entities.ProcurementEntity
import com.example.stockwise.databinding.FragmentStockProcureBinding
import com.example.stockwise.ui.adapter.CartSearchAdapter
import com.example.stockwise.ui.adapter.ProcureAdapter
import com.example.stockwise.ui.model.ProcureItem
import com.example.stockwise.viewmodels.SharedDataViewModel
import com.google.android.material.bottomsheet.BottomSheetDialog
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@AndroidEntryPoint
class StockProcureFragment : Fragment() {

    private var _binding: FragmentStockProcureBinding? = null
    private val binding get() = _binding!!

    private val sharedViewModel: SharedDataViewModel by activityViewModels()

    private lateinit var searchAdapter: CartSearchAdapter
    private lateinit var procureAdapter: ProcureAdapter

    private val allItems = mutableListOf<Item>()

    /**
     * Single source of truth for the procurement list on this screen.
     * All mutations go through updateProcureItem()/removeProcureItem(),
     * which look items up by stable uniqueId. The adapter never mutates
     * objects directly, and Save reads only from this list.
     */
    private val procureItems = mutableListOf<ProcureItem>()

    /** Row IDs deleted since opening — applied on Save */
    private val deletedRowIds = mutableListOf<Long>()

    private var dateKey: String = ""

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        _binding = FragmentStockProcureBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // ---- Header + dateKey ----
        val year = arguments?.getInt(KEY_YEAR) ?: 0
        val month = arguments?.getInt(KEY_MONTH) ?: 0
        val day = arguments?.getInt(KEY_DAY) ?: 0

        val cal = Calendar.getInstance().apply { set(year, month, day) }
        val formatted = SimpleDateFormat("EEE, MMM d", Locale.getDefault()).format(cal.time)
        dateKey = makeDateKey(year, month, day)

        binding.tvHeader.text = "Procurement for $formatted"

        // ---- Back = Cancel ----
        binding.btnBack.setOnClickListener { cancelAndExit() }

        // ---- Search adapter ----
        searchAdapter = CartSearchAdapter { onItemPicked(it) }
        binding.rvStockSearchResults.layoutManager = LinearLayoutManager(requireContext())
        binding.rvStockSearchResults.adapter = searchAdapter

        // ---- Cards adapter ----
        procureAdapter = ProcureAdapter(
            onDelete = { data -> removeProcureItem(data.uniqueId) },
            onQuantityChanged = { uniqueId, qty ->
                updateProcureItem(uniqueId) { it.quantity = qty }
            },
            onPurchasedChanged = { uniqueId, purchased ->
                updateProcureItem(uniqueId) { it.isPurchased = purchased }
            },
            onNotesChanged = { uniqueId, notes ->
                updateProcureItem(uniqueId) { it.notes = notes }
            }
        )
        binding.rvProcureCards.layoutManager = LinearLayoutManager(requireContext())
        binding.rvProcureCards.adapter = procureAdapter

        // ---- Search bar ----
        binding.etSearchStock.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString()?.trim().orEmpty()
                binding.ivClearSearch.isVisible = query.isNotEmpty()
                binding.ivClearSearch.alpha = if (query.isNotEmpty()) 1f else 0f
                if (query.isEmpty()) showCardsView() else performSearch(query)
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        binding.ivClearSearch.setOnClickListener {
            binding.etSearchStock.text.clear()
            binding.etSearchStock.clearFocus()
            hideKeyboard(binding.etSearchStock)
        }

        binding.ivAddItem.setOnClickListener { openAddCustomItemSheet() }

        binding.btnSaveProcure.setOnClickListener { saveAllAndExit() }
        binding.btnCancelProcure.setOnClickListener { cancelAndExit() }

        // Load catalogue first, then saved rows — sequentially, no race
        loadInitialData()
    }

    // ============================================================
    // LOAD (single sequential coroutine — no race)
    // ============================================================

    private fun loadInitialData() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // 1. Catalogue items
                val items = sharedViewModel.getAllItemsOnce()
                allItems.clear()
                allItems.addAll(items)

                // 2. Saved procurement rows for this date
                val entities = sharedViewModel.getProcurementItemsOnce(dateKey)
                procureItems.clear()
                procureItems.addAll(
                    entities.map { e ->
                        ProcureItem(
                            rowId = e.id,
                            item = if (e.isCustom) null else allItems.find { it.id == e.itemId },
                            customName = e.customName,
                            quantity = e.quantity,
                            isPurchased = e.isPurchased,
                            notes = e.notes,
                            isCustom = e.isCustom,
                            createdAt = e.createdAt
                        )
                    }
                )

                refreshCards()
            } catch (e: Exception) {
                e.printStackTrace()
                "Failed to load procurement data".toastError(requireContext())
            }
        }
    }

    // ============================================================
    // CENTRAL MUTATION HELPERS (single source of truth)
    // ============================================================

    /**
     * Apply [mutate] to the live ProcureItem in `procureItems` matching
     * [uniqueId]. The adapter's object reference is intentionally ignored.
     */
    private fun updateProcureItem(uniqueId: String, mutate: (ProcureItem) -> Unit) {
        val target = procureItems.find { it.uniqueId == uniqueId } ?: return
        mutate(target)
    }

    private fun removeProcureItem(uniqueId: String) {
        val target = procureItems.find { it.uniqueId == uniqueId } ?: return
        target.rowId?.let { deletedRowIds.add(it) }
        procureItems.remove(target)
        refreshCards()
    }

    // ============================================================
    // SAVE / CANCEL
    // ============================================================

    private fun saveAllAndExit() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                // 1. Delete rows removed by the user
                for (id in deletedRowIds) {
                    sharedViewModel.deleteProcurementById(id)
                }

                // 2. Upsert every remaining row
                for (pi in procureItems) {
                    val entity = ProcurementEntity(
                        id = pi.rowId ?: 0L,
                        dateKey = dateKey,
                        itemId = pi.item?.id,
                        customName = pi.customName,
                        quantity = pi.quantity,
                        isPurchased = pi.isPurchased,
                        notes = pi.notes,
                        isCustom = pi.isCustom,
                        createdAt = pi.createdAt ?: Date(),
                        updatedAt = Date()
                    )

                    if (pi.rowId == null) {
                        sharedViewModel.addProcurementItem(entity)
                    } else {
                        sharedViewModel.updateProcurementItem(entity)
                    }
                }

                parentFragmentManager.popBackStack()
            } catch (e: Exception) {
                e.printStackTrace()
                "Failed to save: ${e.message}".toastError(requireContext())
            }
        }
    }

    private fun cancelAndExit() {
        parentFragmentManager.popBackStack()
    }

    // ============================================================
    // VIEW TOGGLES
    // ============================================================

    private fun showCardsView() {
        binding.rvStockSearchResults.visibility = View.GONE
        binding.llNoResults.visibility = View.GONE

        val hasItems = procureItems.isNotEmpty()
        val hasPendingDeletes = deletedRowIds.isNotEmpty()

        if (hasItems) {
            binding.rvProcureCards.visibility = View.VISIBLE
            binding.llEmptyState.visibility = View.GONE
            binding.llEmptyDeleted.visibility = View.GONE
        } else if (hasPendingDeletes) {
            binding.rvProcureCards.visibility = View.GONE
            binding.llEmptyState.visibility = View.GONE
            binding.llEmptyDeleted.visibility = View.VISIBLE
        } else {
            binding.rvProcureCards.visibility = View.GONE
            binding.llEmptyState.visibility = View.VISIBLE
            binding.llEmptyDeleted.visibility = View.GONE
        }

        binding.llBottomActions.visibility =
            if (hasItems || hasPendingDeletes) View.VISIBLE else View.GONE
    }

    private fun showSearchView() {
        binding.llEmptyState.visibility = View.GONE
        binding.llEmptyDeleted.visibility = View.GONE
        binding.rvProcureCards.visibility = View.GONE

        val hasPendingChanges = procureItems.isNotEmpty() || deletedRowIds.isNotEmpty()
        binding.llBottomActions.visibility =
            if (hasPendingChanges) View.VISIBLE else View.GONE
    }

    // ============================================================
    // SEARCH
    // ============================================================

    private fun performSearch(query: String) {
        val filtered = allItems.filter { item ->
            item.name.contains(query, ignoreCase = true) ||
                    item.id.take(8).contains(query, ignoreCase = true)
        }
        searchAdapter.submitList(filtered)
        showSearchView()

        if (filtered.isEmpty()) {
            binding.rvStockSearchResults.visibility = View.GONE
            binding.llNoResults.visibility = View.VISIBLE
            binding.tvNoResultsQuery.text = "No products found for \"$query\""
        } else {
            binding.rvStockSearchResults.visibility = View.VISIBLE
            binding.llNoResults.visibility = View.GONE
        }
    }

    private fun onItemPicked(item: Item) {
        val existing = procureItems.find { !it.isCustom && it.item?.id == item.id }
        if (existing != null) {
            existing.quantity += 1
        } else {
            procureItems.add(
                ProcureItem(
                    item = item,
                    isCustom = false
                )
            )
        }

        binding.etSearchStock.text.clear()
        binding.etSearchStock.clearFocus()
        hideKeyboard(binding.etSearchStock)

        refreshCards()
        showCardsView()
    }

    // ============================================================
    // CUSTOM ITEM SHEET
    // ============================================================

    @SuppressLint("SetTextI18n")
    private fun openAddCustomItemSheet() {
        val dialog = BottomSheetDialog(requireContext(), R.style.BottomSheetDialogTheme)
        val view = layoutInflater.inflate(R.layout.bottom_sheet_add_procure_item, null, false)
        dialog.setContentView(view)

        val etName = view.findViewById<EditText>(R.id.etCustomName)
        val etQty = view.findViewById<EditText>(R.id.etCustomQty)
        val etNotes = view.findViewById<EditText>(R.id.etCustomNotes)
        val btnAdd = view.findViewById<AppCompatButton>(R.id.btnAddCustomItem)
        val ivClose = view.findViewById<ImageView>(R.id.ivCloseSheet)

        ivClose.setOnClickListener { dialog.dismiss() }

        btnAdd.setOnClickListener {
            val name = etName.text?.toString()?.trim().orEmpty()
            val qty = etQty.text?.toString()?.toIntOrNull() ?: 0
            val notes = etNotes.text?.toString()?.trim().orEmpty()

            if (name.isEmpty()) {
                "Enter item name".toastError(requireContext())
                return@setOnClickListener
            }
            if (qty <= 0) {
                "Quantity must be greater than 0".toastError(requireContext())
                return@setOnClickListener
            }

            addCustomProcureItem(name, qty, notes)
            dialog.dismiss()
        }

        dialog.show()
    }

    private fun addCustomProcureItem(name: String, qty: Int, notes: String) {
        val existing = procureItems.find {
            it.isCustom && it.customName.equals(name, ignoreCase = true)
        }
        if (existing != null) {
            existing.quantity += qty
            if (notes.isNotEmpty()) existing.notes = notes
        } else {
            procureItems.add(
                ProcureItem(
                    item = null,
                    customName = name,
                    quantity = qty,
                    isPurchased = false,
                    notes = notes,
                    isCustom = true
                )
            )
        }
        refreshCards()
        showCardsView()
    }

    // ============================================================
    // HELPERS
    // ============================================================

    private fun refreshCards() {
        procureAdapter.submitList(procureItems.toList())
        showCardsView()
    }

    private fun hideKeyboard(view: View) {
        val imm = requireContext()
            .getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        const val KEY_YEAR = "selectedYear"
        const val KEY_MONTH = "selectedMonth"
        const val KEY_DAY = "selectedDay"
    }
}