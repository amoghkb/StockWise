package com.example.stockwise.ui.fragment

import android.app.Activity
import android.app.Dialog
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.ImageButton
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ListView
import android.widget.TextView
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.AppCompatButton
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.lifecycleScope
import com.example.stockwise.R
import com.example.stockwise.commons.ReusableBottomSheet
import com.example.stockwise.commons.toastError
import com.example.stockwise.commons.toastSuccess
import com.example.stockwise.data.entities.Vehicle
import com.example.stockwise.data.entities.VehicleType
import com.example.stockwise.databinding.AddProductSheetBinding
import com.example.stockwise.ui.adapter.VehicleMultiSelectAdapter
import com.example.stockwise.viewmodels.ProductsViewModel
import com.example.stockwise.viewmodels.SharedDataViewModel
import com.google.android.material.bottomsheet.BottomSheetDialog
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream
import java.util.Date

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
    private lateinit var tvAssignedVehicles: TextView

    private lateinit var viewModel: ProductsViewModel
    private val sharedViewModel: SharedDataViewModel by activityViewModels()

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
    private var imageUri: String? = null
    private var selectedImageUri: Uri? = null

    private var allVehicles = mutableListOf<Vehicle>()
    private var filteredVehicles = mutableListOf<Vehicle>()
    private var selectedVehicles = mutableListOf<Vehicle>()
    private var vehicleAdapter: VehicleMultiSelectAdapter? = null
    private var vehicleDropdownPopup: android.widget.PopupWindow? = null
    private var currentSheetBinding: AddProductSheetBinding? = null

    // Sale bottom sheet variables
    private var saleBottomSheet: BottomSheetDialog? = null
    private var currentQuantity = 1
    private var currentSalePrice = 0.0

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

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_item_detail, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        viewModel = ViewModelProvider(requireActivity())[ProductsViewModel::class.java]

        try {
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
            tvAssignedVehicles = view.findViewById(R.id.tv_assigned_vehicles)
        } catch (e: Exception) {
            e.printStackTrace()
            return
        }

        setupClickListeners()
        loadDataFromArguments()
        loadVehicles()
        setupImageLongPress()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        vehicleDropdownPopup?.dismiss()
        vehicleDropdownPopup = null
        currentSheetBinding = null
        saleBottomSheet?.dismiss()
        saleBottomSheet = null
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    private fun setupClickListeners() {
        btnBack.setOnClickListener {
            parentFragmentManager.popBackStack()
        }

        btnEdit.setOnClickListener {
            openEditItemSheet()
        }

        btnAddToSale.setOnClickListener {
            openAddToSaleBottomSheet()
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
            imageUri = bundle.getString("image_uri", null)

            tvCategory.text = categoryName
            tvTitle.text = itemName
            tvDescription.text = description
            tvSku.text = sku
            tvCurrentStock.text = currentStock.toString()
            tvCostPrice.text = "₹${String.format("%.2f", costPrice)}"
            tvSellingPrice.text = "₹${String.format("%.2f", sellingPrice)}"
            tvTotalSales.text = totalSales.toString()

            updateStockBadge(currentStock)
            loadProductImage()
        }

        if (arguments == null) {
            tvTitle.text = "No item data available"
            imgProduct.setImageResource(R.drawable.ic_image)
            tvStockBadge.visibility = View.GONE
        }
    }

    private fun loadProductImage() {
        if (!imageUri.isNullOrEmpty()) {
            try {
                val imageFile = File(imageUri)
                if (imageFile.exists()) {
                    imgProduct.setImageURI(Uri.fromFile(imageFile))
                    imgProduct.scaleType = ImageView.ScaleType.CENTER_CROP
                } else {
                    imgProduct.setImageResource(R.drawable.ic_image)
                    imgProduct.scaleType = ImageView.ScaleType.CENTER_INSIDE
                }
            } catch (e: Exception) {
                e.printStackTrace()
                imgProduct.setImageResource(R.drawable.ic_image)
                imgProduct.scaleType = ImageView.ScaleType.CENTER_INSIDE
            }
        } else {
            imgProduct.setImageResource(R.drawable.ic_image)
            imgProduct.scaleType = ImageView.ScaleType.CENTER_INSIDE
        }
    }

    private fun setupImageLongPress() {
        imgProduct.setOnLongClickListener {
            showFullScreenImage()
            true
        }
    }

    private fun showFullScreenImage() {
        val dialog = Dialog(requireContext(), android.R.style.Theme_Black_NoTitleBar_Fullscreen)
        dialog.setContentView(R.layout.dialog_fullscreen_image)

        val fullScreenImage = dialog.findViewById<ImageView>(R.id.iv_fullscreen_image)
        val closeButton = dialog.findViewById<ImageView>(R.id.iv_close_fullscreen)

        if (!imageUri.isNullOrEmpty()) {
            try {
                val imageFile = File(imageUri)
                if (imageFile.exists()) {
                    fullScreenImage.setImageURI(Uri.fromFile(imageFile))
                } else {
                    fullScreenImage.setImageResource(R.drawable.ic_image)
                }
            } catch (e: Exception) {
                e.printStackTrace()
                fullScreenImage.setImageResource(R.drawable.ic_image)
            }
        } else {
            fullScreenImage.setImageResource(R.drawable.ic_image)
        }

        closeButton.setOnClickListener {
            dialog.dismiss()
        }

        fullScreenImage.setOnClickListener {
            dialog.dismiss()
        }

        dialog.show()
    }

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

    // ==============================
    // ADD TO SALE BOTTOM SHEET
    // ==============================

    private fun openAddToSaleBottomSheet() {
        if (currentStock <= 0) {
            "Item is out of stock!".toastError(requireContext())
            return
        }

        val dialog = BottomSheetDialog(requireContext(), R.style.BottomSheetDialogTheme)
        val view = LayoutInflater.from(requireContext())
            .inflate(R.layout.bottom_sheet_add_to_sale, null)

        dialog.setContentView(view)
        dialog.show()
        saleBottomSheet = dialog

        val tvItemName = view.findViewById<TextView>(R.id.tv_sale_item_name)
        val tvItemPrice = view.findViewById<TextView>(R.id.tv_sale_item_price)
        val tvQuantity = view.findViewById<TextView>(R.id.tv_quantity)
        val tvAvailableStock = view.findViewById<TextView>(R.id.tv_available_stock)
        val tvTotalAmount = view.findViewById<TextView>(R.id.tv_total_amount)
        val etSalePrice = view.findViewById<EditText>(R.id.et_sale_price)
        val btnDecrease = view.findViewById<ImageButton>(R.id.btn_decrease_quantity)
        val btnIncrease = view.findViewById<ImageButton>(R.id.btn_increase_quantity)
        val btnConfirm = view.findViewById<AppCompatButton>(R.id.btn_confirm_sale)

        tvItemName.text = itemName
        tvItemPrice.text = "₹${String.format("%.2f", sellingPrice)}"
        tvQuantity.text = "1"
        tvAvailableStock.text = "Available: $currentStock units"
        etSalePrice.setText(String.format("%.2f", sellingPrice))
        currentQuantity = 1
        currentSalePrice = sellingPrice

        updateTotalAmount(tvTotalAmount)
        updateConfirmButtonState(btnConfirm)

        btnDecrease.setOnClickListener {
            if (currentQuantity > 1) {
                currentQuantity--
                tvQuantity.text = currentQuantity.toString()
                updateTotalAmount(tvTotalAmount)
                updateConfirmButtonState(btnConfirm)
            }
        }

        btnIncrease.setOnClickListener {
            if (currentQuantity < currentStock) {
                currentQuantity++
                tvQuantity.text = currentQuantity.toString()
                updateTotalAmount(tvTotalAmount)
                updateConfirmButtonState(btnConfirm)
            } else {
                "Cannot exceed available stock!".toastError(requireContext())
            }
        }

        etSalePrice.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val priceStr = s?.toString()?.trim() ?: ""
                currentSalePrice = if (priceStr.isNotEmpty()) {
                    priceStr.toDoubleOrNull() ?: 0.0
                } else {
                    0.0
                }
                updateTotalAmount(tvTotalAmount)
                updateConfirmButtonState(btnConfirm)
            }
        })

        btnConfirm.setOnClickListener {
            if (currentQuantity > 0 && currentSalePrice > 0) {
                performSale(
                    quantity = currentQuantity,
                    price = currentSalePrice,
                    dialog = dialog
                )
            } else {
                "Please enter valid quantity and price".toastError(requireContext())
            }
        }

        dialog.setOnDismissListener {
            saleBottomSheet = null
        }
    }

    private fun updateTotalAmount(tvTotalAmount: TextView) {
        val total = currentQuantity * currentSalePrice
        tvTotalAmount.text = "₹${String.format("%.2f", total)}"
    }

    private fun updateConfirmButtonState(btnConfirm: AppCompatButton) {
        val isValid = currentQuantity > 0 && currentSalePrice > 0
        btnConfirm.isEnabled = isValid
        btnConfirm.alpha = if (isValid) 1.0f else 0.5f
    }

    private fun performSale(quantity: Int, price: Double, dialog: BottomSheetDialog) {
        lifecycleScope.launch {
            try {
                val item = viewModel.getItemById(itemId)
                if (item == null) {
                    "Item not found!".toastError(requireContext())
                    return@launch
                }

                if (item.stock < quantity) {
                    "Not enough stock available!".toastError(requireContext())
                    return@launch
                }

                viewModel.recordSale(
                    itemId = item.id,
                    itemName = item.name,
                    quantity = quantity,
                    sellingPrice = price,
                    originalPrice = item.originalPrice
                )

                val newStock = item.stock - quantity
                val updatedItem = item.copy(
                    stock = newStock,
                    updatedAt = Date()
                )

                viewModel.updateItem(updatedItem)

                currentStock = newStock
                tvCurrentStock.text = newStock.toString()
                updateStockBadge(newStock)

                sharedViewModel.refreshDashboardData()

                val totalAmount = quantity * price
                val totalCost = quantity * item.originalPrice
                val profit = totalAmount - totalCost
                val message = "Sold $quantity unit(s) for ₹${String.format("%.2f", totalAmount)}\nProfit: ₹${String.format("%.2f", profit)}"
                message.toastSuccess(requireContext())

                dialog.dismiss()

            } catch (e: Exception) {
                "Failed to process sale: ${e.message}".toastError(requireContext())
            }
        }
    }

    // ==============================
    // VEHICLE METHODS
    // ==============================

    private fun loadVehicles() {
        lifecycleScope.launch {
            try {
                val vehicles = viewModel.getAllVehicles()
                allVehicles.clear()
                allVehicles.addAll(vehicles)

                val assignedVehicleIds = viewModel.getAssignedVehicleIdsForItem(itemId)
                selectedVehicles.clear()
                selectedVehicles.addAll(allVehicles.filter { it.id in assignedVehicleIds })

                updateAssignedVehiclesDisplay()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun updateAssignedVehiclesDisplay() {
        if (selectedVehicles.isEmpty()) {
            tvAssignedVehicles.text = "No compatible vehicles"
        } else {
            val vehicleNames = selectedVehicles.joinToString(", ") { it.name }
            tvAssignedVehicles.text = vehicleNames
        }
    }

    // ==============================
    // EDIT ITEM SHEET
    // ==============================

    private fun openEditItemSheet() {
        val sheet = ReusableBottomSheet.newInstance(
            layoutRes = R.layout.add_product_sheet
        )

        sheet.setContentBinder { content ->
            val sheetBinding = AddProductSheetBinding.bind(content)
            currentSheetBinding = sheetBinding
            showItemFormWithData(sheetBinding)
            setupSheetClickListeners(sheetBinding, sheet)
            setupVehicleMultiSelectDropdown(sheetBinding)
        }

        sheet.show(parentFragmentManager, "EditItemSheet")
    }

    private fun showItemFormWithData(binding: AddProductSheetBinding) {
        binding.menuContainer.visibility = View.GONE
        binding.categoryFormContainer.visibility = View.GONE
        binding.itemFormContainer.visibility = View.VISIBLE

        binding.etCategory.setText(categoryName)
        binding.etItemName.setText(itemName)
        binding.etOriginalPrice.setText(String.format("%.2f", costPrice))
        binding.etSellingPrice.setText(String.format("%.2f", sellingPrice))
        binding.etStock.setText(currentStock.toString())
        binding.etItemDescription.setText(description)

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

        binding.etCategory.isFocusable = false
        binding.etCategory.isFocusableInTouchMode = false
        binding.etCategory.isClickable = true
        binding.etCategory.isCursorVisible = false
        binding.etCategory.inputType = InputType.TYPE_NULL

        binding.btnSaveItem.text = "Update Item"
        binding.tvNewItem.text = "Edit Item"

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
        val inputMethodManager = requireContext().getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
        inputMethodManager.hideSoftInputFromWindow(binding.root.windowToken, 0)
    }

    private fun setupSheetClickListeners(binding: AddProductSheetBinding, sheet: ReusableBottomSheet) {
        binding.btnBackToMenuFromItem.setOnClickListener {
            sheet.dismiss()
        }

        binding.btnItemCancel.setOnClickListener {
            sheet.dismiss()
        }

        binding.btnSaveItem.setOnClickListener {
            updateItem(binding, sheet)
        }

        binding.btnSelectImage.setOnClickListener {
            checkPermissionAndOpenPicker()
        }

        binding.btnAddCategory.isEnabled = false
        binding.btnAddItem.isEnabled = false
        binding.btnCancel.isEnabled = false
        binding.btnBackToMenu.visibility = View.GONE
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
                "No camera available".toastError(requireContext())
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

            if (storageDir == null) return null
            if (!storageDir.exists()) storageDir.mkdirs()

            return File.createTempFile(imageFileName, ".jpg", storageDir)
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    private fun saveImageToInternalStorage(uri: Uri): String? {
        try {
            val inputStream: InputStream? = requireContext().contentResolver.openInputStream(uri)
            if (inputStream == null) return null

            val fileName = "item_${System.currentTimeMillis()}.jpg"
            val file = File(requireContext().filesDir, "images")
            if (!file.exists()) file.mkdirs()

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

    private fun updateImagePreview() {
        val binding = currentSheetBinding
        selectedImageUri?.let { uri ->
            try {
                binding?.ivItemImage?.setImageURI(uri)
                binding?.tvTapToSelect?.text = "Change Image"
            } catch (e: Exception) {
                e.printStackTrace()
            }
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
    // CLEAR BUTTON HELPERS (for vehicle search popup)
    // ==============================

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
            .withEndAction {
                view.visibility = View.GONE
            }
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

    private fun handleClearTouch(view: View, event: android.view.MotionEvent): Boolean {
        when (event.action) {
            android.view.MotionEvent.ACTION_DOWN -> {
                view.animate().scaleX(0.85f).scaleY(0.85f).setDuration(50).start()
            }
            android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL -> {
                view.animate().scaleX(1f).scaleY(1f).setDuration(50).start()
            }
        }
        return false
    }

    private fun setupVehiclePopupClearButton(searchEditText: EditText, clearButton: ImageView) {
        clearButton.alpha = 0f
        clearButton.visibility = View.GONE

        if (!searchEditText.text.isNullOrEmpty()) {
            showClearButtonWithFade(clearButton)
        }

        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (s.isNullOrEmpty()) {
                    hideClearButtonWithFade(clearButton)
                } else {
                    showClearButtonWithFade(clearButton)
                }
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        clearButton.setOnClickListener {
            animateClearTap(clearButton)
            searchEditText.text?.clear()
            val parent = searchEditText.parent as? ViewGroup
            val tvEmptyState = parent?.findViewById<TextView>(R.id.tvEmptyState)
            // This will now sort the vehicles with selected ones on top
            filterVehicles("", tvEmptyState)
            hideClearButtonWithFade(clearButton)
        }

        clearButton.setOnTouchListener { _, event -> handleClearTouch(clearButton, event) }
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
            inputType = InputType.TYPE_NULL
            clearFocus()

            setOnClickListener {
                showVehicleMultiSelectDropdown(binding)
            }
        }

        updateVehicleChips(binding)
    }

    private fun showVehicleMultiSelectDropdown(binding: AddProductSheetBinding) {
        if (allVehicles.isEmpty()) {
            "No vehicles available".toastError(requireContext())
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
        val ivClearSearch = popupView.findViewById<ImageView>(R.id.ivClearVehicleSearch)

        // Wire up the clear ("X") button for vehicle search
        setupVehiclePopupClearButton(searchEditText, ivClearSearch)

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
            updateVehicleChips(binding)
            updateVehicleEditText(binding)
        }

        binding.etVehicles.post {
            val screenWidth = resources.displayMetrics.widthPixels
            val screenHeight = resources.displayMetrics.heightPixels

            val widthSpec = View.MeasureSpec.makeMeasureSpec(
                (screenWidth * 0.92).toInt(),
                View.MeasureSpec.AT_MOST
            )
            val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
            popupView.measure(widthSpec, heightSpec)

            val contentHeight = popupView.measuredHeight + dp(20)

            val location = IntArray(2)
            binding.etVehicles.getLocationOnScreen(location)

            val editTextBottom = location[1] + binding.etVehicles.height
            val spaceBelow = screenHeight - editTextBottom - dp(20)
            val spaceAbove = location[1] - dp(20)

            val maxHeight = (screenHeight * 0.65).toInt()
            val minHeight = dp(500)

            val finalHeight = when {
                contentHeight > spaceBelow && contentHeight > spaceAbove -> {
                    minOf(maxHeight, maxOf(spaceBelow, spaceAbove))
                }
                contentHeight < minHeight -> {
                    minHeight
                }
                else -> {
                    minOf(contentHeight, maxHeight)
                }
            }

            val popupWidth = (screenWidth * 0.92).toInt()
            val xOffset = (binding.etVehicles.width - popupWidth) / 2

            val showAbove = spaceBelow < finalHeight && spaceAbove > finalHeight

            vehicleDropdownPopup = android.widget.PopupWindow(
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

                if (showAbove) {
                    showAsDropDown(binding.etVehicles, xOffset, -finalHeight - binding.etVehicles.height - dp(8))
                } else {
                    showAsDropDown(binding.etVehicles, xOffset, dp(8))
                }

                setOnDismissListener {
                    vehicleDropdownPopup = null
                }
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
            selectedVehicles.removeAll(group)
        } else {
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

        container.addView(
            createTypeChip(
                type = null,
                name = "All",
                iconRes = null,
                container,
                tvSelectedCount,
                tvEmptyState,
                searchEditText
            )
        )

        VehicleType.values().forEach { type ->
            val count = allVehicles.count { it.type == type }
            if (count == 0) return@forEach
            container.addView(
                createTypeChip(
                    type,
                    type.displayName,
                    type.iconRes,
                    container,
                    tvSelectedCount,
                    tvEmptyState,
                    searchEditText
                )
            )
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

        chip.tag = type

        val ivIcon: ImageView = chip.findViewById(R.id.ivTypeIcon)
        val tvName: TextView = chip.findViewById(R.id.tvTypeName)

        if (iconRes != null) {
            ivIcon.setImageResource(iconRes)
            ivIcon.visibility = View.VISIBLE
        } else {
            ivIcon.visibility = View.GONE
        }
        tvName.text = name

        chip.setOnClickListener {
            val isAllChip = type == null
            toggleChip(type)
            vehicleAdapter?.updateSelection(selectedVehicles)
            updateVehicleSelectionUI(tvSelectedCount)
            updateTypeChipSelectionStates(container)

            val currentQuery = searchEditText.text.toString()

            // If "All" chip is selected, show without sorting (original order)
            // If a specific type is selected, sort with selected vehicles on top
            if (isAllChip) {
                // Show all vehicles in original order when "All" is selected
                val filtered = allVehicles.filter { vehicle ->
                    currentQuery.isEmpty() ||
                            vehicle.name.contains(currentQuery, ignoreCase = true) ||
                            (vehicle.company?.contains(currentQuery, ignoreCase = true) == true) ||
                            (vehicle.model?.contains(currentQuery, ignoreCase = true) == true)
                }
                vehicleAdapter?.updateList(filtered)
            } else {
                filterVehicles(currentQuery, tvEmptyState)
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

    private fun filterVehicles(query: String, tvEmptyState: TextView?) {
        // First, filter vehicles based on search query
        val filtered = allVehicles.filter { vehicle ->
            query.isEmpty() ||
                    vehicle.name.contains(query, ignoreCase = true) ||
                    (vehicle.company?.contains(query, ignoreCase = true) == true) ||
                    (vehicle.model?.contains(query, ignoreCase = true) == true)
        }

        // Then, sort the filtered list: selected vehicles first, then unselected
        val sortedFiltered = filtered.sortedWith(compareByDescending<Vehicle> {
            selectedVehicles.contains(it)
        }.thenBy { it.name })

        // Update the adapter with sorted list
        vehicleAdapter?.updateList(sortedFiltered)

        // Show/hide empty state
        tvEmptyState?.visibility = if (sortedFiltered.isEmpty()) View.VISIBLE else View.GONE
    }
    private fun updateVehicleSelectionUI(tvSelectedCount: TextView?) {
        val count = selectedVehicles.size
        tvSelectedCount?.text = "$count vehicle${if (count != 1) "s" else ""} selected"
    }

    private fun updateVehicleEditText(binding: AddProductSheetBinding) {
        val count = selectedVehicles.size
        if (count == 0) {
            binding.etVehicles.setText("")
            binding.etVehicles.hint = "Select Vehicles"
        } else {
            binding.etVehicles.setText("$count vehicle${if (count > 1) "s" else ""} selected")
        }
    }

    // Shows ALL selected vehicles as chips (scrollable row) — no "+N more" truncation
    private fun updateVehicleChips(binding: AddProductSheetBinding) {
        if (selectedVehicles.isEmpty()) {
            binding.selectedVehiclesContainer.visibility = View.GONE
        } else {
            binding.selectedVehiclesContainer.visibility = View.VISIBLE

            val chipsContainer = binding.vehicleChipsContainer
            chipsContainer.removeAllViews()

            selectedVehicles.forEach { vehicle ->
                val chip = createVehicleChip(binding, vehicle)
                chipsContainer.addView(chip)
            }
        }
    }

    private fun createVehicleChip(binding: AddProductSheetBinding, vehicle: Vehicle): View {
        val chip = LayoutInflater.from(requireContext())
            .inflate(R.layout.item_vehicle_chip, binding.vehicleChipsContainer, false) as LinearLayout

        val tvChipName: TextView = chip.findViewById(R.id.tvChipName)
        val ivRemove: ImageView = chip.findViewById(R.id.ivRemoveVehicle)

        tvChipName.text = vehicle.name

        ivRemove.setOnClickListener {
            selectedVehicles.remove(vehicle)
            updateVehicleChips(binding)
            updateVehicleEditText(binding)
        }

        return chip
    }

    // ==============================
    // UPDATE ITEM
    // ==============================

    private fun updateItem(binding: AddProductSheetBinding, sheet: ReusableBottomSheet) {
        val name = binding.etItemName.text.toString().trim()
        val originalPriceStr = binding.etOriginalPrice.text.toString().trim()
        val sellingPriceStr = binding.etSellingPrice.text.toString().trim()
        val stockStr = binding.etStock.text.toString().trim()
        val description = binding.etItemDescription.text.toString().trim()
        val newCategoryName = binding.etCategory.text.toString().trim()

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
            newCategoryName.isEmpty() -> {
                "Please select a category".toastError(requireContext())
                return
            }
        }

        val updatedCostPrice = originalPriceStr.toDouble()
        val updatedSellingPrice = sellingPriceStr.toDouble()
        val updatedStock = stockStr.toInt()

        val category = viewModel.getCategoryByName(newCategoryName)
        if (category == null) {
            "Selected category not found".toastError(requireContext())
            return
        }

        val existingItem = viewModel.getItemById(itemId)
        if (existingItem == null) {
            "Item not found".toastError(requireContext())
            return
        }

        var savedImagePath = existingItem.imageUri
        selectedImageUri?.let { uri ->
            savedImagePath = saveImageToInternalStorage(uri)
            if (savedImagePath == null) {
                "Failed to save image".toastError(requireContext())
                return
            }
        }

        lifecycleScope.launch {
            try {
                val updatedItem = existingItem.copy(
                    name = name,
                    categoryId = category.id,
                    originalPrice = updatedCostPrice,
                    sellingPrice = updatedSellingPrice,
                    stock = updatedStock,
                    description = description.takeIf { it.isNotEmpty() },
                    imageUri = savedImagePath
                )

                viewModel.updateItem(updatedItem)

                if (selectedVehicles.isNotEmpty()) {
                    val vehicleIds = selectedVehicles.map { it.id }
                    viewModel.assignVehiclesToItem(itemId, vehicleIds)
                } else {
                    viewModel.removeAllVehiclesFromItem(itemId)
                }

                updateAssignedVehiclesDisplay()

                itemName = name
                categoryName = newCategoryName
                categoryId = category.id
                costPrice = updatedCostPrice
                sellingPrice = updatedSellingPrice
                currentStock = updatedStock
                this@ItemDetailFragment.description = description
                imageUri = savedImagePath

                tvTitle.text = itemName
                tvCategory.text = categoryName
                tvDescription.text = description
                tvCurrentStock.text = currentStock.toString()
                tvCostPrice.text = "₹${String.format("%.2f", costPrice)}"
                tvSellingPrice.text = "₹${String.format("%.2f", sellingPrice)}"
                updateStockBadge(currentStock)
                loadProductImage()

                sharedViewModel.refreshDashboardData()

                "Item updated successfully!".toastSuccess(requireContext())
                sheet.dismiss()
            } catch (e: Exception) {
                "Failed to update item: ${e.message}".toastError(requireContext())
            }
        }
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