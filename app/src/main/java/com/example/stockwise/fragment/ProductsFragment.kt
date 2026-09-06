package com.example.stockwise.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.stockwise.R
import com.example.stockwise.commons.ReusableBottomSheet
import com.example.stockwise.databinding.AddProductSheetBinding
import com.example.stockwise.databinding.FragmentProductsBinding
import com.example.stockwise.viewmodels.ProductsViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ProductsFragment : Fragment() {

    private var _binding: FragmentProductsBinding? = null
    private val binding get() = _binding!!

    // Use lateinit var with manual initialization
    private lateinit var viewModel: ProductsViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProductsBinding.inflate(inflater, container, false)

        // Manually initialize ViewModel with Hilt's factory
        viewModel = ViewModelProvider(this)[ProductsViewModel::class.java]

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        observeViewModel()

        binding.fabAddProduct.setOnClickListener {
            openAddProductSheet()
        }

        // Load initial data
        viewModel.loadAllData()
    }

    private fun observeViewModel() {
        // Observe items
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.items.collect { items ->
                updateProductList(items)
            }
        }

        // Observe category save success
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.categorySaveSuccess.collect { success ->
                if (success) {
                    Toast.makeText(requireContext(), "Category saved successfully!", Toast.LENGTH_SHORT).show()
                    viewModel.clearSaveSuccess()
                }
            }
        }

        // Observe item save success
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.itemSaveSuccess.collect { success ->
                if (success) {
                    Toast.makeText(requireContext(), "Item saved successfully!", Toast.LENGTH_SHORT).show()
                    viewModel.clearSaveSuccess()
                }
            }
        }

        // Observe errors
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.error.collect { error ->
                error?.let {
                    Toast.makeText(requireContext(), it, Toast.LENGTH_LONG).show()
                    viewModel.clearError()
                }
            }
        }

        // Observe loading state
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.isLoading.collect { isLoading ->
                // Show/hide loading indicator
                // binding.progressBar.visibility = if (isLoading) View.VISIBLE else View.GONE
            }
        }
    }

    private fun updateProductList(items: List<com.example.stockwise.data.entities.ItemWithCategory>) {
        // TODO: Update your RecyclerView adapter here
        // For now, you can update a TextView with the count
        // binding.tvItemCount.text = "Items: ${items.size}"
    }

    private fun openAddProductSheet() {
        val sheet = ReusableBottomSheet.newInstance(
            layoutRes = R.layout.add_product_sheet
        )

        sheet.setContentBinder { content ->
            val sheetBinding = AddProductSheetBinding.bind(content)

            // Setup category dropdown
            setupCategoryDropdown(sheetBinding)

            // Initially show menu
            showMenu(sheetBinding)

            // ===== MENU BUTTONS =====
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

            // ===== CATEGORY FORM =====
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

            // ===== ITEM FORM =====
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

            // ===== IMAGE SELECTION =====
            sheetBinding.btnSelectImage.setOnClickListener {
                Toast.makeText(requireContext(), "Image selection coming soon", Toast.LENGTH_SHORT).show()
            }
        }

        sheet.show(parentFragmentManager, "AddProductSheet")
    }

    private fun saveCategory(binding: AddProductSheetBinding, sheet: ReusableBottomSheet) {
        val name = binding.etCategoryName.text.toString().trim()
        val description = binding.etCategoryDescription.text.toString().trim()

        if (name.isEmpty()) {
            Toast.makeText(requireContext(), "Please enter a category name", Toast.LENGTH_SHORT).show()
            return
        }

        viewModel.saveCategory(name, description.takeIf { it.isNotEmpty() })
        clearCategoryForm(binding)
        sheet.dismiss()
    }

    private fun saveItem(binding: AddProductSheetBinding, sheet: ReusableBottomSheet) {
        val categoryName = binding.etCategory.text.toString().trim()
        val name = binding.etItemName.text.toString().trim()
        val originalPriceStr = binding.etOriginalPrice.text.toString().trim()
        val sellingPriceStr = binding.etSellingPrice.text.toString().trim()
        val stockStr = binding.etStock.text.toString().trim()
        val description = binding.etItemDescription.text.toString().trim()

        // Use ViewModel's validation
        val validation = viewModel.validateItemInput(
            name = name,
            categoryName = categoryName,
            originalPriceStr = originalPriceStr,
            sellingPriceStr = sellingPriceStr,
            stockStr = stockStr
        )

        if (!validation.isValid) {
            validation.errors.forEach { error ->
                Toast.makeText(requireContext(), error, Toast.LENGTH_SHORT).show()
            }
            return
        }

        // Save the item using validated data
        validation.category?.let { category ->
            viewModel.saveItem(
                categoryId = category.id,
                name = name,
                originalPrice = validation.originalPrice,
                sellingPrice = validation.sellingPrice,
                stock = validation.stock,
                description = description.takeIf { it.isNotEmpty() },
                imageUri = null
            )
        }

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
            Toast.makeText(requireContext(), "No categories available. Please create one first.", Toast.LENGTH_SHORT).show()
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