package com.example.stockwise.fragment

import android.os.Bundle
import android.text.Html
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.core.content.ContextCompat
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.stockwise.R
import com.example.stockwise.commons.ReusableBottomSheet
import com.example.stockwise.commons.toastError
import com.example.stockwise.commons.toastSuccess
import com.example.stockwise.data.entities.Category
import com.example.stockwise.data.entities.ItemWithCategory
import com.example.stockwise.databinding.AddProductSheetBinding
import com.example.stockwise.databinding.FragmentProductsBinding
import com.example.stockwise.viewmodels.ProductsViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch

@AndroidEntryPoint
class ProductsFragment : Fragment() {

    // ==============================
    // 1. BINDING & VIEWMODEL
    // ==============================

    private var _binding: FragmentProductsBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: ProductsViewModel

    // ==============================
    // 2. SEARCH STATE
    // ==============================

    private var currentQuery: String = ""

    private val expandedCategories = mutableSetOf<String>()
    private var hasInitializedDefaultExpansion = false

    // ==============================
    // 3. BOTTOM SHEET DROPDOWN COMPONENTS
    // ==============================

    private var bottomSheetDropdownPopup: PopupWindow? = null
    private var bottomSheetSearchEditText: EditText? = null
    private var bottomSheetListView: ListView? = null
    private var bottomSheetFilteredCategories = mutableListOf<String>()

    // ==============================
    // 4. LIFECYCLE METHODS
    // ==============================

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
        setupClickListeners()
        viewModel.loadAllData()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        bottomSheetDropdownPopup?.dismiss()
        bottomSheetDropdownPopup = null
        _binding = null
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    // ==============================
    // 5. VIEW MODEL OBSERVERS
    // ==============================

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.items.collect {
                renderProducts()
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.categories.collect {
                renderProducts()
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

    // ==============================
    // 6. UI SETUP
    // ==============================

    private fun setupClickListeners() {
        binding.fabAddProduct.setOnClickListener {
            openAddProductSheet()
        }

        binding.etSearchProducts.doOnTextChanged { text, _, _, _ ->
            currentQuery = text.toString().trim()
            renderProducts()
        }
    }

    // ==============================
    // 7. RENDER
    // ==============================

    private fun renderProducts() {
        if (_binding == null) return

        val allItems = viewModel.items.value
        val allCategories = viewModel.categories.value
        val query = currentQuery

        val categoryContainer = binding.categoryContainer
        categoryContainer.removeAllViews()

        if (allCategories.isEmpty()) {
            val emptyView = TextView(requireContext()).apply {
                text = "No categories available.\nTap + to add products."
                textSize = 16f
                setTextColor(ContextCompat.getColor(requireContext(), android.R.color.darker_gray))
                gravity = android.view.Gravity.CENTER
                setPadding(0, 50, 0, 50)
            }
            categoryContainer.addView(emptyView)
            return
        }

        if (!hasInitializedDefaultExpansion) {
            hasInitializedDefaultExpansion = true
        }

        var anyCardShown = false
        var cardIndex = 0

        allCategories.forEach { category ->
            val categoryMatches = query.isEmpty() ||
                    category.name.contains(query, ignoreCase = true)

            val itemsInCategory = allItems.filter { it.categoryName == category.name }

            val itemsToShow = if (categoryMatches) {
                itemsInCategory
            } else {
                itemsInCategory.filter { itemWithCategory ->
                    val item = itemWithCategory.item
                    item.name.contains(query, ignoreCase = true) ||
                            item.id.take(8).contains(query, ignoreCase = true)
                }
            }

            if (query.isNotEmpty() && !categoryMatches && itemsToShow.isEmpty()) {
                return@forEach
            }

            val forceExpanded = query.isNotEmpty()
            val isExpanded = forceExpanded || expandedCategories.contains(category.name)

            val categoryView = createCategoryCard(
                category = category,
                items = itemsToShow,
                isExpanded = isExpanded,
                onToggle = { nowExpanded ->
                    if (nowExpanded) expandedCategories.add(category.name)
                    else expandedCategories.remove(category.name)
                }
            )
            categoryContainer.addView(categoryView)

            val params = categoryView.layoutParams as ViewGroup.MarginLayoutParams
            params.topMargin = if (cardIndex == 0) 0 else 12
            categoryView.layoutParams = params

            cardIndex++
            anyCardShown = true
        }

        if (!anyCardShown) {
            val noResultsView = TextView(requireContext()).apply {
                text = "No results for \"$query\""
                textSize = 16f
                setTextColor(ContextCompat.getColor(requireContext(), android.R.color.darker_gray))
                gravity = android.view.Gravity.CENTER
                setPadding(0, 50, 0, 50)
            }
            categoryContainer.addView(noResultsView)
        }
    }

    private fun createCategoryCard(
        category: Category,
        items: List<ItemWithCategory>,
        isExpanded: Boolean,
        onToggle: (Boolean) -> Unit
    ): View {
        val inflater = LayoutInflater.from(requireContext())
        val cardView = inflater.inflate(R.layout.category_card_item, null)

        val header = cardView.findViewById<LinearLayout>(R.id.headerCategory)
        val nameText = cardView.findViewById<TextView>(R.id.tvCategoryName)
        val countText = cardView.findViewById<TextView>(R.id.tvCategoryCount)
        val chevron = cardView.findViewById<ImageView>(R.id.chevronCategory)
        val itemsContainer = cardView.findViewById<LinearLayout>(R.id.itemsContainer)

        nameText.text = category.name
        countText.text = "${items.size} items"

        itemsContainer.removeAllViews()
        if (items.isNotEmpty()) {
            val divider = View(requireContext()).apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    1
                )
                setBackgroundColor(ContextCompat.getColor(requireContext(), android.R.color.darker_gray))
            }
            itemsContainer.addView(divider)

            items.forEach { itemWithCategory ->
                val itemView = createItemView(itemWithCategory)
                itemsContainer.addView(itemView)
            }
        } else {
            val emptyText = TextView(requireContext()).apply {
                text = "No items in this category"
                textSize = 14f
                setTextColor(ContextCompat.getColor(requireContext(), android.R.color.darker_gray))
                gravity = android.view.Gravity.CENTER
                setPadding(16, 20, 16, 20)
            }
            itemsContainer.addView(emptyText)
        }

        var expanded = isExpanded
        applyExpansionState(itemsContainer, chevron, expanded)

        header.setOnClickListener {
            expanded = !expanded
            applyExpansionState(itemsContainer, chevron, expanded)
            onToggle(expanded)
        }

        return cardView
    }

    private fun applyExpansionState(itemsContainer: View, chevron: ImageView, expanded: Boolean) {
        itemsContainer.visibility = if (expanded) View.VISIBLE else View.GONE
        chevron.setImageResource(
            if (expanded) R.drawable.ic_up_arrow else R.drawable.ic_down_arrow
        )
    }

    private fun createItemView(itemWithCategory: ItemWithCategory): View {
        val inflater = LayoutInflater.from(requireContext())
        val itemView = inflater.inflate(R.layout.category_item_layout, null)

        val item = itemWithCategory.item

        val nameText = itemView.findViewById<TextView>(R.id.tvItemName)
        val skuText = itemView.findViewById<TextView>(R.id.tvItemSku)
        val priceText = itemView.findViewById<TextView>(R.id.tvItemPrice)
        val qtyText = itemView.findViewById<TextView>(R.id.tvItemQty)
        val itemImage = itemView.findViewById<ImageView>(R.id.ivItemImage)

        // Apply styles programmatically
        nameText.setTextAppearance(com.example.stockwise.R.style.BodyText)
        skuText.setTextAppearance(com.example.stockwise.R.style.BodyText)
        priceText.setTextAppearance(com.example.stockwise.R.style.BodyText)
        qtyText.setTextAppearance(com.example.stockwise.R.style.BodyText)

        // Set price text color to blue
        priceText.setTextColor(ContextCompat.getColor(requireContext(), R.color.stock_wise_primary))

        nameText.text = item.name
        skuText.text = "SKU: ${item.id.take(8).uppercase()}"
        priceText.text = "\u20B9${String.format("%.2f", item.sellingPrice)}"
        qtyText.text = "Qty: ${item.stock}"

        // Keep image simple - using default drawable
        itemImage.setImageResource(R.drawable.ic_belts)

        // ADD CLICK LISTENER FOR ITEM NAVIGATION
        itemView.setOnClickListener {
            navigateToItemDetail(itemWithCategory)
        }

        // Add ripple effect for better UX
        itemView.isClickable = true
        itemView.isFocusable = true
        itemView.background = ContextCompat.getDrawable(requireContext(), R.drawable.ripple_effect)

        return itemView
    }
    // ==============================
    // NAVIGATION TO ITEM DETAIL
    // ==============================

    private fun navigateToItemDetail(itemWithCategory: ItemWithCategory) {
        // Create bundle with all item data
        val bundle = Bundle().apply {
            putString("item_id", itemWithCategory.item.id)
            putString("item_name", itemWithCategory.item.name)
            putString("category_name", itemWithCategory.categoryName)
            putString("description", itemWithCategory.item.description ?: "No description available")
            putString("sku", itemWithCategory.item.id.take(8).uppercase())
            putString("brand", "N/A")
            putInt("current_stock", itemWithCategory.item.stock)
            putDouble("cost_price", itemWithCategory.item.originalPrice)
            putDouble("selling_price", itemWithCategory.item.sellingPrice)
            putInt("total_sales", 0)
            putString("category_id", itemWithCategory.item.categoryId)
        }

        // Create the fragment and set arguments
        val fragment = ItemDetailFragment()
        fragment.arguments = bundle

        // Get the container ID from the activity's layout
        val containerId = android.R.id.content

        // Perform the fragment transaction
        parentFragmentManager.beginTransaction()
            .replace(containerId, fragment)
            .addToBackStack("ItemDetailFragment")
            .commit()
    }

    // ==============================
    // 8. BOTTOM SHEET - OPEN & BIND
    // ==============================

    private fun openAddProductSheet() {
        val sheet = ReusableBottomSheet.newInstance(
            layoutRes = R.layout.add_product_sheet
        )

        sheet.setContentBinder { content ->
            val sheetBinding = AddProductSheetBinding.bind(content)

            setupBottomSheetCategoryDropdown(sheetBinding)
            showMenu(sheetBinding)
            preventAutoFocus(sheetBinding)
            setupSheetClickListeners(sheetBinding, sheet)
        }

        sheet.show(parentFragmentManager, "AddProductSheet")
    }

    private fun setupSheetClickListeners(binding: AddProductSheetBinding, sheet: ReusableBottomSheet) {
        binding.btnAddCategory.setOnClickListener {
            showCategoryForm(binding)
        }

        binding.btnAddItem.setOnClickListener {
            showItemForm(binding)
        }

        binding.btnCancel.setOnClickListener {
            dismissSheet(binding, sheet)
        }

        binding.btnBackToMenu.setOnClickListener {
            showMenu(binding)
            clearCategoryForm(binding)
        }

        binding.btnCategoryCancel.setOnClickListener {
            dismissSheet(binding, sheet)
        }

        binding.btnSaveCategory.setOnClickListener {
            saveCategory(binding, sheet)
        }

        binding.btnBackToMenuFromItem.setOnClickListener {
            showMenu(binding)
            clearItemForm(binding)
        }

        binding.btnItemCancel.setOnClickListener {
            dismissSheet(binding, sheet)
        }

        binding.btnSaveItem.setOnClickListener {
            saveItem(binding, sheet)
        }

        binding.btnSelectImage.setOnClickListener {
            "Image selection coming soon".toastError(requireContext())
        }
    }

    private fun preventAutoFocus(binding: AddProductSheetBinding) {
        binding.etCategory.post {
            binding.etCategory.clearFocus()
            binding.etCategory.isFocusable = false
            binding.etCategory.isFocusableInTouchMode = false
        }
    }

    private fun dismissSheet(binding: AddProductSheetBinding, sheet: ReusableBottomSheet) {
        bottomSheetDropdownPopup?.dismiss()
        sheet.dismiss()
    }

    // ==============================
    // 9. NAVIGATION - MENU & FORMS
    // ==============================

    private fun showMenu(binding: AddProductSheetBinding) {
        bottomSheetDropdownPopup?.dismiss()
        binding.menuContainer.visibility = View.VISIBLE
        binding.categoryFormContainer.visibility = View.GONE
        binding.itemFormContainer.visibility = View.GONE
    }

    private fun showCategoryForm(binding: AddProductSheetBinding) {
        bottomSheetDropdownPopup?.dismiss()
        binding.menuContainer.visibility = View.GONE
        binding.categoryFormContainer.visibility = View.VISIBLE
        binding.itemFormContainer.visibility = View.GONE
    }

    private fun showItemForm(binding: AddProductSheetBinding) {
        bottomSheetDropdownPopup?.dismiss()
        binding.menuContainer.visibility = View.GONE
        binding.categoryFormContainer.visibility = View.GONE
        binding.itemFormContainer.visibility = View.VISIBLE

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

        binding.etCategory.isFocusable = false
        binding.etCategory.isFocusableInTouchMode = false
        binding.etCategory.clearFocus()
    }

    private fun hideKeyboard(binding: AddProductSheetBinding) {
        val inputMethodManager = requireContext().getSystemService(android.app.Activity.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        inputMethodManager.hideSoftInputFromWindow(binding.root.windowToken, 0)
    }

    // ==============================
    // 10. FORM CLEAR FUNCTIONS
    // ==============================

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
        bottomSheetDropdownPopup?.dismiss()
    }

    // ==============================
    // 11. SAVE OPERATIONS
    // ==============================

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
        bottomSheetDropdownPopup?.dismiss()
        sheet.dismiss()
    }

    private fun saveItem(binding: AddProductSheetBinding, sheet: ReusableBottomSheet) {
        val categoryName = binding.etCategory.text.toString().trim()
        val name = binding.etItemName.text.toString().trim()
        val originalPriceStr = binding.etOriginalPrice.text.toString().trim()
        val sellingPriceStr = binding.etSellingPrice.text.toString().trim()
        val stockStr = binding.etStock.text.toString().trim()
        val description = binding.etItemDescription.text.toString().trim()

        if (!validateItemFields(binding)) return

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
        bottomSheetDropdownPopup?.dismiss()
        sheet.dismiss()
    }

    private fun validateItemFields(binding: AddProductSheetBinding): Boolean {
        val categoryName = binding.etCategory.text.toString().trim()
        val name = binding.etItemName.text.toString().trim()
        val originalPriceStr = binding.etOriginalPrice.text.toString().trim()
        val sellingPriceStr = binding.etSellingPrice.text.toString().trim()
        val stockStr = binding.etStock.text.toString().trim()

        when {
            categoryName.isEmpty() -> {
                "Please select a category".toastError(requireContext())
                return false
            }
            name.isEmpty() -> {
                "Please enter an item name".toastError(requireContext())
                return false
            }
            name.length < 2 -> {
                "Item name must be at least 2 characters".toastError(requireContext())
                return false
            }
            name.length > 100 -> {
                "Item name must be less than 100 characters".toastError(requireContext())
                return false
            }
            originalPriceStr.isEmpty() -> {
                "Please enter the original price".toastError(requireContext())
                return false
            }
            originalPriceStr.toDoubleOrNull() == null -> {
                "Please enter a valid original price".toastError(requireContext())
                return false
            }
            originalPriceStr.toDoubleOrNull()!! < 0 -> {
                "Original price cannot be negative".toastError(requireContext())
                return false
            }
            sellingPriceStr.isEmpty() -> {
                "Please enter the selling price".toastError(requireContext())
                return false
            }
            sellingPriceStr.toDoubleOrNull() == null -> {
                "Please enter a valid selling price".toastError(requireContext())
                return false
            }
            sellingPriceStr.toDoubleOrNull()!! < 0 -> {
                "Selling price cannot be negative".toastError(requireContext())
                return false
            }
            stockStr.isEmpty() -> {
                "Please enter the stock quantity".toastError(requireContext())
                return false
            }
            stockStr.toIntOrNull() == null -> {
                "Please enter a valid stock quantity".toastError(requireContext())
                return false
            }
            stockStr.toIntOrNull()!! < 0 -> {
                "Stock quantity cannot be negative".toastError(requireContext())
                return false
            }
        }
        return true
    }

    // ==============================
    // 12. BOTTOM SHEET DROPDOWN
    // ==============================

    private fun setupBottomSheetCategoryDropdown(binding: AddProductSheetBinding) {
        binding.etCategory.apply {
            isFocusable = false
            isFocusableInTouchMode = false
            isClickable = true
            isCursorVisible = false
            inputType = android.text.InputType.TYPE_NULL
            clearFocus()
            isFocusableInTouchMode = false

            setOnClickListener {
                showBottomSheetDropdown(binding)
            }
        }
    }

    private fun showBottomSheetDropdown(binding: AddProductSheetBinding) {
        val categories = viewModel.categoryNames.value
        if (categories.isEmpty()) {
            "No categories available. Please create one first.".toastError(requireContext())
            return
        }

        bottomSheetDropdownPopup?.dismiss()

        val popupView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dropdown_category_search, null)

        bottomSheetSearchEditText = popupView.findViewById(R.id.etSearchCategory)
        bottomSheetListView = popupView.findViewById(R.id.lvCategories)

        bottomSheetFilteredCategories = categories.toMutableList()
        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_list_item_1,
            bottomSheetFilteredCategories
        )
        bottomSheetListView?.adapter = adapter

        bottomSheetSearchEditText?.setText("")
        bottomSheetSearchEditText?.doOnTextChanged { text, _, _, _ ->
            filterBottomSheetCategories(text.toString(), adapter)
        }

        bottomSheetListView?.setOnItemClickListener { _, _, position, _ ->
            val selectedCategory = bottomSheetFilteredCategories[position]
            binding.etCategory.setText(selectedCategory)
            bottomSheetDropdownPopup?.dismiss()
        }

        binding.etCategory.post {
            val screenHeight = resources.displayMetrics.heightPixels
            val screenWidth = resources.displayMetrics.widthPixels

            val location = IntArray(2)
            try {
                binding.etCategory.getLocationOnScreen(location)
            } catch (e: Exception) {
                location[0] = 0
                location[1] = 0
            }

            val editTextBottom = location[1] + binding.etCategory.height
            val spaceBelow = screenHeight - editTextBottom - dp(20)

            val maxAvailableHeight = (spaceBelow * 0.95).toInt()
            val maxScreenHeight = (screenHeight * 0.6).toInt()
            val finalHeight = minOf(maxAvailableHeight, maxScreenHeight).coerceAtLeast(dp(250))

            val popupWidth = (screenWidth * 0.9).toInt()

            bottomSheetDropdownPopup = PopupWindow(
                popupView,
                popupWidth,
                finalHeight,
                true
            ).apply {
                isFocusable = true
                isOutsideTouchable = true
                setBackgroundDrawable(
                    ContextCompat.getDrawable(requireContext(), R.drawable.rounded_dropdown_bg)
                )
                elevation = dp(8).toFloat()

                val offsetX = (binding.etCategory.width - popupWidth) / 2
                showAsDropDown(binding.etCategory, offsetX, dp(8))

                setOnDismissListener {
                    bottomSheetDropdownPopup = null
                }
            }
        }
    }

    private fun filterBottomSheetCategories(query: String, adapter: ArrayAdapter<String>) {
        val categories = viewModel.categoryNames.value
        bottomSheetFilteredCategories = if (query.isEmpty()) {
            categories.toMutableList()
        } else {
            categories.filter { it.contains(query, ignoreCase = true) }.toMutableList()
        }
        adapter.clear()
        adapter.addAll(bottomSheetFilteredCategories)
        adapter.notifyDataSetChanged()
    }
}