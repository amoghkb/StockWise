package com.example.stockwise.ui.fragment

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Canvas
import android.app.Dialog
import android.graphics.Color
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.inputmethod.InputMethodManager
import android.widget.*
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.DrawableRes
import androidx.appcompat.app.AlertDialog
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.core.widget.doOnTextChanged
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.bitmap.RoundedCorners
import com.example.stockwise.R
import com.example.stockwise.commons.ReusableBottomSheet
import com.example.stockwise.commons.toastError
import com.example.stockwise.commons.toastSuccess
import com.example.stockwise.data.entities.ItemWithCategory
import com.example.stockwise.data.entities.Vehicle
import com.example.stockwise.data.entities.VehicleType
import com.example.stockwise.databinding.AddProductSheetBinding
import com.example.stockwise.databinding.FragmentProductsBinding
import com.example.stockwise.ui.adapter.ProductsAdapter
import com.example.stockwise.ui.adapter.VehicleMultiSelectAdapter
import com.example.stockwise.viewmodels.ProductsViewModel
import com.example.stockwise.viewmodels.SharedDataViewModel
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

@AndroidEntryPoint
class ProductsFragment : Fragment() {

    private var _binding: FragmentProductsBinding? = null
    private val binding get() = _binding!!

    private val viewModel: ProductsViewModel by viewModels()
    private val sharedViewModel: SharedDataViewModel by activityViewModels()

    private lateinit var productsAdapter: ProductsAdapter

    private var isFirstLoad = true

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
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupProductsList()
        setupSearch()
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
        binding.rvProducts.adapter = null
        _binding = null
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).toInt()

    // ==============================
    // PRODUCTS LIST (RecyclerView)
    // ==============================

    private fun setupProductsList() {
        productsAdapter = ProductsAdapter(
            onHeaderClick = { categoryId -> viewModel.toggleCategoryExpansion(categoryId) },
            onItemClick = { itemWithCategory -> navigateToItemDetail(itemWithCategory) }
        )
        binding.rvProducts.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = productsAdapter
            setHasFixedSize(true)
        }

        // Swipe right on a category header → confirm delete
        attachSwipeToDelete()
    }

    // ==============================
    // SWIPE RIGHT TO DELETE CATEGORY
    // ==============================

    private fun attachSwipeToDelete() {
        val callback = object : ItemTouchHelper.SimpleCallback(
            0,                     // no drag
            ItemTouchHelper.RIGHT  // swipe right only
        ) {

            override fun getSwipeDirs(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder
            ): Int {
                // Only category headers are swipeable.
                return if (productsAdapter.isHeaderAt(viewHolder.bindingAdapterPosition)) {
                    super.getSwipeDirs(recyclerView, viewHolder)
                } else {
                    0
                }
            }

            override fun onMove(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                target: RecyclerView.ViewHolder
            ): Boolean = false

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {
                val position = viewHolder.bindingAdapterPosition
                val categoryId = productsAdapter.headerCategoryIdAt(position) ?: return

                // Snap the row back before showing the dialog.
                viewHolder.itemView.translationX = 0f
                productsAdapter.notifyItemChanged(position)

                showDeleteCategoryDialog(categoryId)
            }

            override fun onChildDraw(
                c: Canvas,
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder,
                dX: Float,
                dY: Float,
                actionState: Int,
                isCurrentlyActive: Boolean
            ) {
                val itemView = viewHolder.itemView
                val ctx = itemView.context

                // --- Tune these three values to match your category card ---
                val insetHorizontal = dp(4)   // space on left & right of the red panel
                val insetVertical   = dp(2)   // space on top & bottom of the red panel
                // ------------------------------------------------------------

                val left = itemView.left + insetHorizontal
                val right = (itemView.left + dX.toInt())
                    .coerceAtMost(itemView.right - insetHorizontal)
                val top = itemView.top + insetVertical
                val bottom = itemView.bottom - insetVertical

                if (actionState == ItemTouchHelper.ACTION_STATE_SWIPE && dX > 0 && right > left) {
                    // Red rounded panel behind the row
                    val bg = ContextCompat.getDrawable(ctx, R.drawable.bg_swipe_delete)
                    bg?.setBounds(left, top, right, bottom)
                    bg?.draw(c)

                    // Trash icon centered in the revealed area
                    val icon = ContextCompat.getDrawable(ctx, R.drawable.ic_delete)?.mutate()?.apply {
                        setTint(Color.WHITE)
                    }
                    val iconSize = dp(22)
                    val centerX = (left + right) / 2
                    val centerY = (top + bottom) / 2
                    icon?.setBounds(
                        centerX - iconSize / 2,
                        centerY - iconSize / 2,
                        centerX + iconSize / 2,
                        centerY + iconSize / 2
                    )
                    icon?.draw(c)
                }

                super.onChildDraw(
                    c, recyclerView, viewHolder, dX, dY, actionState, isCurrentlyActive
                )
            }

            override fun clearView(
                recyclerView: RecyclerView,
                viewHolder: RecyclerView.ViewHolder
            ) {
                super.clearView(recyclerView, viewHolder)
                viewHolder.itemView.translationX = 0f
            }

            override fun getSwipeThreshold(viewHolder: RecyclerView.ViewHolder): Float = 0.4f

            override fun getSwipeEscapeVelocity(defaultValue: Float): Float =
                defaultValue * 1.5f
        }

        ItemTouchHelper(callback).attachToRecyclerView(binding.rvProducts)
    }

    // ==============================
    // DELETE CATEGORY DIALOG
    // ==============================

    private fun showDeleteCategoryDialog(categoryId: String) {
        val category = viewModel.categories.value.find { it.id == categoryId } ?: return

        // Guard: block deleting the fallback category.
        if (category.name.equals(ProductsViewModel.DEFAULT_CATEGORY_NAME, ignoreCase = true)) {
            "The Default category cannot be deleted.".toastError(requireContext())
            return
        }

        val itemCount = viewModel.items.value.count { it.item.categoryId == categoryId }
        val hasItems = itemCount > 0

        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_delete_category, null)

        val tvDialogTitle = dialogView.findViewById<TextView>(R.id.tvDialogTitle)
        val tvMessage = dialogView.findViewById<TextView>(R.id.tvDialogMessage)
        val optionsContainer = dialogView.findViewById<LinearLayout>(R.id.optionsContainer)
        val optionMoveContainer = dialogView.findViewById<LinearLayout>(R.id.optionMoveContainer)
        val optionDeleteContainer = dialogView.findViewById<LinearLayout>(R.id.optionDeleteContainer)
        val rbMove = dialogView.findViewById<RadioButton>(R.id.rbMoveItems)
        val rbDelete = dialogView.findViewById<RadioButton>(R.id.rbDeleteItems)
        val tvDeleteTitle = dialogView.findViewById<TextView>(R.id.tvDeleteTitle)
        val btnCancel = dialogView.findViewById<TextView>(R.id.btnCancel)
        val btnDelete = dialogView.findViewById<TextView>(R.id.btnDeleteCategory)

        // Explicit text — never rely on the XML default
        tvDialogTitle.text = "Delete Category"
        btnDelete.text = "Delete Category"

        if (hasItems) {
            tvMessage.text =
                "Are you sure you want to delete ${category.name}? ($itemCount item${if (itemCount != 1) "s" else ""})"
            tvDeleteTitle.text =
                "Delete category and all $itemCount item${if (itemCount != 1) "s" else ""}"

            fun applySelectedState(moveSelected: Boolean) {
                rbMove.isChecked = moveSelected
                rbDelete.isChecked = !moveSelected

                optionMoveContainer.background = ContextCompat.getDrawable(
                    requireContext(),
                    if (moveSelected) R.drawable.bg_option_selected else R.drawable.bg_option_unselected
                )
                optionDeleteContainer.background = ContextCompat.getDrawable(
                    requireContext(),
                    if (moveSelected) R.drawable.bg_option_unselected else R.drawable.bg_option_selected
                )
            }

            applySelectedState(moveSelected = true)
            optionMoveContainer.setOnClickListener { applySelectedState(true) }
            optionDeleteContainer.setOnClickListener { applySelectedState(false) }
        } else {
            tvMessage.text =
                "Are you sure you want to delete ${category.name}?\n\nThis cannot be undone."
            optionsContainer.visibility = View.GONE
        }

        val dialog = android.app.Dialog(requireContext())
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        dialog.setContentView(dialogView)
        dialog.setCancelable(true)
        dialog.setCanceledOnTouchOutside(true)

        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            val width = (resources.displayMetrics.widthPixels * 0.92).toInt()
            setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        btnCancel.setOnClickListener { dialog.dismiss() }

        btnDelete.setOnClickListener {
            if (!hasItems) {
                viewModel.deleteCategoryAndItems(categoryId)
            } else if (rbMove.isChecked) {
                viewModel.deleteCategoryAndMoveItems(categoryId)
            } else {
                viewModel.deleteCategoryAndItems(categoryId)
            }
            dialog.dismiss()
        }

        dialog.show()
    }
    // ==============================
    // MAIN SEARCH WITH CLEAR BUTTON
    // ==============================

    private fun setupSearch() {
        val etSearch = binding.etSearchProducts
        val ivClear = binding.ivClearSearch

        ivClear.alpha = 0f
        ivClear.visibility = View.GONE

        val currentQuery = viewModel.searchQuery.value
        if (currentQuery.isNotEmpty()) {
            etSearch.setText(currentQuery)
            showClearButtonWithFade(ivClear)
        }

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (s.isNullOrEmpty()) {
                    hideClearButtonWithFade(ivClear)
                } else {
                    showClearButtonWithFade(ivClear)
                }
            }

            override fun afterTextChanged(s: Editable?) {
                viewModel.onSearchQueryChanged(s?.toString().orEmpty())
            }
        })

        ivClear.setOnClickListener {
            animateClearTap(ivClear)
            etSearch.text?.clear()
            viewModel.onSearchQueryChanged("")
            hideClearButtonWithFade(ivClear)
            hideKeyboard()
        }

        ivClear.setOnTouchListener { _, event -> handleClearTouch(ivClear, event) }
    }

    // ==============================
    // SHARED CLEAR BUTTON HELPERS
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
            .withEndAction { view.visibility = View.GONE }
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
            android.view.MotionEvent.ACTION_DOWN ->
                view.animate().scaleX(0.85f).scaleY(0.85f).setDuration(50).start()
            android.view.MotionEvent.ACTION_UP, android.view.MotionEvent.ACTION_CANCEL ->
                view.animate().scaleX(1f).scaleY(1f).setDuration(50).start()
        }
        return false
    }

    private fun hideKeyboard() {
        val imm = requireContext().getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.root.windowToken, 0)
    }

    private fun hideKeyboard(binding: AddProductSheetBinding) {
        val imm = requireContext().getSystemService(Activity.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(binding.root.windowToken, 0)
    }

    // ==============================
    // POPUP CLEAR BUTTON HELPERS
    // ==============================

    private fun setupVehiclePopupClearButton(searchEditText: EditText, clearButton: ImageView) {
        clearButton.alpha = 0f
        clearButton.visibility = View.GONE

        if (!searchEditText.text.isNullOrEmpty()) {
            showClearButtonWithFade(clearButton)
        }

        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (s.isNullOrEmpty()) hideClearButtonWithFade(clearButton)
                else showClearButtonWithFade(clearButton)
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        clearButton.setOnClickListener {
            animateClearTap(clearButton)
            searchEditText.text?.clear()
            val parent = searchEditText.parent as? ViewGroup
            val tvEmptyState = parent?.findViewById<TextView>(R.id.tvEmptyState)
            filterVehicles("", tvEmptyState)
            hideClearButtonWithFade(clearButton)
        }
        clearButton.setOnTouchListener { _, event -> handleClearTouch(clearButton, event) }
    }

    private fun setupCategoryPopupClearButton(searchEditText: EditText, clearButton: ImageView) {
        clearButton.alpha = 0f
        clearButton.visibility = View.GONE

        if (!searchEditText.text.isNullOrEmpty()) {
            showClearButtonWithFade(clearButton)
        }

        searchEditText.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}

            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                if (s.isNullOrEmpty()) hideClearButtonWithFade(clearButton)
                else showClearButtonWithFade(clearButton)
            }

            override fun afterTextChanged(s: Editable?) {}
        })

        clearButton.setOnClickListener {
            animateClearTap(clearButton)
            searchEditText.text?.clear()
            val parent = searchEditText.parent as? ViewGroup
            val listView = parent?.findViewById<ListView>(R.id.lvCategories)
            val adapter = listView?.adapter as? ArrayAdapter<String>
            adapter?.let { filterBottomSheetCategories("", it) }
            hideClearButtonWithFade(clearButton)
        }

        clearButton.setOnTouchListener { _, event -> handleClearTouch(clearButton, event) }
    }

    // ==============================
    // VIEW MODEL OBSERVERS
    // ==============================

    private fun observeViewModel() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.displayItems.collect { list ->
                productsAdapter.submitList(list)
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
                    loadVehiclesForDropdown()
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.categoryDeleteSuccess.collect { success ->
                if (success) {
                    "Category deleted".toastSuccess(requireContext())
                    viewModel.clearCategoryDeleteSuccess()
                    sharedViewModel.refreshDashboardData()
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
            viewModel.isLoading.collect { _ ->
                // Show/hide loading indicator
            }
        }
    }

    private fun setupClickListeners() {
        binding.fabAddProduct.setOnClickListener {
            openAddProductSheet()
        }
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
    // IMAGE PICKER FUNCTIONS
    // ==============================

    private fun openImagePicker() {
        val dialogView = LayoutInflater.from(requireContext())
            .inflate(R.layout.dialog_choice, null)

        val tvDialogTitle = dialogView.findViewById<TextView>(R.id.tvDialogTitle)
        val tvMessage = dialogView.findViewById<TextView>(R.id.tvDialogMessage)
        val optionCamera = dialogView.findViewById<LinearLayout>(R.id.optionCamera)
        val optionGallery = dialogView.findViewById<LinearLayout>(R.id.optionGallery)
        val btnCancel = dialogView.findViewById<TextView>(R.id.btnCancel)

        tvDialogTitle.text = "Select Image"
        tvMessage.text = "Choose a source for your photo."

        val dialog = Dialog(requireContext())
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE)
        dialog.setContentView(dialogView)
        dialog.setCancelable(true)
        dialog.setCanceledOnTouchOutside(true)

        dialog.window?.apply {
            setBackgroundDrawableResource(android.R.color.transparent)
            val width = (resources.displayMetrics.widthPixels * 0.92).toInt()
            setLayout(width, ViewGroup.LayoutParams.WRAP_CONTENT)
        }

        btnCancel.setOnClickListener { dialog.dismiss() }

        optionCamera.setOnClickListener {
            dialog.dismiss()
            openCamera()
        }

        optionGallery.setOnClickListener {
            dialog.dismiss()
            openGallery()
        }

        dialog.show()
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
                ?: return null
            if (!storageDir.exists()) storageDir.mkdirs()
            return File.createTempFile(imageFileName, ".jpg", storageDir)
        } catch (e: Exception) {
            e.printStackTrace()
            return null
        }
    }

    private fun updateImagePreview() {
        currentSheetBinding?.let { binding ->
            selectedImageUri?.let { uri ->
                try {
                    Glide.with(requireContext())
                        .load(uri)
                        .transform(RoundedCorners(8))
                        .centerCrop()
                        .placeholder(R.drawable.ic_image)
                        .error(R.drawable.ic_image)
                        .into(binding.ivItemImage)

                    binding.tvTapToSelect.text = "Change Image"
                    binding.ivDeleteImage.visibility = View.VISIBLE
                    binding.ivDeleteImage.setOnClickListener { deleteSelectedImage() }
                } catch (e: Exception) {
                    e.printStackTrace()
                    "Failed to load image".toastError(requireContext())
                }
            }
        }
    }

    private fun deleteSelectedImage() {
        currentSheetBinding?.let { binding ->
            selectedImageUri = null
            binding.ivItemImage.setImageResource(R.drawable.ic_image)
            binding.tvTapToSelect.text = "Tap to select image"
            binding.ivDeleteImage.visibility = View.GONE
            "Image removed".toastSuccess(requireContext())
        }
    }

    private fun saveImageToInternalStorage(uri: Uri): String? {
        try {
            val context = requireContext()
            val inputStream: InputStream = context.contentResolver.openInputStream(uri) ?: return null
            val fileName = "item_${System.currentTimeMillis()}.jpg"
            val dir = File(context.filesDir, "images")
            if (!dir.exists()) dir.mkdirs()
            val outputFile = File(dir, fileName)
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
    // BOTTOM SHEET
    // ==============================

    private fun openAddProductSheet() {
        selectedImageUri = null
        selectedVehicleType = null
        selectedVehicles.clear()
        val sheet = ReusableBottomSheet.newInstance(layoutRes = R.layout.add_product_sheet)

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
            sheetBinding.ivDeleteImage.visibility = View.GONE
            sheetBinding.ivDeleteImage.setOnClickListener(null)

            loadVehiclesForDropdown()
        }

        sheet.show(parentFragmentManager, "AddProductSheet")
    }

    private fun loadVehiclesForDropdown() {
        lifecycleScope.launch {
            try {
                allVehicles.clear()
                allVehicles.addAll(viewModel.getAllVehicles())
                filteredVehicles.clear()
                filteredVehicles.addAll(allVehicles)
                updateVehicleChips()
                updateVehicleEditText()
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
            showMenu(binding); clearCategoryForm(binding)
        }
        binding.btnBackToMenuFromVehicle.setOnClickListener {
            showMenu(binding); clearVehicleForm(binding)
        }

        binding.btnCategoryCancel.setOnClickListener { dismissSheet(binding, sheet) }
        binding.btnSaveCategory.setOnClickListener { saveCategory(binding, sheet) }

        binding.btnVehicleCancel.setOnClickListener { dismissSheet(binding, sheet) }
        binding.btnSaveVehicle.setOnClickListener { saveVehicle(binding, sheet) }

        binding.btnBackToMenuFromItem.setOnClickListener {
            showMenu(binding); clearItemForm(binding)
        }

        binding.btnItemCancel.setOnClickListener { dismissSheet(binding, sheet) }
        binding.btnSaveItem.setOnClickListener { saveItem(binding, sheet) }

        binding.btnSelectImage.setOnClickListener { checkPermissionAndOpenPicker() }
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
        binding.ivDeleteImage.visibility = View.GONE
        binding.ivDeleteImage.setOnClickListener(null)
        selectedImageUri = null
        selectedVehicles.clear()
        updateVehicleChips()
        updateVehicleEditText()
        bottomSheetDropdownPopup?.dismiss()
        vehicleDropdownPopup?.dismiss()
    }

    // ==============================
    // VEHICLE MULTI-SELECT DROPDOWN
    // ==============================

    private fun setupVehicleMultiSelectDropdown(binding: AddProductSheetBinding) {
        binding.etVehicles.apply {
            isFocusable = false
            isFocusableInTouchMode = false
            isClickable = true
            isCursorVisible = false
            inputType = android.text.InputType.TYPE_NULL
            clearFocus()
            setOnClickListener { showVehicleMultiSelectDropdown(binding) }
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
        val ivClearSearch = popupView.findViewById<ImageView>(R.id.ivClearVehicleSearch)

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
            updateVehicleChips()
            updateVehicleEditText()
        }

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
        container.addView(createTypeChip(null, "All", null, container, tvSelectedCount, tvEmptyState, searchEditText))

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

            if (isAllChip) {
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
        val filtered = allVehicles.filter { vehicle ->
            query.isEmpty() ||
                    vehicle.name.contains(query, ignoreCase = true) ||
                    (vehicle.company?.contains(query, ignoreCase = true) == true) ||
                    (vehicle.model?.contains(query, ignoreCase = true) == true)
        }

        val sortedFiltered = filtered.sortedWith(compareByDescending<Vehicle> {
            selectedVehicles.contains(it)
        }.thenBy { it.name })

        vehicleAdapter?.updateList(sortedFiltered)
        tvEmptyState?.visibility = if (sortedFiltered.isEmpty()) View.VISIBLE else View.GONE
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
                selectedVehicles.forEach { vehicle ->
                    chipsContainer.addView(createVehicleChip(vehicle))
                }
            }
        }
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
            setOnClickListener { showBottomSheetDropdown(binding) }
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
        val ivClearSearch = popupView.findViewById<ImageView>(R.id.ivClearCategorySearch)

        setupCategoryPopupClearButton(bottomSheetSearchEditText!!, ivClearSearch)

        bottomSheetFilteredCategories = categories.toMutableList()
        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, bottomSheetFilteredCategories)
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
                location[0] = 0; location[1] = 0
            }

            val spaceBelow = screenHeight - (location[1] + binding.etCategory.height) - dp(20)
            val maxAvailableHeight = (spaceBelow * 0.95).toInt()
            val maxScreenHeight = (screenHeight * 0.6).toInt()
            val finalHeight = minOf(maxAvailableHeight, maxScreenHeight).coerceAtLeast(dp(250))
            val popupWidth = (screenWidth * 0.9).toInt()

            bottomSheetDropdownPopup = PopupWindow(popupView, popupWidth, finalHeight, true).apply {
                isFocusable = true
                isOutsideTouchable = true
                setBackgroundDrawable(ContextCompat.getDrawable(requireContext(), R.drawable.rounded_dropdown_bg))
                elevation = dp(8).toFloat()
                val offsetX = (binding.etCategory.width - popupWidth) / 2
                showAsDropDown(binding.etCategory, offsetX, dp(8))
                setOnDismissListener { bottomSheetDropdownPopup = null }
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
            setOnClickListener { showVehicleTypeDropdown(binding) }
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

        val adapter = ArrayAdapter(requireContext(), android.R.layout.simple_list_item_1, typeNames)
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
                location[0] = 0; location[1] = 0
            }

            val spaceBelow = screenHeight - (location[1] + binding.etVehicleType.height) - dp(20)
            val maxAvailableHeight = (spaceBelow * 0.95).toInt()
            val maxScreenHeight = (screenHeight * 0.6).toInt()
            val finalHeight = minOf(maxAvailableHeight, maxScreenHeight).coerceAtLeast(dp(200))
            val popupWidth = (screenWidth * 0.9).toInt()

            vehicleTypePopup = PopupWindow(popupView, popupWidth, finalHeight, true).apply {
                isFocusable = true
                isOutsideTouchable = true
                setBackgroundDrawable(ContextCompat.getDrawable(requireContext(), R.drawable.rounded_dropdown_bg))
                elevation = dp(8).toFloat()
                val offsetX = (binding.etVehicleType.width - popupWidth) / 2
                showAsDropDown(binding.etVehicleType, offsetX, dp(8))
                setOnDismissListener { vehicleTypePopup = null }
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
            name.isEmpty() -> { "Please enter a category name".toastError(requireContext()); return }
            name.length < 2 -> { "Category name must be at least 2 characters".toastError(requireContext()); return }
            name.length > 50 -> { "Category name must be less than 50 characters".toastError(requireContext()); return }
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
            name.isEmpty() -> { "Please enter a vehicle name".toastError(requireContext()); return }
            name.length < 2 -> { "Vehicle name must be at least 2 characters".toastError(requireContext()); return }
            name.length > 100 -> { "Vehicle name must be less than 100 characters".toastError(requireContext()); return }
            type == null -> { "Please select a vehicle type".toastError(requireContext()); return }
        }

        viewModel.saveVehicle(name = name, company = company, type = type, model = model, description = description)
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
                    viewModel.assignVehiclesToItem(itemId, selectedVehicles.map { it.id })
                }

                sharedViewModel.refreshDashboardData()

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
            categoryName.isEmpty() -> { "Please select a category".toastError(requireContext()); return false }
            name.isEmpty() -> { "Please enter an item name".toastError(requireContext()); return false }
            name.length < 2 -> { "Item name must be at least 2 characters".toastError(requireContext()); return false }
            name.length > 100 -> { "Item name must be less than 100 characters".toastError(requireContext()); return false }
            originalPriceStr.isEmpty() -> { "Please enter the original price".toastError(requireContext()); return false }
            originalPriceStr.toDoubleOrNull() == null -> { "Please enter a valid original price".toastError(requireContext()); return false }
            originalPriceStr.toDoubleOrNull()!! < 0 -> { "Original price cannot be negative".toastError(requireContext()); return false }
            sellingPriceStr.isEmpty() -> { "Please enter the selling price".toastError(requireContext()); return false }
            sellingPriceStr.toDoubleOrNull() == null -> { "Please enter a valid selling price".toastError(requireContext()); return false }
            sellingPriceStr.toDoubleOrNull()!! < 0 -> { "Selling price cannot be negative".toastError(requireContext()); return false }
            stockStr.isEmpty() -> { "Please enter the stock quantity".toastError(requireContext()); return false }
            stockStr.toIntOrNull() == null -> { "Please enter a valid stock quantity".toastError(requireContext()); return false }
            stockStr.toIntOrNull()!! < 0 -> { "Stock quantity cannot be negative".toastError(requireContext()); return false }
        }
        return true
    }
}