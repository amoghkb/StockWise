package com.example.stockwise.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
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
                    // FIX: Use String extension with context parameter
                    "Category saved successfully!".toastSuccess(requireContext())
                    viewModel.clearSaveSuccess()
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.itemSaveSuccess.collect { success ->
                if (success) {
                    // FIX: Use String extension with context parameter
                    "Item saved successfully!".toastSuccess(requireContext())
                    viewModel.clearSaveSuccess()
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.error.collect { error ->
                error?.let {
                    // FIX: Use String extension with context parameter
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

            sheetBinding.btnAddCategory.setOnClickListener {
                showCategoryForm(sheetBinding)
            }

            sheetBinding.btnAddItem.setOnClickListener {
                showItemForm(sheetBinding)
                updateCategoryDropdown(sheetBinding)
            }

            sheetBinding.btnCancel.setOnClickListener {
                sheet.dismiss()
            }

            sheetBinding.btnBackToMenu.setOnClickListener {
                showMenu(sheetBinding)
                clearCategoryForm(sheetBinding)
            }

            sheetBinding.btnCategoryCancel.setOnClickListener {
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

    // ===== SAVE CATEGORY WITH VALIDATION =====
    private fun saveCategory(binding: AddProductSheetBinding, sheet: ReusableBottomSheet) {
        val name = binding.etCategoryName.text.toString().trim()
        val description = binding.etCategoryDescription.text.toString().trim()

        // Validation - Show only first error from top
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
        sheet.dismiss()
    }

    // ===== SAVE ITEM WITH VALIDATION =====
    private fun saveItem(binding: AddProductSheetBinding, sheet: ReusableBottomSheet) {
        val categoryName = binding.etCategory.text.toString().trim()
        val name = binding.etItemName.text.toString().trim()
        val originalPriceStr = binding.etOriginalPrice.text.toString().trim()
        val sellingPriceStr = binding.etSellingPrice.text.toString().trim()
        val stockStr = binding.etStock.text.toString().trim()
        val description = binding.etItemDescription.text.toString().trim()

        // Validation - Show only first error from top to bottom
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

        // Get the category ID
        val category = viewModel.getCategoryByName(categoryName)
        if (category == null) {
            "Selected category not found".toastError(requireContext())
            return
        }

        // Save the item
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
        sheet.dismiss()
    }

    // ===== NAVIGATION =====

    private fun showMenu(binding: AddProductSheetBinding) {
        binding.menuContainer.visibility = View.VISIBLE
        binding.categoryFormContainer.visibility = View.GONE
        binding.itemFormContainer.visibility = View.GONE
    }

    private fun showCategoryForm(binding: AddProductSheetBinding) {
        binding.menuContainer.visibility = View.GONE
        binding.categoryFormContainer.visibility = View.VISIBLE
        binding.itemFormContainer.visibility = View.GONE
    }

    private fun showItemForm(binding: AddProductSheetBinding) {
        binding.menuContainer.visibility = View.GONE
        binding.categoryFormContainer.visibility = View.GONE
        binding.itemFormContainer.visibility = View.VISIBLE
    }

    // ===== FORM CLEAR =====

    private fun clearCategoryForm(binding: AddProductSheetBinding) {
        binding.etCategoryName.text?.clear()
        binding.etCategoryDescription.text?.clear()
    }

    private fun clearItemForm(binding: AddProductSheetBinding) {
        binding.etItemName.text?.clear()
        binding.etCategory.text?.clear()
        binding.etOriginalPrice.text?.clear()
        binding.etSellingPrice.text?.clear()
        binding.etStock.text?.clear()
        binding.etItemDescription.text?.clear()
    }

    // ===== CATEGORY DROPDOWN =====

    private fun setupCategoryDropdown(binding: AddProductSheetBinding) {
        val adapter = ArrayAdapter<String>(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            mutableListOf()
        )
        binding.etCategory.setAdapter(adapter)
        binding.etCategory.threshold = 1

        binding.etCategory.setOnClickListener {
            updateCategoryDropdown(binding)
        }
    }

    private fun updateCategoryDropdown(binding: AddProductSheetBinding) {
        val names = viewModel.categoryNames.value
        if (names.isEmpty()) {
            "No categories available. Please create one first.".toastError(requireContext())
            return
        }

        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            names
        )
        binding.etCategory.setAdapter(adapter)
        binding.etCategory.showDropDown()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}