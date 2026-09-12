package com.example.stockwise.ui.fragment

import android.animation.ObjectAnimator
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.TextWatcher
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.AccelerateDecelerateInterpolator
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.widget.AppCompatButton
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.example.stockwise.R
import com.example.stockwise.commons.toastError
import com.example.stockwise.commons.toastSuccess
import com.example.stockwise.data.entities.DailySalesSummary
import com.example.stockwise.data.entities.Item
import com.example.stockwise.databinding.FragmentDashboardBinding
import com.example.stockwise.ui.adapter.CartAdapter
import com.example.stockwise.ui.adapter.CartSearchAdapter
import com.example.stockwise.ui.adapter.LowStockAdapter
import com.example.stockwise.ui.model.CartItem
import com.example.stockwise.viewmodels.SharedDataViewModel
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.DecimalFormat

@AndroidEntryPoint
class DashboardFragment : Fragment() {

    // ============================================================
    // BINDING & VIEWMODEL
    // ============================================================

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    private val sharedViewModel: SharedDataViewModel by activityViewModels()
    private lateinit var lowStockAdapter: LowStockAdapter

    // ============================================================
    // SYNC STATE
    // ============================================================

    private var isSyncing = false
    private var rotateAnimator: ObjectAnimator? = null
    private val SYNC_DURATION = 1000L
    private val handler = Handler(Looper.getMainLooper())
    private var isFirstLoad = true

    // ============================================================
    // CART STATE
    // ============================================================

    private var cartBottomSheet: BottomSheetDialog? = null
    private val cartItems = mutableListOf<CartItem>()
    private var cartAdapter: CartAdapter? = null
    private var cartSearchAdapter: CartSearchAdapter? = null
    private val allItemsForCart = mutableListOf<Item>()

    // ============================================================
    // LIFECYCLE
    // ============================================================

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentDashboardBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupRecyclerView()
        setupClickListeners()
        observeData()

        showSkeleton(true)
        sharedViewModel.refreshDashboardData()
    }

    override fun onResume() {
        super.onResume()
        if (!isFirstLoad) {
            sharedViewModel.refreshDashboardData()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        handler.removeCallbacksAndMessages(null)
        rotateAnimator?.cancel()
        rotateAnimator = null

        cartBottomSheet?.dismiss()
        cartBottomSheet = null
        cartAdapter = null
        cartSearchAdapter = null
        cartItems.clear()

        _binding = null
    }

    // ============================================================
    // SETUP
    // ============================================================

    private fun setupRecyclerView() {
        lowStockAdapter = LowStockAdapter(
            getStockProgress = { item ->
                sharedViewModel.getStockProgress(item)
            }
        )

        binding.rvLowStock.apply {
            layoutManager = LinearLayoutManager(requireContext())
            adapter = lowStockAdapter
            setHasFixedSize(true)
        }
    }

    private fun setupClickListeners() {
        binding.btnSync.setOnClickListener {
            if (!isSyncing) {
                performSync()
            }
        }

        binding.fabAddItem.setOnClickListener {
            openCartSaleBottomSheet()
        }
    }

    // ============================================================
    // SYNC ANIMATION
    // ============================================================

    private fun performSync() {
        isSyncing = true
        binding.btnSync.isEnabled = false
        binding.btnSync.alpha = 0.7f
        startSyncAnimation()

        showSkeleton(true)
        sharedViewModel.refreshDashboardData()

        handler.postDelayed({
            stopSync()
        }, SYNC_DURATION)
    }

    private fun startSyncAnimation() {
        rotateAnimator = ObjectAnimator.ofFloat(binding.btnSync, "rotation", 0f, 360f).apply {
            duration = 1000
            repeatCount = ObjectAnimator.INFINITE
            start()
        }
    }

    private fun stopSync() {
        rotateAnimator?.cancel()
        rotateAnimator = null
        binding.btnSync.rotation = 0f
        binding.btnSync.isEnabled = true
        binding.btnSync.alpha = 1.0f
        isSyncing = false
    }

    private fun showSkeleton(show: Boolean) {
        if (show) {
            binding.shimmerFullPage.visibility = View.VISIBLE
            binding.shimmerFullPage.startShimmer()
            binding.scrollMainContent.visibility = View.GONE
        } else {
            binding.shimmerFullPage.visibility = View.GONE
            binding.shimmerFullPage.stopShimmer()
            binding.scrollMainContent.visibility = View.VISIBLE
            isFirstLoad = false
        }
    }

    // ============================================================
    // OBSERVERS
    // ============================================================

    private fun observeData() {
        viewLifecycleOwner.lifecycleScope.launch {
            sharedViewModel.greeting.collect { greeting ->
                binding.tvGreeting.text = greeting
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            sharedViewModel.currentDate.collect { date ->
                binding.tvDate.text = date
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            sharedViewModel.todaySales.collect { sales ->
                binding.tvTodaySales.text = sales
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            sharedViewModel.stockAlertsCount.collect { count ->
                binding.tvStockAlerts.text = count
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            sharedViewModel.totalItems.collect { total ->
                binding.tvTotalItems.text = total
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            sharedViewModel.lowStockItems.collect { items ->
                lowStockAdapter.submitList(items)
                binding.tvLowStockCount.text = "${items.size} items"

                if (items.isEmpty()) {
                    binding.llEmptyLowStock.visibility = View.VISIBLE
                    binding.rvLowStock.visibility = View.GONE
                } else {
                    binding.llEmptyLowStock.visibility = View.GONE
                    binding.rvLowStock.visibility = View.VISIBLE
                }

                if (isSyncing) stopSync()
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            sharedViewModel.weeklySalesData.collect { salesData ->
                populateWeeklyBarChart(salesData)
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            sharedViewModel.isLoading.collect { isLoading ->
                if (!isLoading) {
                    showSkeleton(false)
                    if (isSyncing) stopSync()
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            sharedViewModel.error.collect { error ->
                error?.let {
                    if (isSyncing) stopSync()
                    showSkeleton(false)
                }
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            sharedViewModel.saleSuccessEvent.collect { message ->
                message?.let {
                    it.toastSuccess(requireContext())
                    sharedViewModel.clearSaleSuccessEvent()
                }
            }
        }
    }

    // ============================================================
    // WEEKLY BAR CHART
    // ============================================================

    private fun populateWeeklyBarChart(salesData: List<DailySalesSummary>) {
        val barChart = binding.chartWeeklySales

        if (salesData.isEmpty() || salesData.all { it.totalAmount == 0.0 }) {
            barChart.clear()
            barChart.setNoDataText("No sales data for the week")
            barChart.setNoDataTextColor(resources.getColor(R.color.text_secondary, null))
            barChart.invalidate()
            return
        }

        val entries = salesData.mapIndexed { index, day ->
            BarEntry(index.toFloat(), day.totalAmount.toFloat())
        }

        val dataSet = BarDataSet(entries, "Daily Sales").apply {
            color = resources.getColor(R.color.chart_high, null)
            valueTextColor = resources.getColor(R.color.text_secondary, null)
            valueTextSize = 10f
            setDrawValues(true)
            valueFormatter = object : ValueFormatter() {
                private val format = DecimalFormat("₹#,##0")
                override fun getFormattedValue(value: Float): String {
                    return if (value > 0) format.format(value.toDouble()) else ""
                }
            }
        }

        val days = salesData.map { it.date ?: "" }

        val barData = BarData(dataSet).apply {
            barWidth = 0.6f
        }

        barChart.apply {
            this.data = barData
            description.isEnabled = false
            setFitBars(true)
            animateY(800)
            setDrawGridBackground(false)
            setDrawBarShadow(false)
            setPinchZoom(false)
            setScaleEnabled(false)
            setDoubleTapToZoomEnabled(false)

            xAxis.apply {
                valueFormatter = IndexAxisValueFormatter(days)
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                setDrawAxisLine(false)
                setDrawLabels(true)
                textSize = 11f
                textColor = resources.getColor(R.color.text_secondary, null)
            }

            axisLeft.apply {
                setDrawGridLines(true)
                setDrawAxisLine(false)
                axisMinimum = 0f
                setLabelCount(5, true)
                textSize = 10f
                textColor = resources.getColor(R.color.text_secondary, null)
                valueFormatter = object : ValueFormatter() {
                    private val format = DecimalFormat("₹#,##0")
                    override fun getFormattedValue(value: Float): String {
                        return if (value > 0) format.format(value.toDouble()) else ""
                    }
                }
            }

            axisRight.isEnabled = false
            legend.isEnabled = false
            invalidate()
        }
    }

    // ============================================================
    // CART SALE BOTTOM SHEET
    // ============================================================

    private fun openCartSaleBottomSheet() {
        cartItems.clear()
        allItemsForCart.clear()

        val dialog = BottomSheetDialog(requireContext(), R.style.BottomSheetDialogTheme)
        val view = LayoutInflater.from(requireContext())
            .inflate(R.layout.bottom_sheet_cart_sale, null)

        dialog.setContentView(view)

        dialog.show()
        cartBottomSheet = dialog

        dialog.window?.let { window ->
            WindowCompat.setDecorFitsSystemWindows(window, false)
            window.setLayout(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            window.statusBarColor = android.graphics.Color.TRANSPARENT
        }

        val screenHeight = resources.displayMetrics.heightPixels

        val bottomSheet = dialog.findViewById<FrameLayout>(
            com.google.android.material.R.id.design_bottom_sheet
        )
        bottomSheet?.let { sheet ->
            sheet.layoutParams = sheet.layoutParams.apply {
                height = ViewGroup.LayoutParams.MATCH_PARENT
            }
            sheet.requestLayout()
        }

        ViewCompat.setOnApplyWindowInsetsListener(view) { v, insets ->
            val statusBarHeight = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            v.setPadding(v.paddingLeft, statusBarHeight, v.paddingRight, v.paddingBottom)
            insets
        }
        dialog.behavior.apply {
            peekHeight = screenHeight
            isDraggable = true
            isHideable = true
            skipCollapsed = true
            state = BottomSheetBehavior.STATE_EXPANDED
        }

        // ============================================================
        // BIND VIEWS
        // ============================================================
        val etSearch = view.findViewById<EditText>(R.id.etCartSearch)
        val ivClearSearch = view.findViewById<ImageView>(R.id.ivCartClearSearch)
        val flSearchResultsContainer = view.findViewById<FrameLayout>(R.id.flSearchResultsContainer)
        val rvSearchResults = view.findViewById<RecyclerView>(R.id.rvCartSearchResults)
        val llNoSearchResults = view.findViewById<LinearLayout>(R.id.llNoSearchResults)
        val tvNoResultsQuery = view.findViewById<TextView>(R.id.tvNoResultsQuery)
        val flCartContainer = view.findViewById<FrameLayout>(R.id.flCartContainer)
        val rvCartItems = view.findViewById<RecyclerView>(R.id.rvCartItems)
        val llEmptyCart = view.findViewById<LinearLayout>(R.id.llEmptyCart)
        val llSummary = view.findViewById<LinearLayout>(R.id.llCartSummary)
        val tvTotal = view.findViewById<TextView>(R.id.tvCartTotal)
        val tvTotalCost = view.findViewById<TextView>(R.id.tvCartTotalCost)
        val tvProfit = view.findViewById<TextView>(R.id.tvCartProfit)
        val tvItemCount = view.findViewById<TextView>(R.id.tvCartItemCount)
        val btnCompleteSale = view.findViewById<AppCompatButton>(R.id.btnCompleteSale)

        // ===== Search results adapter =====
        cartSearchAdapter = CartSearchAdapter { item ->
            addItemToCart(
                item = item,
                llEmptyCart = llEmptyCart,
                llSummary = llSummary,
                tvTotal = tvTotal,
                tvTotalCost = tvTotalCost,
                tvProfit = tvProfit,
                tvItemCount = tvItemCount
            )
            etSearch.text?.clear()
            etSearch.clearFocus()
            hideKeyboard(etSearch)
            flSearchResultsContainer.visibility = View.GONE
            flCartContainer.visibility = View.VISIBLE
        }
        rvSearchResults.layoutManager = LinearLayoutManager(requireContext())
        rvSearchResults.adapter = cartSearchAdapter

        // ===== Cart adapter =====
        // FIX: replace items with .copy(...) instead of mutating in place,
        // so DiffUtil can detect the change and rebind the row (updating
        // tvCartQuantity immediately).
        cartAdapter = CartAdapter(
            onQuantityChanged = { cartItem, newQty ->
                val index = cartItems.indexOfFirst { it.item.id == cartItem.item.id }
                if (index >= 0) {
                    cartItems[index] = cartItems[index].copy(quantity = newQty)
                }
            },
            onPriceChanged = { cartItem, newPrice ->
                val index = cartItems.indexOfFirst { it.item.id == cartItem.item.id }
                if (index >= 0) {
                    cartItems[index] = cartItems[index].copy(sellingPrice = newPrice)
                }
            },
            onRemove = { cartItem ->
                cartItems.removeAll { it.item.id == cartItem.item.id }
                refreshCartUI(llEmptyCart, llSummary, tvTotal, tvTotalCost, tvProfit, tvItemCount)
            },
            onCartUpdated = {
                refreshCartUI(llEmptyCart, llSummary, tvTotal, tvTotalCost, tvProfit, tvItemCount)
            }
        )
        rvCartItems.layoutManager = LinearLayoutManager(requireContext())
        rvCartItems.adapter = cartAdapter

        refreshCartUI(llEmptyCart, llSummary, tvTotal, tvTotalCost, tvProfit, tvItemCount)

        // ===== Search bar with animated clear button =====
        ivClearSearch.alpha = 0f
        ivClearSearch.visibility = View.GONE

        etSearch.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                val query = s?.toString()?.trim().orEmpty()

                if (query.isEmpty()) {
                    hideClearButtonWithFade(ivClearSearch)
                    flSearchResultsContainer.visibility = View.GONE
                    flCartContainer.visibility = View.VISIBLE
                } else {
                    showClearButtonWithFade(ivClearSearch)
                    flCartContainer.visibility = View.GONE
                    flSearchResultsContainer.visibility = View.VISIBLE
                    performCartSearch(query, rvSearchResults, llNoSearchResults, tvNoResultsQuery)
                }
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        ivClearSearch.setOnClickListener {
            animateClearTap(ivClearSearch)
            etSearch.text?.clear()
            etSearch.clearFocus()
            hideKeyboard(etSearch)
            flSearchResultsContainer.visibility = View.GONE
            flCartContainer.visibility = View.VISIBLE
            llNoSearchResults.visibility = View.GONE
            hideClearButtonWithFade(ivClearSearch)
        }

        btnCompleteSale.setOnClickListener {
            completeCartSale(dialog)
        }

        dialog.setOnDismissListener {
            cartBottomSheet = null
            cartAdapter = null
            cartSearchAdapter = null
            cartItems.clear()
        }

        loadAllItemsForCart()
    }

    private fun loadAllItemsForCart() {
        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val items = sharedViewModel.getAllItemsOnce()
                allItemsForCart.clear()
                allItemsForCart.addAll(items.filter { it.stock > 0 })
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    private fun performCartSearch(
        query: String,
        rvSearchResults: RecyclerView,
        llNoSearchResults: LinearLayout,
        tvNoResultsQuery: TextView
    ) {
        val filtered = allItemsForCart.filter { item ->
            item.name.contains(query, ignoreCase = true) ||
                    item.id.take(8).contains(query, ignoreCase = true)
        }

        cartSearchAdapter?.submitList(filtered)

        if (filtered.isEmpty()) {
            rvSearchResults.visibility = View.GONE
            llNoSearchResults.visibility = View.VISIBLE
            tvNoResultsQuery.text = "No products found for \"$query\""
        } else {
            rvSearchResults.visibility = View.VISIBLE
            llNoSearchResults.visibility = View.GONE
        }
    }

    /**
     * FIX: replace the item in `cartItems` with .copy(...) when bumping
     * quantity, instead of mutating the existing CartItem in place. Combined
     * with the new DiffUtil in CartAdapter, this makes the list-diff see
     * the change and rebind the affected row.
     */
    private fun addItemToCart(
        item: Item,
        llEmptyCart: LinearLayout,
        llSummary: LinearLayout,
        tvTotal: TextView,
        tvTotalCost: TextView,
        tvProfit: TextView,
        tvItemCount: TextView
    ) {
        val index = cartItems.indexOfFirst { it.item.id == item.id }

        if (index >= 0) {
            val existing = cartItems[index]
            if (existing.quantity < item.stock) {
                cartItems[index] = existing.copy(quantity = existing.quantity + 1)
            } else {
                "Already at max stock for ${item.name}".toastError(requireContext())
                return
            }
        } else {
            cartItems.add(
                CartItem(
                    item = item,
                    quantity = 1,
                    sellingPrice = item.sellingPrice
                )
            )
        }

        refreshCartUI(llEmptyCart, llSummary, tvTotal, tvTotalCost, tvProfit, tvItemCount)
    }

    private fun refreshCartUI(
        llEmptyCart: LinearLayout,
        llSummary: LinearLayout,
        tvTotal: TextView,
        tvTotalCost: TextView,
        tvProfit: TextView,
        tvItemCount: TextView
    ) {
        cartAdapter?.submitList(cartItems.toList())

        if (cartItems.isEmpty()) {
            llEmptyCart.visibility = View.VISIBLE
            llSummary.visibility = View.GONE
            tvItemCount.text = "0 items"
        } else {
            llEmptyCart.visibility = View.GONE
            llSummary.visibility = View.VISIBLE

            val total = cartItems.sumOf { it.totalPrice }
            val cost = cartItems.sumOf { it.totalCost }
            val profit = total - cost

            tvTotal.text = "₹${String.format("%.0f", total)}"
            tvTotalCost.text = "Cost: ₹${String.format("%.0f", cost)}"
            tvProfit.text = "Profit: ₹${String.format("%.0f", profit)}"

            val count = cartItems.sumOf { it.quantity }
            tvItemCount.text = "$count item${if (count != 1) "s" else ""}"
        }
    }

    private fun completeCartSale(dialog: BottomSheetDialog) {
        if (cartItems.isEmpty()) {
            "Cart is empty".toastError(requireContext())
            return
        }

        for (cartItem in cartItems) {
            if (cartItem.quantity <= 0) {
                "Invalid quantity for ${cartItem.item.name}".toastError(requireContext())
                return
            }
            if (cartItem.quantity > cartItem.item.stock) {
                "Not enough stock for ${cartItem.item.name}".toastError(requireContext())
                return
            }
            if (cartItem.sellingPrice <= 0) {
                "Invalid price for ${cartItem.item.name}".toastError(requireContext())
                return
            }
        }

        viewLifecycleOwner.lifecycleScope.launch {
            try {
                val payload = cartItems.map { cartItem ->
                    cartItem.item to (cartItem.quantity to cartItem.sellingPrice)
                }
                sharedViewModel.completeCartSale(payload)
                dialog.dismiss()
            } catch (e: Exception) {
                "Sale failed: ${e.message}".toastError(requireContext())
            }
        }
    }

    // ============================================================
    // SHARED CLEAR BUTTON HELPERS
    // ============================================================

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

    // ============================================================
    // UTILITY
    // ============================================================

    private fun hideKeyboard(view: View) {
        val imm = requireContext()
            .getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
        imm.hideSoftInputFromWindow(view.windowToken, 0)
    }
}