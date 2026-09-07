package com.example.stockwise.ui.fragment

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.*
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.bumptech.glide.Glide
import com.bumptech.glide.load.engine.DiskCacheStrategy
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.example.stockwise.R
import com.example.stockwise.commons.ReusableBottomSheet
import com.example.stockwise.commons.toastError
import com.example.stockwise.commons.toastSuccess
import com.example.stockwise.data.entities.Category
import com.example.stockwise.data.entities.ItemWithCategory
import com.example.stockwise.data.entities.Vehicle
import com.example.stockwise.data.entities.VehicleType
import com.example.stockwise.databinding.AddProductSheetBinding
import com.example.stockwise.databinding.FragmentProductsBinding
import com.example.stockwise.ui.adapter.VehicleMultiSelectAdapter
import com.example.stockwise.viewmodels.ProductsViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

@AndroidEntryPoint
class ProductsFragment : Fragment() {

    private var _binding: FragmentProductsBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: ProductsViewModel

    private var selectedImageUri: Uri? = null
    private var currentSheetBinding: AddProductSheetBinding? = null

    private val imagePickerLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                val data: Intent? = result.data
                if (data != null && data.data != null) {
                    selectedImageUri = data.data
                    updateImagePreview()
                }
            }
        }

    private val cameraLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                updateImagePreview()
                "Photo captured successfully".toastSuccess(requireContext())
            } else {
                "Camera cancelled".toastError(requireContext())
            }
        }

    private val permissionLauncher: ActivityResultLauncher<Array<String>> =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            val allGranted = permissions.values.all { it }
            if (allGranted) {
                openImagePicker()
            } else {
                val deniedPermissions = permissions.filter { !it.value }.keys.joinToString()
                "Permission denied: $deniedPermissions".toastError(requireContext())
            }
        }

    private var currentQuery: String = ""
    private val expandedCategories = mutableSetOf<String>()
    private var hasInitializedDefaultExpansion = false

    private var bottomSheetDropdownPopup: PopupWindow? = null
    private var bottomSheetSearchEditText: EditText? = null
    private var bottomSheetListView: ListView? = null
    private var bottomSheetFilteredCategories = mutableListOf<String>()

    private var vehicleTypePopup: PopupWindow? = null
    private var selectedVehicleType: VehicleType? = null

    private var vehicleDropdownPopup: PopupWindow? = null
    private var allVehicles = mutableListOf<Vehicle>()
    private var filteredVehicles = mutableListOf<Vehicle>()
    private var selectedVehicles = mutableListOf<Vehicle>()
    private var vehicleAdapter: VehicleMultiSelectAdapter? = null

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
        vehicleTypePopup?.dismiss()
        vehicleTypePopup = null
        vehicleDropdownPopup?.dismiss()
        vehicleDropdownPopup = null
        _binding = null
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.items.collect { renderProducts() }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.categories.collect { renderProducts() }
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
            viewModel.vehicleSaveSuccess.collect { success ->
                if (success) {
                    "Vehicle saved successfully!".toastSuccess(requireContext())
                    viewModel.clearVehicleSaveSuccess()
                    loadVehiclesForDropdown()
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
    // IMAGE PICKER FUNCTIONS
    // ==============================

    private fun openImagePicker() {
        val options = arrayOf("Camera", "Gallery")
        AlertDialog.Builder(requireContext())
            .setTitle("Select Image")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> openCamera()
                    1 -> openGallery()
                }
            }
            .show()
    }

    private fun openCamera() {
        try {
            if (!requireContext().packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA)) {
                "No camera available on this device".toastError(requireContext())
                return
            }

            if (ContextCompat.checkSelfPermission(
                    requireContext(),
                    android.Manifest.permission.CAMERA
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                "Camera permission not granted".toastError(requireContext())
                return
            }

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val contentValues = android.content.ContentValues().apply {
                    put(MediaStore.Images.Media.DISPLAY_NAME, "IMG_${System.currentTimeMillis()}.jpg")
                    put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                    put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
                }

                val uri = requireContext().contentResolver.insert(
                    MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                    contentValues
                )

                if (uri != null) {
                    selectedImageUri = uri
                    val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                    intent.putExtra(MediaStore.EXTRA_OUTPUT, uri)
                    intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                    intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

                    if (intent.resolveActivity(requireContext().packageManager) != null) {
                        cameraLauncher.launch(intent)
                    } else {
                        "No camera app found".toastError(requireContext())
                    }
                } else {
                    "Failed to create image file".toastError(requireContext())
                }
            } else {
                val photoFile = createImageFile()
                if (photoFile == null) {
                    "Failed to create image file".toastError(requireContext())
                    return
                }

                val photoUri = try {
                    FileProvider.getUriForFile(
                        requireContext(),
                        "${requireContext().packageName}.fileprovider",
                        photoFile
                    )
                } catch (e: Exception) {
                    e.printStackTrace()
                    Uri.fromFile(photoFile)
                }

                selectedImageUri = photoUri
                val intent = Intent(MediaStore.ACTION_IMAGE_CAPTURE)
                intent.putExtra(MediaStore.EXTRA_OUTPUT, photoUri)
                intent.addFlags(Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)

                if (intent.resolveActivity(requireContext().packageManager) != null) {
                    cameraLauncher.launch(intent)
                } else {
                    "No camera app found".toastError(requireContext())
                }
            }
        } catch (e: Exception) {
            e.printStackTrace()
            "Error opening camera: ${e.message}".toastError(requireContext())
        }
    }

    private fun openGallery() {
        val intent = Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        intent.type = "image/*"
        imagePickerLauncher.launch(intent)
    }

    private fun createImageFile(): File? {
        try {
            val timeStamp = System.currentTimeMillis()
            val imageFileName = "JPEG_${timeStamp}_"
            val storageDir = requireContext().getExternalFilesDir(Environment.DIRECTORY_PICTURES)

            if (storageDir == null) {
                return null
            }

            if (!storageDir.exists()) {
                storageDir.mkdirs()
            }

            return File.createTempFile(
                imageFileName,
                ".jpg",
                storageDir
            )
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    private fun updateImagePreview() {
        currentSheetBinding?.let { binding ->
            selectedImageUri?.let { uri ->
                try {
                    binding.ivItemImage.setImageURI(uri)
                    binding.tvTapToSelect.text = "Change Image"
                } catch (e: Exception) {
                    e.printStackTrace()
                    "Failed to load image".toastError(requireContext())
                }
            }
        }
    }

    private fun saveImageToInternalStorage(uri: Uri): String? {
        try {
            val context = requireContext()
            val inputStream: InputStream? = context.contentResolver.openInputStream(uri)
            if (inputStream == null) {
                return null
            }
            val fileName = "item_${System.currentTimeMillis()}.jpg"
            val file = File(context.filesDir, "images")
            if (!file.exists()) {
                file.mkdirs()
            }
            val outputFile = File(file, fileName)
            val outputStream = FileOutputStream(outputFile)
            inputStream.copyTo(outputStream)
            inputStream.close()
            outputStream.close()
            return outputFile.absolutePath
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    private fun checkPermissionAndOpenPicker() {
        val permissions = mutableListOf<String>()

        if (ContextCompat.checkSelfPermission(
                requireContext(),
                android.Manifest.permission.CAMERA
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            permissions.add(android.Manifest.permission.CAMERA)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    requireContext(),
                    android.Manifest.permission.READ_MEDIA_IMAGES
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissions.add(android.Manifest.permission.READ_MEDIA_IMAGES)
            }
        } else {
            if (ContextCompat.checkSelfPermission(
                    requireContext(),
                    android.Manifest.permission.READ_EXTERNAL_STORAGE
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissions.add(android.Manifest.permission.READ_EXTERNAL_STORAGE)
            }
        }

        if (permissions.isEmpty()) {
            openImagePicker()
        } else {
            permissionLauncher.launch(permissions.toTypedArray())
        }
    }

    // ==============================
    // RENDER PRODUCTS
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

        nameText.setTextAppearance(com.example.stockwise.R.style.BodyText)
        skuText.setTextAppearance(com.example.stockwise.R.style.BodyText)
        priceText.setTextAppearance(com.example.stockwise.R.style.BodyText)
        qtyText.setTextAppearance(com.example.stockwise.R.style.BodyText)

        priceText.setTextColor(ContextCompat.getColor(requireContext(), R.color.stock_wise_primary))

        nameText.text = item.name
        skuText.text = "SKU: ${item.id.take(8).uppercase()}"
        priceText.text = "\u20B9${String.format("%.2f", item.sellingPrice)}"
        qtyText.text = "Qty: ${item.stock}"

        val layoutParams = itemImage.layoutParams
        layoutParams.width = dp(48)
        layoutParams.height = dp(48)
        itemImage.layoutParams = layoutParams

        itemImage.scaleType = ImageView.ScaleType.CENTER_CROP

        val imagePath = item.imageUri
        if (!imagePath.isNullOrEmpty()) {
            try {
                val imageFile = File(imagePath)
                if (imageFile.exists()) {
                    Glide.with(requireContext())
                        .load(imageFile)
                        .diskCacheStrategy(DiskCacheStrategy.ALL)
                        .placeholder(R.drawable.ic_image)
                        .error(R.drawable.ic_image)
                        .transform(RoundedCorners(dp(8)))
                        .centerCrop()
                        .override(dp(48), dp(48))
                        .into(itemImage)
                } else {
                    itemImage.setImageResource(R.drawable.ic_image)
                }
            } catch (e: Exception) {
                itemImage.setImageResource(R.drawable.ic_image)
            }
        } else {
            itemImage.setImageResource(R.drawable.ic_image)
        }

        itemView.setOnClickListener {
            navigateToItemDetail(itemWithCategory)
        }

        itemView.isClickable = true
        itemView.isFocusable = true
        itemView.background = ContextCompat.getDrawable(requireContext(), R.drawable.ripple_effect)

        return itemView
    }

    private fun navigateToItemDetail(itemWithCategory: ItemWithCategory) {
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
            putString("image_uri", itemWithCategory.item.imageUri)
        }

        val fragment = ItemDetailFragment()
        fragment.arguments = bundle

        val containerId = android.R.id.content

        parentFragmentManager.beginTransaction()
            .replace(containerId, fragment)
            .addToBackStack("ItemDetailFragment")
            .commit()
    }

    // ==============================
    // BOTTOM SHEET
    // ==============================

    private fun openAddProductSheet() {
        selectedImageUri = null
        selectedVehicleType = null
        selectedVehicles.clear()
        val sheet = ReusableBottomSheet.newInstance(
            layoutRes = R.layout.add_product_sheet
        )

        sheet.setContentBinder { content ->
            val sheetBinding = AddProductSheetBinding.bind(content)
            currentSheetBinding = sheetBinding

            setupBottomSheetCategoryDropdown(sheetBinding)
            setupVehicleTypeDropdown(sheetBinding)
            setupVehicleMultiSelectDropdown(sheetBinding)
            showMenu(sheetBinding)
            preventAutoFocus(sheetBinding)
            setupSheetClickListeners(sheetBinding, sheet)

            sheetBinding.ivItemImage.setImageResource(R.drawable.ic_image)
            sheetBinding.tvTapToSelect.text = "Tap to select image"

            loadVehiclesForDropdown()
        }

        sheet.show(parentFragmentManager, "AddProductSheet")
    }

    private fun loadVehiclesForDropdown() {
        lifecycleScope.launch {
            try {
                allVehicles.clear()
                val vehicles = viewModel.getAllVehicles()
                allVehicles.addAll(vehicles)
                filteredVehicles.clear()
                filteredVehicles.addAll(allVehicles)
                updateVehicleChips()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun setupSheetClickListeners(binding: AddProductSheetBinding, sheet: ReusableBottomSheet) {
        binding.btnAddCategory.setOnClickListener { showCategoryForm(binding) }
        binding.btnAddVehicle.setOnClickListener { showVehicleForm(binding) }
        binding.btnAddItem.setOnClickListener { showItemForm(binding) }
        binding.btnCancel.setOnClickListener { dismissSheet(binding, sheet) }

        binding.btnBackToMenu.setOnClickListener {
            showMenu(binding)
            clearCategoryForm(binding)
        }

        binding.btnBackToMenuFromVehicle.setOnClickListener {
            showMenu(binding)
            clearVehicleForm(binding)
        }

        binding.btnCategoryCancel.setOnClickListener { dismissSheet(binding, sheet) }
        binding.btnSaveCategory.setOnClickListener { saveCategory(binding, sheet) }

        binding.btnVehicleCancel.setOnClickListener { dismissSheet(binding, sheet) }
        binding.btnSaveVehicle.setOnClickListener { saveVehicle(binding, sheet) }

        binding.btnBackToMenuFromItem.setOnClickListener {
            showMenu(binding)
            clearItemForm(binding)
        }

        binding.btnItemCancel.setOnClickListener { dismissSheet(binding, sheet) }
        binding.btnSaveItem.setOnClickListener { saveItem(binding, sheet) }

        binding.btnSelectImage.setOnClickListener {
            checkPermissionAndOpenPicker()
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
        vehicleTypePopup?.dismiss()
        vehicleDropdownPopup?.dismiss()
        currentSheetBinding = null
        sheet.dismiss()
    }

    // ==============================
    // NAVIGATION - MENU & FORMS
    // ==============================

    private fun showMenu(binding: AddProductSheetBinding) {
        bottomSheetDropdownPopup?.dismiss()
        vehicleTypePopup?.dismiss()
        vehicleDropdownPopup?.dismiss()
        binding.menuContainer.visibility = View.VISIBLE
        binding.categoryFormContainer.visibility = View.GONE
        binding.vehicleFormContainer.visibility = View.GONE
        binding.itemFormContainer.visibility = View.GONE
    }

    private fun showCategoryForm(binding: AddProductSheetBinding) {
        bottomSheetDropdownPopup?.dismiss()
        vehicleTypePopup?.dismiss()
        vehicleDropdownPopup?.dismiss()
        binding.menuContainer.visibility = View.GONE
        binding.categoryFormContainer.visibility = View.VISIBLE
        binding.vehicleFormContainer.visibility = View.GONE
        binding.itemFormContainer.visibility = View.GONE
    }

    private fun showVehicleForm(binding: AddProductSheetBinding) {
        bottomSheetDropdownPopup?.dismiss()
        vehicleTypePopup?.dismiss()
        vehicleDropdownPopup?.dismiss()
        binding.menuContainer.visibility = View.GONE
        binding.categoryFormContainer.visibility = View.GONE
        binding.vehicleFormContainer.visibility = View.VISIBLE
        binding.itemFormContainer.visibility = View.GONE
        clearVehicleFormFocus(binding)
    }

    private fun showItemForm(binding: AddProductSheetBinding) {
        bottomSheetDropdownPopup?.dismiss()
        vehicleTypePopup?.dismiss()
        vehicleDropdownPopup?.dismiss()
        binding.menuContainer.visibility = View.GONE
        binding.categoryFormContainer.visibility = View.GONE
        binding.vehicleFormContainer.visibility = View.GONE
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

    private fun clearVehicleFormFocus(binding: AddProductSheetBinding) {
        binding.etVehicleName.clearFocus()
        binding.etVehicleCompany.clearFocus()
        binding.etVehicleType.clearFocus()
        binding.etVehicleModel.clearFocus()
        binding.etVehicleDescription.clearFocus()
        binding.etVehicleType.isFocusable = false
        binding.etVehicleType.isFocusableInTouchMode = false
        binding.etVehicleType.clearFocus()
    }

    private fun hideKeyboard(binding: AddProductSheetBinding) {
        val inputMethodManager = requireContext().getSystemService(android.app.Activity.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
        inputMethodManager.hideSoftInputFromWindow(binding.root.windowToken, 0)
    }

    // ==============================
    // FORM CLEAR FUNCTIONS
    // ==============================

    private fun clearCategoryForm(binding: AddProductSheetBinding) {
        binding.etCategoryName.text?.clear()
        binding.etCategoryDescription.text?.clear()
    }

    private fun clearVehicleForm(binding: AddProductSheetBinding) {
        binding.etVehicleName.text?.clear()
        binding.etVehicleCompany.text?.clear()
        binding.etVehicleType.text?.clear()
        binding.etVehicleModel.text?.clear()
        binding.etVehicleDescription.text?.clear()
        selectedVehicleType = null
        vehicleTypePopup?.dismiss()
    }

    private fun clearItemForm(binding: AddProductSheetBinding) {
        binding.etItemName.text?.clear()
        binding.etCategory.text?.clear()
        binding.etOriginalPrice.text?.clear()
        binding.etSellingPrice.text?.clear()
        binding.etStock.text?.clear()
        binding.etItemDescription.text?.clear()
        binding.ivItemImage.setImageResource(R.drawable.ic_image)
        binding.tvTapToSelect.text = "Tap to select image"
        selectedImageUri = null
        selectedVehicles.clear()
        updateVehicleChips()
        updateVehicleEditText()
        bottomSheetDropdownPopup?.dismiss()
        vehicleDropdownPopup?.dismiss()
    }

    // ==============================
    // VEHICLE MULTI-SELECT DROPDOWN WITH TYPE CHIPS
    // ==============================

    private fun setupVehicleMultiSelectDropdown(binding: AddProductSheetBinding) {
        binding.etVehicles.apply {
            isFocusable = false
            isFocusableInTouchMode = false
            isClickable = true
            isCursorVisible = false
            inputType = android.text.InputType.TYPE_NULL
            clearFocus()

            setOnClickListener {
                showVehicleMultiSelectDropdown(binding)
            }
        }

        binding.etVehicles.post {
            updateVehicleChips()
            updateVehicleEditText()
        }
    }

    private fun showVehicleMultiSelectDropdown(binding: AddProductSheetBinding) {
        if (allVehicles.isEmpty()) {
            "No vehicles available. Please create one first.".toastError(requireContext())
            return
        }

        vehicleDropdownPopup?.dismiss()

        val popupView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dropdown_vehicle_multiselect, null)

        val searchEditText = popupView.findViewById<EditText>(R.id.etSearchVehicle)
        val listView = popupView.findViewById<ListView>(R.id.lvVehicles)
        val tvClearAll = popupView.findViewById<TextView>(R.id.tvClearAll)
        val tvSelectedCount = popupView.findViewById<TextView>(R.id.tvSelectedCount)
        val btnConfirm = popupView.findViewById<TextView>(R.id.btnConfirm)
        val tvEmptyState = popupView.findViewById<TextView>(R.id.tvEmptyState)
        val typeChipsContainer = popupView.findViewById<LinearLayout>(R.id.vehicleTypeChipsContainer)

        filteredVehicles.clear()
        filteredVehicles.addAll(allVehicles)

        vehicleAdapter = VehicleMultiSelectAdapter(
            context = requireContext(),
            vehicles = filteredVehicles,
            selectedVehicles = selectedVehicles,
            onVehicleToggle = { vehicle, isChecked ->
                if (isChecked) {
                    if (!selectedVehicles.contains(vehicle)) selectedVehicles.add(vehicle)
                } else {
                    selectedVehicles.remove(vehicle)
                }
                updateVehicleSelectionUI(tvSelectedCount)
                updateTypeChipSelectionStates(typeChipsContainer)
            }
        )
        listView.adapter = vehicleAdapter

        updateVehicleSelectionUI(tvSelectedCount)

        buildTypeChips(
            container = typeChipsContainer,
            tvSelectedCount = tvSelectedCount,
            tvEmptyState = tvEmptyState,
            searchEditText = searchEditText
        )

        searchEditText.doOnTextChanged { text, _, _, _ ->
            filterVehicles(text?.toString()?.trim() ?: "", tvEmptyState)
        }

        tvClearAll.setOnClickListener {
            selectedVehicles.clear()
            vehicleAdapter?.updateSelection(selectedVehicles)
            updateVehicleSelectionUI(tvSelectedCount)
            updateTypeChipSelectionStates(typeChipsContainer)
        }

        btnConfirm.setOnClickListener {
            vehicleDropdownPopup?.dismiss()
            updateVehicleChips()
            updateVehicleEditText()
        }

        // Popup positioning (unchanged from your version)
        binding.etVehicles.post {
            val screenWidth = resources.displayMetrics.widthPixels
            val screenHeight = resources.displayMetrics.heightPixels

            val widthSpec = View.MeasureSpec.makeMeasureSpec((screenWidth * 0.92).toInt(), View.MeasureSpec.AT_MOST)
            val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            popupView.measure(widthSpec, heightSpec)

            val contentHeight = popupView.measuredHeight + dp(20)

            val location = IntArray(2)
            binding.etVehicles.getLocationOnScreen(location)

            val editTextBottom = location[1] + binding.etVehicles.height
            val spaceBelow = screenHeight - editTextBottom - dp(20)
            val spaceAbove = location[1] - dp(20)

            val maxHeight = (screenHeight * 0.75).toInt()
            val minHeight = dp(500)

            val finalHeight = when {
                contentHeight > spaceBelow && contentHeight > spaceAbove -> minOf(maxHeight, maxOf(spaceBelow, spaceAbove))
                contentHeight < minHeight -> minHeight
                else -> minOf(contentHeight, maxHeight)
            }

            val popupWidth = (screenWidth * 0.92).toInt()
            val xOffset = (binding.etVehicles.width - popupWidth) / 2
            val showAbove = spaceBelow < finalHeight && spaceAbove > finalHeight

            vehicleDropdownPopup = PopupWindow(popupView, popupWidth, finalHeight, true).apply {
                isFocusable = true
                isOutsideTouchable = true
                setBackgroundDrawable(ContextCompat.getDrawable(requireContext(), R.drawable.rounded_dropdown_bg))
                elevation = dp(8).toFloat()

                if (showAbove) {
                    showAsDropDown(binding.etVehicles, xOffset, -finalHeight - binding.etVehicles.height - dp(8))
                } else {
                    showAsDropDown(binding.etVehicles, xOffset, dp(8))
                }

                setOnDismissListener { vehicleDropdownPopup = null }
            }
        }
    }

    private fun vehiclesForChip(type: VehicleType?): List<Vehicle> =
        if (type == null) allVehicles else allVehicles.filter { it.type == type }

    private fun isChipFullySelected(type: VehicleType?): Boolean {
        val group = vehiclesForChip(type)
        return group.isNotEmpty() && group.all { selectedVehicles.contains(it) }
    }

    private fun isChipPartiallySelected(type: VehicleType?): Boolean {
        val group = vehiclesForChip(type)
        return group.isNotEmpty() && group.any { selectedVehicles.contains(it) } && !isChipFullySelected(type)
    }

    private fun toggleChip(type: VehicleType?) {
        val group = vehiclesForChip(type)
        if (isChipFullySelected(type)) {
            // Deselect all vehicles in this group
            selectedVehicles.removeAll(group)
        } else {
            // Select all vehicles in this group
            group.forEach { if (!selectedVehicles.contains(it)) selectedVehicles.add(it) }
        }
    }

    private fun buildTypeChips(
        container: LinearLayout,
        tvSelectedCount: TextView,
        tvEmptyState: TextView,
        searchEditText: EditText
    ) {
        container.removeAllViews()

        // "All" chip — type = null
        container.addView(createTypeChip(type = null, name = "All", iconRes = null, container, tvSelectedCount, tvEmptyState, searchEditText))

        VehicleType.values().forEach { type ->
            val count = allVehicles.count { it.type == type }
            if (count == 0) return@forEach
            container.addView(createTypeChip(type, type.displayName, type.iconRes, container, tvSelectedCount, tvEmptyState, searchEditText))
        }

        updateTypeChipSelectionStates(container)
    }
    private fun createTypeChip(
        type: VehicleType?,
        name: String,
        @DrawableRes iconRes: Int?,
        container: LinearLayout,
        tvSelectedCount: TextView,
        tvEmptyState: TextView,
        searchEditText: EditText
    ): View {
        val chip = LayoutInflater.from(requireContext())
            .inflate(R.layout.item_type_chip, null) as LinearLayout

        // Tag lets updateTypeChipSelectionStates find this chip's type reliably,
        // instead of matching on displayName text.
        chip.tag = type

        val ivIcon: ImageView = chip.findViewById(R.id.ivTypeIcon)
        val tvName: TextView = chip.findViewById(R.id.tvTypeName)
        val tvCount: TextView = chip.findViewById(R.id.tvTypeCount)

        if (iconRes != null) {
            ivIcon.setImageResource(iconRes)
            ivIcon.visibility = View.VISIBLE
        } else {
            ivIcon.visibility = View.GONE
        }
        tvName.text = name

        chip.setOnClickListener {
            toggleChip(type)
            vehicleAdapter?.updateSelection(selectedVehicles)
            updateVehicleSelectionUI(tvSelectedCount)
            updateTypeChipSelectionStates(container)
            filterVehicles(searchEditText.text.toString(), tvEmptyState)
        }

        return chip
    }

    private fun buildTypeChipsWithAutoSelect(
        container: LinearLayout,
        tvSelectedCount: TextView,
        tvEmptyState: TextView,
        searchEditText: EditText,
        typeChipsContainer: LinearLayout
    ) {
        container.removeAllViews()

        // Add "All" chip
        val allChip = createTypeChipWithAutoSelect(
            type = null,
            name = "All",
            iconRes = null,
            count = allVehicles.size,
            isSelected = selectedVehicles.size == allVehicles.size && allVehicles.isNotEmpty(),
            onSelect = {
                selectedVehicles.clear()
                selectedVehicles.addAll(allVehicles)
                vehicleAdapter?.updateSelection(selectedVehicles)
                updateVehicleSelectionUI(tvSelectedCount)
                updateTypeChipSelectionStates(typeChipsContainer)
                filterVehicles(searchEditText.text.toString(), tvEmptyState)
            },
            onDeselect = {
                selectedVehicles.clear()
                vehicleAdapter?.updateSelection(selectedVehicles)
                updateVehicleSelectionUI(tvSelectedCount)
                updateTypeChipSelectionStates(typeChipsContainer)
                filterVehicles(searchEditText.text.toString(), tvEmptyState)
            }
        )
        container.addView(allChip)

        // Show ONLY VehicleTypes that have at least 1 vehicle
        VehicleType.values().forEach { type ->
            val vehiclesOfType = allVehicles.filter { it.type == type }
            val count = vehiclesOfType.size

            // Skip if count is 0 (don't show empty types)
            if (count == 0) return@forEach

            val allSelected = vehiclesOfType.isNotEmpty() && vehiclesOfType.all { selectedVehicles.contains(it) }
            val someSelected = vehiclesOfType.isNotEmpty() && vehiclesOfType.any { selectedVehicles.contains(it) }

            val chip = createTypeChipWithAutoSelect(
                type = type,
                name = type.displayName,
                iconRes = type.iconRes,
                count = count,
                isSelected = allSelected,
                isPartial = someSelected && !allSelected,
                onSelect = {
                    vehiclesOfType.forEach { vehicle ->
                        if (!selectedVehicles.contains(vehicle)) {
                            selectedVehicles.add(vehicle)
                        }
                    }
                    vehicleAdapter?.updateSelection(selectedVehicles)
                    updateVehicleSelectionUI(tvSelectedCount)
                    updateTypeChipSelectionStates(typeChipsContainer)
                    filterVehicles(searchEditText.text.toString(), tvEmptyState)
                },
                onDeselect = {
                    vehiclesOfType.forEach { vehicle ->
                        selectedVehicles.remove(vehicle)
                    }
                    vehicleAdapter?.updateSelection(selectedVehicles)
                    updateVehicleSelectionUI(tvSelectedCount)
                    updateTypeChipSelectionStates(typeChipsContainer)
                    filterVehicles(searchEditText.text.toString(), tvEmptyState)
                }
            )
            container.addView(chip)
        }
    }

    private fun createTypeChipWithAutoSelect(
        type: VehicleType?,
        name: String,
        @DrawableRes iconRes: Int?,
        count: Int,
        isSelected: Boolean = false,
        isPartial: Boolean = false,
        onSelect: () -> Unit,
        onDeselect: () -> Unit
    ): View {
        val chip = LayoutInflater.from(requireContext())
            .inflate(R.layout.item_type_chip, null) as LinearLayout

        val ivIcon: ImageView = chip.findViewById(R.id.ivTypeIcon)
        val tvName: TextView = chip.findViewById(R.id.tvTypeName)
        val tvCount: TextView = chip.findViewById(R.id.tvTypeCount)

        if (iconRes != null) {
            ivIcon.setImageResource(iconRes)
            ivIcon.visibility = View.VISIBLE
            ivIcon.setColorFilter(
                if (isSelected) ContextCompat.getColor(requireContext(), R.color.white)
                else ContextCompat.getColor(requireContext(), R.color.grey)
            )
        } else {
            ivIcon.visibility = View.GONE
        }

        tvName.text = name
        tvName.setTextColor(
            if (isSelected) ContextCompat.getColor(requireContext(), R.color.white)
            else ContextCompat.getColor(requireContext(), R.color.black)
        )

        tvCount.text = "($count)"
        tvCount.setTextColor(
            if (isSelected) ContextCompat.getColor(requireContext(), R.color.white)
            else ContextCompat.getColor(requireContext(), R.color.grey)
        )

        val chipColor = when {
            isSelected -> R.color.chip_selected
            isPartial -> R.color.chip_partial
            else -> R.color.chip_unselected
        }
        chip.isSelected = isSelected
        chip.backgroundTintList = ContextCompat.getColorStateList(requireContext(), chipColor)

        chip.setOnClickListener {
            if (chip.isSelected) {
                chip.isSelected = false
                chip.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.chip_unselected)
                tvName.setTextColor(ContextCompat.getColor(requireContext(), R.color.black))
                tvCount.setTextColor(ContextCompat.getColor(requireContext(), R.color.grey))
                ivIcon.setColorFilter(ContextCompat.getColor(requireContext(), R.color.grey))
                onDeselect()
            } else {
                chip.isSelected = true
                chip.backgroundTintList = ContextCompat.getColorStateList(requireContext(), R.color.chip_selected)
                tvName.setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
                tvCount.setTextColor(ContextCompat.getColor(requireContext(), R.color.white))
                ivIcon.setColorFilter(ContextCompat.getColor(requireContext(), R.color.white))
                onSelect()
            }
        }

        return chip
    }

    private fun updateTypeChipSelectionStates(container: LinearLayout) {
        for (i in 0 until container.childCount) {
            val chip = container.getChildAt(i) as LinearLayout
            val type = chip.tag as VehicleType?
            val tvName = chip.findViewById<TextView>(R.id.tvTypeName)
            val tvCount = chip.findViewById<TextView>(R.id.tvTypeCount)
            val ivIcon = chip.findViewById<ImageView>(R.id.ivTypeIcon)

            val count = vehiclesForChip(type).size
            val selected = isChipFullySelected(type)
            val partial = isChipPartiallySelected(type)

            tvCount.text = "($count)"

            val mainColor = when {
                selected -> R.color.chip_selected
                partial -> R.color.chip_partial
                else -> R.color.black
            }
            val secondaryColor = when {
                selected -> R.color.chip_selected
                partial -> R.color.chip_partial
                else -> R.color.grey
            }

            tvName.setTextColor(ContextCompat.getColor(requireContext(), mainColor))
            tvCount.setTextColor(ContextCompat.getColor(requireContext(), secondaryColor))
            if (ivIcon.visibility == View.VISIBLE) {
                ivIcon.setColorFilter(ContextCompat.getColor(requireContext(), secondaryColor))
            }
        }
    }
    private fun filterVehicles(query: String, tvEmptyState: TextView) {
        val filtered = allVehicles.filter { vehicle ->
            query.isEmpty() ||
                    vehicle.name.contains(query, ignoreCase = true) ||
                    (vehicle.company?.contains(query, ignoreCase = true) == true) ||
                    (vehicle.model?.contains(query, ignoreCase = true) == true)
        }
        vehicleAdapter?.updateList(filtered)
        tvEmptyState.visibility = if (filtered.isEmpty()) View.VISIBLE else View.GONE
    }

    private fun updateVehicleSelectionUI(tvSelectedCount: TextView?) {
        val count = selectedVehicles.size
        tvSelectedCount?.text = "$count vehicle${if (count != 1) "s" else ""} selected"
    }

    private fun updateVehicleEditText() {
        currentSheetBinding?.let { binding ->
            val count = selectedVehicles.size
            if (count == 0) {
                binding.etVehicles.setText("")
                binding.etVehicles.hint = "Select Vehicles"
            } else {
                binding.etVehicles.setText("$count vehicle${if (count > 1) "s" else ""} selected")
            }
        }
    }

    private fun updateVehicleChips() {
        currentSheetBinding?.let { binding ->
            if (selectedVehicles.isEmpty()) {
                binding.selectedVehiclesContainer.visibility = View.GONE
            } else {
                binding.selectedVehiclesContainer.visibility = View.VISIBLE

                val chipsContainer = binding.vehicleChipsContainer
                chipsContainer.removeAllViews()

                val displayVehicles = if (selectedVehicles.size > 4) {
                    selectedVehicles.take(4) + selectedVehicles.drop(4)
                } else {
                    selectedVehicles
                }

                displayVehicles.forEach { vehicle ->
                    val chip = createVehicleChip(vehicle)
                    chipsContainer.addView(chip)
                }

            }
        }
    }

    private fun createMoreChip(count: Int): View {
        val chip = LayoutInflater.from(requireContext())
            .inflate(R.layout.item_vehicle_chip, null) as LinearLayout

        val tvChipName: TextView = chip.findViewById(R.id.tvChipName)
        val ivRemove: ImageView = chip.findViewById(R.id.ivRemoveVehicle)

        tvChipName.text = "+$count more"
        ivRemove.visibility = View.GONE

        return chip
    }

    private fun createVehicleChip(vehicle: Vehicle): View {
        val chip = LayoutInflater.from(requireContext())
            .inflate(R.layout.item_vehicle_chip, null) as LinearLayout

        val tvChipName: TextView = chip.findViewById(R.id.tvChipName)
        val ivRemove: ImageView = chip.findViewById(R.id.ivRemoveVehicle)

        tvChipName.text = vehicle.name

        ivRemove.setOnClickListener {
            selectedVehicles.remove(vehicle)
            updateVehicleChips()
            updateVehicleEditText()
        }

        return chip
    }

    // ==============================
    // BOTTOM SHEET CATEGORY DROPDOWN
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

    // ==============================
    // VEHICLE TYPE DROPDOWN
    // ==============================

    private fun setupVehicleTypeDropdown(binding: AddProductSheetBinding) {
        binding.etVehicleType.apply {
            isFocusable = false
            isFocusableInTouchMode = false
            isClickable = true
            isCursorVisible = false
            inputType = android.text.InputType.TYPE_NULL
            clearFocus()

            setOnClickListener {
                showVehicleTypeDropdown(binding)
            }
        }
    }

    private fun showVehicleTypeDropdown(binding: AddProductSheetBinding) {
        vehicleTypePopup?.dismiss()

        val vehicleTypes = VehicleType.values()
        val typeNames = vehicleTypes.map { it.displayName }

        val popupView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dropdown_category_search, null)

        val searchEditText = popupView.findViewById<EditText>(R.id.etSearchCategory)
        val listView = popupView.findViewById<ListView>(R.id.lvCategories)

        searchEditText.visibility = View.GONE

        val adapter = ArrayAdapter(
            requireContext(),
            android.R.layout.simple_list_item_1,
            typeNames
        )
        listView.adapter = adapter

        listView.setOnItemClickListener { _, _, position, _ ->
            val selectedType = vehicleTypes[position]
            selectedVehicleType = selectedType
            binding.etVehicleType.setText(selectedType.displayName)
            vehicleTypePopup?.dismiss()
        }

        binding.etVehicleType.post {
            val screenHeight = resources.displayMetrics.heightPixels
            val screenWidth = resources.displayMetrics.widthPixels

            val location = IntArray(2)
            try {
                binding.etVehicleType.getLocationOnScreen(location)
            } catch (e: Exception) {
                location[0] = 0
                location[1] = 0
            }

            val editTextBottom = location[1] + binding.etVehicleType.height
            val spaceBelow = screenHeight - editTextBottom - dp(20)

            val maxAvailableHeight = (spaceBelow * 0.95).toInt()
            val maxScreenHeight = (screenHeight * 0.6).toInt()
            val finalHeight = minOf(maxAvailableHeight, maxScreenHeight).coerceAtLeast(dp(200))

            val popupWidth = (screenWidth * 0.9).toInt()

            vehicleTypePopup = PopupWindow(
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

                val offsetX = (binding.etVehicleType.width - popupWidth) / 2
                showAsDropDown(binding.etVehicleType, offsetX, dp(8))

                setOnDismissListener {
                    vehicleTypePopup = null
                }
            }
        }
    }

    // ==============================
    // SAVE OPERATIONS
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

    private fun saveVehicle(binding: AddProductSheetBinding, sheet: ReusableBottomSheet) {
        val name = binding.etVehicleName.text.toString().trim()
        val company = binding.etVehicleCompany.text.toString().trim().takeIf { it.isNotEmpty() }
        val type = selectedVehicleType
        val model = binding.etVehicleModel.text.toString().trim().takeIf { it.isNotEmpty() }
        val description = binding.etVehicleDescription.text.toString().trim().takeIf { it.isNotEmpty() }

        when {
            name.isEmpty() -> {
                "Please enter a vehicle name".toastError(requireContext())
                return
            }
            name.length < 2 -> {
                "Vehicle name must be at least 2 characters".toastError(requireContext())
                return
            }
            name.length > 100 -> {
                "Vehicle name must be less than 100 characters".toastError(requireContext())
                return
            }
            type == null -> {
                "Please select a vehicle type".toastError(requireContext())
                return
            }
        }

        viewModel.saveVehicle(
            name = name,
            company = company,
            type = type,
            model = model,
            description = description
        )

        clearVehicleForm(binding)
        vehicleTypePopup?.dismiss()
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

        var savedImagePath: String? = null
        selectedImageUri?.let { uri ->
            savedImagePath = saveImageToInternalStorage(uri)
            if (savedImagePath == null) {
                "Failed to save image".toastError(requireContext())
                return
            }
        }

        lifecycleScope.launch {
            try {
                val itemId = viewModel.saveItemAndGetId(
                    categoryId = category.id,
                    name = name,
                    originalPrice = originalPriceStr.toDouble(),
                    sellingPrice = sellingPriceStr.toDouble(),
                    stock = stockStr.toInt(),
                    description = description.takeIf { it.isNotEmpty() },
                    imageUri = savedImagePath
                )

                if (selectedVehicles.isNotEmpty() && itemId != null) {
                    val vehicleIds = selectedVehicles.map { it.id }
                    viewModel.assignVehiclesToItem(itemId, vehicleIds)
                }

                clearItemForm(binding)
                bottomSheetDropdownPopup?.dismiss()
                vehicleDropdownPopup?.dismiss()
                sheet.dismiss()

                "Item saved successfully!".toastSuccess(requireContext())
            } catch (e: Exception) {
                "Failed to save item: ${e.message}".toastError(requireContext())
            }
        }
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
}