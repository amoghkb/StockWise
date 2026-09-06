package com.example.stockwise.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.appcompat.widget.AppCompatEditText
import androidx.core.content.ContextCompat
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.stockwise.R
import com.example.stockwise.commons.ReusableBottomSheet
import com.example.stockwise.commons.toastError
import com.example.stockwise.commons.toastSuccess
import com.example.stockwise.databinding.AddProductSheetBinding
import com.example.stockwise.databinding.FragmentProductsBinding
import com.example.stockwise.viewmodels.ProductsViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ProductsFragment : Fragment() {

    private var _binding: FragmentProductsBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: ProductsViewModel

    // Custom dropdown components
    private var customDropdownPopup: PopupWindow? = null
    private var searchEditText: EditText? = null
    private var listView: ListView? = null
    private var filteredCategories = mutableListOf<String>()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProductsBinding.inflate(inflater, container, false)
        viewModel = ViewModelProvider(this)[ProductsViewModel::class.java]
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        observeViewModel()

        binding.fabAddProduct.setOnClickListener {
            openAddProductSheet()
        }

        viewModel.loadAllData()
    }

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.items.collect { items ->
                updateProductList(items)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.categorySaveSuccess.collect { success ->
                if (success) {
                    "Category saved successfully!".toastSuccess(requireContext())
                    viewModel.clearSaveSuccess()
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.itemSaveSuccess.collect { success ->
                if (success) {
                    "Item saved successfully!".toastSuccess(requireContext())
                    viewModel.clearSaveSuccess()
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.error.collect { error ->
                error?.let {
                    it.toastError(requireContext())
                    viewModel.clearError()
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.isLoading.collect { isLoading ->
                // Show/hide loading indicator
            }
        }
    }

    private fun updateProductList(items: List<com.example.stockwise.data.entities.ItemWithCategory>) {
        // TODO: Update RecyclerView
    }
    private fun openAddProductSheet() {
        val sheet = ReusableBottomSheet.newInstance(
            layoutRes = R.layout.add_product_sheet
        )

        sheet.setContentBinder { content ->
            val sheetBinding = AddProductSheetBinding.bind(content)

            setupCategoryDropdown(sheetBinding)
            showMenu(sheetBinding)

            sheetBinding.etCategory.post {
                sheetBinding.etCategory.clearFocus()
                sheetBinding.etCategory.isFocusable = false
                sheetBinding.etCategory.isFocusableInTouchMode = false
            }

            sheetBinding.btnAddCategory.setOnClickListener {
                showCategoryForm(sheetBinding)
            }

            sheetBinding.btnAddItem.setOnClickListener {
                showItemForm(sheetBinding)
            }

            sheetBinding.btnCancel.setOnClickListener {
                customDropdownPopup?.dismiss()
                sheet.dismiss()
            }

            sheetBinding.btnBackToMenu.setOnClickListener {
                showMenu(sheetBinding)
                clearCategoryForm(sheetBinding)
            }

            sheetBinding.btnCategoryCancel.setOnClickListener {
                customDropdownPopup?.dismiss()
                sheet.dismiss()
            }

            sheetBinding.btnSaveCategory.setOnClickListener {
                saveCategory(sheetBinding, sheet)
            }

            sheetBinding.btnBackToMenuFromItem.setOnClickListener {
                showMenu(sheetBinding)
                clearItemForm(sheetBinding)
            }

            sheetBinding.btnItemCancel.setOnClickListener {
                customDropdownPopup?.dismiss()
                sheet.dismiss()
            }

            sheetBinding.btnSaveItem.setOnClickListener {
                saveItem(sheetBinding, sheet)
            }

            sheetBinding.btnSelectImage.setOnClickListener {
                "Image selection coming soon".toastError(requireContext())
            }
        }

        sheet.show(parentFragmentManager, "AddProductSheet")
    }
    private fun showMenu(binding: AddProductSheetBinding) {
        customDropdownPopup?.dismiss()
        binding.menuContainer.visibility = View.VISIBLE
        binding.categoryFormContainer.visibility = View.GONE
        binding.itemFormContainer.visibility = View.GONE
    }


    /*              CATEGORY              */
    private fun showCategoryForm(binding: AddProductSheetBinding) {
        customDropdownPopup?.dismiss()
        binding.menuContainer.visibility = View.GONE
        binding.categoryFormContainer.visibility = View.VISIBLE
        binding.itemFormContainer.visibility = View.GONE
    }
    private fun clearCategoryForm(binding: AddProductSheetBinding) {
        binding.etCategoryName.text?.clear()
        binding.etCategoryDescription.text?.clear()
    }

    //SAVE CATEGORY WITH VALIDATION
    private fun saveCategory(binding: AddProductSheetBinding, sheet: ReusableBottomSheet) {
        val name = binding.etCategoryName.text.toString().trim()
        val description = binding.etCategoryDescription.text.toString().trim()

        when {
            name.isEmpty() -> {
                "Please enter a category name".toastError(requireContext())
                return
            }
            name.length < 2 -> {
                "Category name must be at least 2 characters".toastError(requireContext())
                return
            }
            name.length > 50 -> {
                "Category name must be less than 50 characters".toastError(requireContext())
                return
            }
        }

        viewModel.saveCategory(name, description.takeIf { it.isNotEmpty() })
        clearCategoryForm(binding)
        customDropdownPopup?.dismiss()
        sheet.dismiss()
    }


    /*              ITEM              */
    //SAVE ITEM WITH VALIDATION
    private fun saveItem(binding: AddProductSheetBinding, sheet: ReusableBottomSheet) {
        val categoryName = binding.etCategory.text.toString().trim()
        val name = binding.etItemName.text.toString().trim()
        val originalPriceStr = binding.etOriginalPrice.text.toString().trim()
        val sellingPriceStr = binding.etSellingPrice.text.toString().trim()
        val stockStr = binding.etStock.text.toString().trim()
        val description = binding.etItemDescription.text.toString().trim()

        when {
            categoryName.isEmpty() -> {
                "Please select a category".toastError(requireContext())
                return
            }
            name.isEmpty() -> {
                "Please enter an item name".toastError(requireContext())
                return
            }
            name.length < 2 -> {
                "Item name must be at least 2 characters".toastError(requireContext())
                return
            }
            name.length > 100 -> {
                "Item name must be less than 100 characters".toastError(requireContext())
                return
            }
            originalPriceStr.isEmpty() -> {
                "Please enter the original price".toastError(requireContext())
                return
            }
            originalPriceStr.toDoubleOrNull() == null -> {
                "Please enter a valid original price".toastError(requireContext())
                return
            }
            originalPriceStr.toDoubleOrNull()!! < 0 -> {
                "Original price cannot be negative".toastError(requireContext())
                return
            }
            sellingPriceStr.isEmpty() -> {
                "Please enter the selling price".toastError(requireContext())
                return
            }
            sellingPriceStr.toDoubleOrNull() == null -> {
                "Please enter a valid selling price".toastError(requireContext())
                return
            }
            sellingPriceStr.toDoubleOrNull()!! < 0 -> {
                "Selling price cannot be negative".toastError(requireContext())
                return
            }
            stockStr.isEmpty() -> {
                "Please enter the stock quantity".toastError(requireContext())
                return
            }
            stockStr.toIntOrNull() == null -> {
                "Please enter a valid stock quantity".toastError(requireContext())
                return
            }
            stockStr.toIntOrNull()!! < 0 -> {
                "Stock quantity cannot be negative".toastError(requireContext())
                return
            }
        }

        val category = viewModel.getCategoryByName(categoryName)
        if (category == null) {
            "Selected category not found".toastError(requireContext())
            return
        }

        viewModel.saveItem(
            categoryId = category.id,
            name = name,
            originalPrice = originalPriceStr.toDouble(),
            sellingPrice = sellingPriceStr.toDouble(),
            stock = stockStr.toInt(),
            description = description.takeIf { it.isNotEmpty() },
            imageUri = null
        )

        clearItemForm(binding)
        customDropdownPopup?.dismiss()
        sheet.dismiss()
    }

    //CUSTOM DROPDOWN WITH SEARCH
    private fun setupCategoryDropdown(binding: AddProductSheetBinding) {
        binding.etCategory.apply {
            // Make it non-editable but clickable
            isFocusable = false
            isFocusableInTouchMode = false
            isClickable = true
            isCursorVisible = false
            inputType = android.text.InputType.TYPE_NULL

            // Clear any focus
            clearFocus()

            // Don't request focus
            isFocusableInTouchMode = false

            setOnClickListener {
                // Only open dropdown when explicitly clicked
                showCustomDropdown(binding)
            }
        }
    }
    private fun showCustomDropdown(binding: AddProductSheetBinding) {
        val categories = viewModel.categoryNames.value
        if (categories.isEmpty()) {
            "No categories available. Please create one first.".toastError(requireContext())
            return
        }

        // Dismiss existing popup
        customDropdownPopup?.dismiss()

        // Create popup window
        val popupView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dropdown_category_search, null)

        searchEditText = popupView.findViewById(R.id.etSearchCategory)
        listView = popupView.findViewById(R.id.lvCategories)

        // Set up filtered list
        filteredCategories = categories.toMutableList()
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_list_item_1,
            filteredCategories
        )
        listView?.adapter = adapter

        // Set up search
        searchEditText?.doOnTextChanged { text, _, _, _ ->
            filterCategories(text.toString(), adapter)
        }

        // List item click listener
        listView?.setOnItemClickListener { _, _, position, _ ->
            val selectedCategory = filteredCategories[position]
            binding.etCategory.setText(selectedCategory)
            customDropdownPopup?.dismiss()
        }

        // Get screen dimensions
        val screenHeight = resources.displayMetrics.heightPixels
        val screenWidth = resources.displayMetrics.widthPixels

        // Get the location of the EditText on screen
        val location = IntArray(2)
        binding.etCategory.getLocationOnScreen(location)
        val editTextBottom = location[1] + binding.etCategory.height

        // Calculate available space below the EditText
        val spaceBelow = screenHeight - editTextBottom - 20 // Subtract small margin

        // Use 95% of available space below
        val maxHeight = (spaceBelow * 0.95).toInt()

        // Ensure minimum height
        val finalHeight = maxOf(maxHeight, 400) // At least 400px

        // Create and show popup with maximum height
        customDropdownPopup = PopupWindow(
            popupView,
            (screenWidth * 0.9).toInt(), // 90% of screen width
            finalHeight,
            true
        ).apply {
            isFocusable = true
            isOutsideTouchable = true

            setBackgroundDrawable(
                ContextCompat.getDrawable(requireContext(), R.drawable.rounded_dropdown_bg)
            )

            // Center the dropdown horizontally
            val offsetX = (binding.etCategory.width - (screenWidth * 0.9).toInt()) / 2
            showAsDropDown(binding.etCategory, offsetX, 8)

            setOnDismissListener {
                customDropdownPopup = null
            }
        }
    }
    private fun filterCategories(query: String, adapter: ArrayAdapter<String>) {
        val categories = viewModel.categoryNames.value
        filteredCategories = if (query.isEmpty()) {
            categories.toMutableList()
        } else {
            categories.filter { it.contains(query, ignoreCase = true) }.toMutableList()
        }
        adapter.clear()
        adapter.addAll(filteredCategories)
        adapter.notifyDataSetChanged()

        // Show "No results" if empty
        if (filteredCategories.isEmpty()) {
            // Optionally show a "No results" message
        }
    }
    private fun showItemForm(binding: AddProductSheetBinding) {
        customDropdownPopup?.dismiss()
        binding.menuContainer.visibility = View.GONE
        binding.categoryFormContainer.visibility = View.GONE
        binding.itemFormContainer.visibility = View.VISIBLE

        // Clear focus from all fields to prevent keyboard from showing
        binding.etCategory.clearFocus()
        binding.etItemName.clearFocus()
        binding.etOriginalPrice.clearFocus()
        binding.etSellingPrice.clearFocus()
        binding.etStock.clearFocus()
        binding.etItemDescription.clearFocus()

        // Ensure the category field doesn't have focus
        binding.etCategory.isFocusable = false
        binding.etCategory.isFocusableInTouchMode = false
        binding.etCategory.clearFocus()

        // Hide keyboard if it's showing
        val inputMethodManager = requireContext().getSystemService(android.app.Activity.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        inputMethodManager.hideSoftInputFromWindow(binding.root.windowToken, 0)
    }
    private fun clearItemForm(binding: AddProductSheetBinding) {
        binding.etItemName.text?.clear()
        binding.etCategory.text?.clear()
        binding.etOriginalPrice.text?.clear()
        binding.etSellingPrice.text?.clear()
        binding.etStock.text?.clear()
        binding.etItemDescription.text?.clear()
        customDropdownPopup?.dismiss()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        customDropdownPopup?.dismiss()
        customDropdownPopup = null
        _binding = null
    }
}