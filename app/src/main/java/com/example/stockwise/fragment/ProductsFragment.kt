package com.example.stockwise.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.fragment.app.Fragment
import com.example.stockwise.R
import com.example.stockwise.commons.ReusableBottomSheet
import com.example.stockwise.databinding.AddProductSheetBinding
import com.example.stockwise.databinding.FragmentProductsBinding

class ProductsFragment : Fragment() {

    private var _binding: FragmentProductsBinding? = null
    private val binding get() = _binding!!

    // Sample category list
    private val categories = mutableListOf(
        "Electronics",
        "Clothing",
        "Food & Beverages",
        "Books & Media",
        "Home & Garden"
    )

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProductsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.fabAddProduct.setOnClickListener {
            openAddProductSheet()
        }
    }

    private fun openAddProductSheet() {
        // Use wrap content version - sheet will resize based on content
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
            }

            // Main Cancel button - Close sheet directly
            sheetBinding.btnCancel.setOnClickListener {
                sheet.dismiss()
            }

            // ===== CATEGORY FORM =====
            // Back button - Go to menu
            sheetBinding.btnBackToMenu.setOnClickListener {
                showMenu(sheetBinding)
                clearCategoryForm(sheetBinding)
            }

            // Category Cancel button - Close sheet directly
            sheetBinding.btnCategoryCancel.setOnClickListener {
                sheet.dismiss()
            }

            sheetBinding.btnSaveCategory.setOnClickListener {
            }

            // ===== ITEM FORM =====
            // Back button - Go to menu
            sheetBinding.btnBackToMenuFromItem.setOnClickListener {
                showMenu(sheetBinding)
                clearItemForm(sheetBinding)
            }

            // Item Cancel button - Close sheet directly
            sheetBinding.btnItemCancel.setOnClickListener {
                sheet.dismiss()
            }

            sheetBinding.btnSaveItem.setOnClickListener {
            }

            // ===== IMAGE SELECTION =====
            sheetBinding.btnSelectImage.setOnClickListener {
                // TODO: Implement image picker
                Toast.makeText(requireContext(), "Image selection coming soon", Toast.LENGTH_SHORT).show()
            }
        }

        sheet.show(parentFragmentManager, "AddProductSheet")
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
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_dropdown_item_1line,
            categories
        )
        binding.etCategory.setAdapter(adapter)
        binding.etCategory.threshold = 1
    }




    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}