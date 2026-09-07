package com.example.stockwise.fragment

import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import com.example.stockwise.R
import com.example.stockwise.commons.ReusableBottomSheet
import com.example.stockwise.commons.toastError
import com.example.stockwise.commons.toastSuccess
import com.example.stockwise.data.entities.Item
import com.example.stockwise.databinding.AddProductSheetBinding
import com.example.stockwise.viewmodels.ProductsViewModel
import dagger.hilt.android.AndroidEntryPoint
import java.io.File

@AndroidEntryPoint
class ItemDetailFragment : Fragment() {

    private lateinit var tvCategory: TextView
    private lateinit var tvTitle: TextView
    private lateinit var tvDescription: TextView
    private lateinit var tvSku: TextView
    private lateinit var tvCurrentStock: TextView
    private lateinit var tvCostPrice: TextView
    private lateinit var tvSellingPrice: TextView
    private lateinit var tvTotalSales: TextView
    private lateinit var imgProduct: ImageView
    private lateinit var btnAddToSale: Button
    private lateinit var tvStockBadge: TextView
    private lateinit var btnBack: ImageView
    private lateinit var btnEdit: ImageView

    private lateinit var viewModel: ProductsViewModel

    // Store item data for editing
    private var itemId: String = ""
    private var itemName: String = ""
    private var categoryName: String = ""
    private var description: String = ""
    private var sku: String = ""
    private var currentStock: Int = 0
    private var costPrice: Double = 0.0
    private var sellingPrice: Double = 0.0
    private var totalSales: Int = 0
    private var categoryId: String = ""
    private var imageUri: String? = null // ADDED: Store image URI

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_item_detail, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Initialize ViewModel
        viewModel = ViewModelProvider(requireActivity())[ProductsViewModel::class.java]

        try {
            // Initialize views
            tvCategory = view.findViewById(R.id.tv_category)
            tvTitle = view.findViewById(R.id.tv_title)
            tvDescription = view.findViewById(R.id.tv_description)
            tvSku = view.findViewById(R.id.tv_sku)
            tvCurrentStock = view.findViewById(R.id.tv_current_stock)
            tvCostPrice = view.findViewById(R.id.tv_cost_price)
            tvSellingPrice = view.findViewById(R.id.tv_selling_price)
            tvTotalSales = view.findViewById(R.id.tv_total_sales)
            imgProduct = view.findViewById(R.id.img_product)
            btnAddToSale = view.findViewById(R.id.btn_add_to_sale)
            tvStockBadge = view.findViewById(R.id.tv_stock_badge)
            btnBack = view.findViewById(R.id.btn_back)
            btnEdit = view.findViewById(R.id.btn_edit)
        } catch (e: Exception) {
            e.printStackTrace()
            return
        }

        // Set click listeners
        setupClickListeners()

        // Load data from arguments
        loadDataFromArguments()
    }

    private fun setupClickListeners() {
        // Back button - navigate back
        btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        // Edit button - open edit sheet with autofilled fields
        btnEdit.setOnClickListener {
            openEditItemSheet()
        }

        btnAddToSale.setOnClickListener {
            "Add to Sale clicked".toastSuccess(requireContext())
        }
    }

    private fun loadDataFromArguments() {
        arguments?.let { bundle ->
            itemId = bundle.getString("item_id", "")
            itemName = bundle.getString("item_name", "Unknown Item")
            categoryName = bundle.getString("category_name", "Uncategorized")
            description = bundle.getString("description", "No description available")
            sku = bundle.getString("sku", "N/A")
            currentStock = bundle.getInt("current_stock", 0)
            costPrice = bundle.getDouble("cost_price", 0.0)
            sellingPrice = bundle.getDouble("selling_price", 0.0)
            totalSales = bundle.getInt("total_sales", 0)
            categoryId = bundle.getString("category_id", "")
            imageUri = bundle.getString("image_uri", null) // GET IMAGE URI

            // Set data to views
            tvCategory.text = categoryName
            tvTitle.text = itemName
            tvDescription.text = description
            tvSku.text = sku
            tvCurrentStock.text = currentStock.toString()
            tvCostPrice.text = "₹${String.format("%.2f", costPrice)}"
            tvSellingPrice.text = "₹${String.format("%.2f", sellingPrice)}"
            tvTotalSales.text = totalSales.toString()

            // Update badge based on stock level
            updateStockBadge(currentStock)

            // Load image if available - FIXED: Display the image
            loadProductImage()
        }

        // If no arguments were passed, show a message
        if (arguments == null) {
            tvTitle.text = "No item data available"
            imgProduct.setImageResource(R.drawable.ic_belts)
            tvStockBadge.visibility = View.GONE
        }
    }

    /**
     * Loads the product image from the saved URI
     */
    private fun loadProductImage() {
        if (!imageUri.isNullOrEmpty()) {
            try {
                val imageFile = File(imageUri)
                if (imageFile.exists()) {
                    imgProduct.setImageURI(Uri.fromFile(imageFile))
                    imgProduct.scaleType = ImageView.ScaleType.CENTER_CROP
                } else {
                    imgProduct.setImageResource(R.drawable.ic_belts)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                imgProduct.setImageResource(R.drawable.ic_belts)
            }
        } else {
            imgProduct.setImageResource(R.drawable.ic_belts)
        }
    }

    /**
     * Updates the stock badge based on current stock value
     * Shows "Low Stock" in red if stock < 10, otherwise "In Stock" in green
     */
    private fun updateStockBadge(stock: Int) {
        if (stock < 10) {
            tvStockBadge.text = "Low Stock"
            tvStockBadge.setBackgroundResource(R.drawable.badge_red_background)
            tvStockBadge.setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
        } else {
            tvStockBadge.text = "In Stock"
            tvStockBadge.setBackgroundResource(R.drawable.badge_green_background)
            tvStockBadge.setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
        }
    }

    /**
     * Opens the edit item sheet with autofilled fields
     */
    private fun openEditItemSheet() {
        val sheet = ReusableBottomSheet.newInstance(
            layoutRes = R.layout.add_product_sheet
        )

        sheet.setContentBinder { content ->
            val sheetBinding = AddProductSheetBinding.bind(content)

            // Show the item form directly (skip menu)
            showItemFormWithData(sheetBinding)

            // Setup click listeners for the sheet
            setupSheetClickListeners(sheetBinding, sheet)
        }

        sheet.show(parentFragmentManager, "EditItemSheet")
    }

    /**
     * Shows the item form with pre-filled data
     */
    private fun showItemFormWithData(binding: AddProductSheetBinding) {
        // Hide menu and category form, show item form
        binding.menuContainer.visibility = View.GONE
        binding.categoryFormContainer.visibility = View.GONE
        binding.itemFormContainer.visibility = View.VISIBLE

        // Pre-fill all fields with existing data
        binding.etCategory.setText(categoryName)
        binding.etItemName.setText(itemName)
        binding.etOriginalPrice.setText(String.format("%.2f", costPrice))
        binding.etSellingPrice.setText(String.format("%.2f", sellingPrice))
        binding.etStock.setText(currentStock.toString())
        binding.etItemDescription.setText(description)

        // Show existing image if available
        if (!imageUri.isNullOrEmpty()) {
            try {
                val imageFile = File(imageUri)
                if (imageFile.exists()) {
                    binding.ivItemImage.setImageURI(Uri.fromFile(imageFile))
                    binding.tvTapToSelect.text = "Change Image"
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        // Make category field non-editable (just for display)
        binding.etCategory.isFocusable = false
        binding.etCategory.isFocusableInTouchMode = false
        binding.etCategory.isClickable = false
        binding.etCategory.isCursorVisible = false
        binding.etCategory.inputType = android.text.InputType.TYPE_NULL
        binding.etCategory.clearFocus()

        // Change button text to "Update Item"
        binding.btnSaveItem.text = "Update Item"

        // Update title to "Edit Item"
        binding.tvNewItem.text = "Edit Item"

        // Clear focus from all fields
        clearAllFieldsFocus(binding)
        hideKeyboard(binding)
    }

    private fun clearAllFieldsFocus(binding: AddProductSheetBinding) {
        binding.etCategory.clearFocus()
        binding.etItemName.clearFocus()
        binding.etOriginalPrice.clearFocus()
        binding.etSellingPrice.clearFocus()
        binding.etStock.clearFocus()
        binding.etItemDescription.clearFocus()
    }

    private fun hideKeyboard(binding: AddProductSheetBinding) {
        val inputMethodManager = requireContext().getSystemService(android.app.Activity.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        inputMethodManager.hideSoftInputFromWindow(binding.root.windowToken, 0)
    }

    private fun setupSheetClickListeners(binding: AddProductSheetBinding, sheet: ReusableBottomSheet) {
        // Back to menu - close the sheet
        binding.btnBackToMenuFromItem.setOnClickListener {
            sheet.dismiss()
        }

        // Cancel button - close the sheet
        binding.btnItemCancel.setOnClickListener {
            sheet.dismiss()
        }

        // Save/Update button
        binding.btnSaveItem.setOnClickListener {
            updateItem(binding, sheet)
        }

        // Disable unused buttons in edit mode
        binding.btnAddCategory.isEnabled = false
        binding.btnAddItem.isEnabled = false
        binding.btnCancel.isEnabled = false
        binding.btnBackToMenu.visibility = View.GONE
    }

    /**
     * Updates the item with new values and saves to database
     */
    private fun updateItem(binding: AddProductSheetBinding, sheet: ReusableBottomSheet) {
        val name = binding.etItemName.text.toString().trim()
        val originalPriceStr = binding.etOriginalPrice.text.toString().trim()
        val sellingPriceStr = binding.etSellingPrice.text.toString().trim()
        val stockStr = binding.etStock.text.toString().trim()
        val description = binding.etItemDescription.text.toString().trim()

        // Validate fields
        when {
            name.isEmpty() -> {
                "Please enter an item name".toastError(requireContext())
                return
            }
            name.length < 2 -> {
                "Item name must be at least 2 characters".toastError(requireContext())
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

        // Get the updated values
        val updatedCostPrice = originalPriceStr.toDouble()
        val updatedSellingPrice = sellingPriceStr.toDouble()
        val updatedStock = stockStr.toInt()

        // Get the existing item to preserve all fields (isDeleted, createdAt, etc.)
        val existingItem = viewModel.getItemById(itemId)
        if (existingItem == null) {
            "Item not found".toastError(requireContext())
            return
        }

        // Create updated Item object with all fields preserved
        val updatedItem = existingItem.copy(
            name = name,
            originalPrice = updatedCostPrice,
            sellingPrice = updatedSellingPrice,
            stock = updatedStock,
            description = description.takeIf { it.isNotEmpty() }
            // All other fields (id, categoryId, isDeleted, createdAt, etc.) are preserved from existingItem
        )

        // Update local variables
        itemName = name
        costPrice = updatedCostPrice
        sellingPrice = updatedSellingPrice
        currentStock = updatedStock
        this.description = description

        // Update the item in the database via ViewModel
        viewModel.updateItem(updatedItem)

        // Update UI
        tvTitle.text = itemName
        tvDescription.text = description
        tvCurrentStock.text = currentStock.toString()
        tvCostPrice.text = "₹${String.format("%.2f", costPrice)}"
        tvSellingPrice.text = "₹${String.format("%.2f", sellingPrice)}"
        updateStockBadge(currentStock)

        // Show success message
        "Item updated successfully!".toastSuccess(requireContext())

        // Close the sheet
        sheet.dismiss()
    }

    companion object {
        fun newInstance(
            itemId: String = "",
            itemName: String = "Unknown Item",
            categoryName: String = "Uncategorized",
            description: String = "No description available",
            sku: String = "N/A",
            currentStock: Int = 0,
            costPrice: Double = 0.0,
            sellingPrice: Double = 0.0,
            totalSales: Int = 0,
            categoryId: String = "",
            imageUri: String? = null
        ): ItemDetailFragment {
            return ItemDetailFragment().apply {
                arguments = Bundle().apply {
                    putString("item_id", itemId)
                    putString("item_name", itemName)
                    putString("category_name", categoryName)
                    putString("description", description)
                    putString("sku", sku)
                    putInt("current_stock", currentStock)
                    putDouble("cost_price", costPrice)
                    putDouble("selling_price", sellingPrice)
                    putInt("total_sales", totalSales)
                    putString("category_id", categoryId)
                    putString("image_uri", imageUri)
                }
            }
        }
    }
}