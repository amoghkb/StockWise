package com.example.stockwise.fragment

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
import com.example.stockwise.data.entities.VehicleType
import com.example.stockwise.databinding.AddProductSheetBinding
import com.example.stockwise.databinding.FragmentProductsBinding
import com.example.stockwise.viewmodels.ProductsViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

@AndroidEntryPoint
class ProductsFragment : Fragment() {

    // ==============================
    // 1. BINDING & VIEWMODEL
    // ==============================

    private var _binding: FragmentProductsBinding? = null
    private val binding get() = _binding!!

    private lateinit var viewModel: ProductsViewModel

    // ==============================
    // 2. IMAGE SELECTION
    // ==============================

    private var selectedImageUri: Uri? = null
    private var currentSheetBinding: AddProductSheetBinding? = null

    // Image picker launcher for gallery
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

    // Camera launcher
    private val cameraLauncher: ActivityResultLauncher<Intent> =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == Activity.RESULT_OK) {
                updateImagePreview()
                "Photo captured successfully".toastSuccess(requireContext())
            } else {
                "Camera cancelled".toastError(requireContext())
            }
        }

    // Permission launcher
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

    // ==============================
    // 3. SEARCH STATE
    // ==============================

    private var currentQuery: String = ""

    private val expandedCategories = mutableSetOf<String>()
    private var hasInitializedDefaultExpansion = false

    // ==============================
    // 4. BOTTOM SHEET DROPDOWN COMPONENTS
    // ==============================

    private var bottomSheetDropdownPopup: PopupWindow? = null
    private var bottomSheetSearchEditText: EditText? = null
    private var bottomSheetListView: ListView? = null
    private var bottomSheetFilteredCategories = mutableListOf<String>()

    // Vehicle type dropdown popup
    private var vehicleTypePopup: PopupWindow? = null
    private var selectedVehicleType: VehicleType? = null

    // ==============================
    // 5. LIFECYCLE METHODS
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
        vehicleTypePopup?.dismiss()
        vehicleTypePopup = null
        _binding = null
    }

    private fun dp(value: Int): Int =
        (value * resources.displayMetrics.density).toInt()

    // ==============================
    // 6. VIEW MODEL OBSERVERS
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
            viewModel.vehicleSaveSuccess.collect { success ->
                if (success) {
                    "Vehicle saved successfully!".toastSuccess(requireContext())
                    viewModel.clearVehicleSaveSuccess()
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
    // 7. UI SETUP
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
    // 8. IMAGE PICKER FUNCTIONS
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
    // 9. RENDER
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

    // ==============================
    // createItemView WITH GLIDE (UI UNCHANGED)
    // ==============================

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

    // ==============================
    // NAVIGATION TO ITEM DETAIL
    // ==============================

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
    // 10. BOTTOM SHEET - OPEN & BIND
    // ==============================

    private fun openAddProductSheet() {
        selectedImageUri = null
        selectedVehicleType = null
        val sheet = ReusableBottomSheet.newInstance(
            layoutRes = R.layout.add_product_sheet
        )

        sheet.setContentBinder { content ->
            val sheetBinding = AddProductSheetBinding.bind(content)
            currentSheetBinding = sheetBinding

            setupBottomSheetCategoryDropdown(sheetBinding)
            setupVehicleTypeDropdown(sheetBinding)
            showMenu(sheetBinding)
            preventAutoFocus(sheetBinding)
            setupSheetClickListeners(sheetBinding, sheet)

            // Reset image preview
            sheetBinding.ivItemImage.setImageResource(R.drawable.ic_image)
            sheetBinding.tvTapToSelect.text = "Tap to select image"
        }

        sheet.show(parentFragmentManager, "AddProductSheet")
    }

    private fun setupSheetClickListeners(binding: AddProductSheetBinding, sheet: ReusableBottomSheet) {
        binding.btnAddCategory.setOnClickListener {
            showCategoryForm(binding)
        }

        binding.btnAddVehicle.setOnClickListener {
            showVehicleForm(binding)
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

        binding.btnBackToMenuFromVehicle.setOnClickListener {
            showMenu(binding)
            clearVehicleForm(binding)
        }

        binding.btnCategoryCancel.setOnClickListener {
            dismissSheet(binding, sheet)
        }

        binding.btnSaveCategory.setOnClickListener {
            saveCategory(binding, sheet)
        }

        binding.btnVehicleCancel.setOnClickListener {
            dismissSheet(binding, sheet)
        }

        binding.btnSaveVehicle.setOnClickListener {
            saveVehicle(binding, sheet)
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

        // Image selection click listener
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
        currentSheetBinding = null
        sheet.dismiss()
    }

    // ==============================
    // 11. NAVIGATION - MENU & FORMS
    // ==============================

    private fun showMenu(binding: AddProductSheetBinding) {
        bottomSheetDropdownPopup?.dismiss()
        vehicleTypePopup?.dismiss()
        binding.menuContainer.visibility = View.VISIBLE
        binding.categoryFormContainer.visibility = View.GONE
        binding.vehicleFormContainer.visibility = View.GONE
        binding.itemFormContainer.visibility = View.GONE
    }

    private fun showCategoryForm(binding: AddProductSheetBinding) {
        bottomSheetDropdownPopup?.dismiss()
        vehicleTypePopup?.dismiss()
        binding.menuContainer.visibility = View.GONE
        binding.categoryFormContainer.visibility = View.VISIBLE
        binding.vehicleFormContainer.visibility = View.GONE
        binding.itemFormContainer.visibility = View.GONE
    }

    private fun showVehicleForm(binding: AddProductSheetBinding) {
        bottomSheetDropdownPopup?.dismiss()
        vehicleTypePopup?.dismiss()
        binding.menuContainer.visibility = View.GONE
        binding.categoryFormContainer.visibility = View.GONE
        binding.vehicleFormContainer.visibility = View.VISIBLE
        binding.itemFormContainer.visibility = View.GONE

        clearVehicleFormFocus(binding)
    }

    private fun showItemForm(binding: AddProductSheetBinding) {
        bottomSheetDropdownPopup?.dismiss()
        vehicleTypePopup?.dismiss()
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
    // 12. FORM CLEAR FUNCTIONS
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
        bottomSheetDropdownPopup?.dismiss()
    }

    // ==============================
    // 13. SAVE OPERATIONS
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

        viewModel.saveItem(
            categoryId = category.id,
            name = name,
            originalPrice = originalPriceStr.toDouble(),
            sellingPrice = sellingPriceStr.toDouble(),
            stock = stockStr.toInt(),
            description = description.takeIf { it.isNotEmpty() },
            imageUri = savedImagePath
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
    // 14. BOTTOM SHEET DROPDOWN
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
    // 15. VEHICLE TYPE DROPDOWN
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

        // Hide search for vehicle types as it's a fixed list
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
}