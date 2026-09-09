package com.example.stockwise.ui.fragment

import android.animation.ObjectAnimator
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import android.widget.TextView
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import com.example.stockwise.R
import com.example.stockwise.data.entities.DailySalesSummary
import com.example.stockwise.data.entities.ItemWithCategoryAndVehicles
import com.example.stockwise.databinding.FragmentDashboardBinding
import androidx.fragment.app.activityViewModels
import com.example.stockwise.viewmodels.SharedDataViewModel
import com.github.mikephil.charting.components.XAxis
import com.github.mikephil.charting.data.BarData
import com.github.mikephil.charting.data.BarDataSet
import com.github.mikephil.charting.data.BarEntry
import com.github.mikephil.charting.formatter.IndexAxisValueFormatter
import com.github.mikephil.charting.formatter.ValueFormatter
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import java.text.DecimalFormat

@AndroidEntryPoint
class DashboardFragment : Fragment() {

    // View Binding
    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    // Use SharedDataViewModel instead of DashboardViewModel
    private val sharedViewModel: SharedDataViewModel by activityViewModels()

    // Animation state
    private var isSyncing = false
    private var rotateAnimator: ObjectAnimator? = null
    private val SYNC_DURATION = 1000L

    // Handler for delayed operations
    private val handler = Handler(Looper.getMainLooper())

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

        setupClickListeners()
        observeData()
    }

    override fun onResume() {
        super.onResume()
        // Refresh data when fragment becomes visible
        // This ensures data is fresh when returning from other fragments
        sharedViewModel.refreshDashboardData()
    }

    private fun setupClickListeners() {
        binding.btnSync.setOnClickListener {
            if (!isSyncing) {
                performSync()
            }
        }

        binding.fabAddItem.setOnClickListener {
            // Navigate to Add Item screen
        }
    }

    private fun performSync() {
        isSyncing = true
        binding.btnSync.isEnabled = false
        binding.btnSync.alpha = 0.7f
        startSyncAnimation()
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

    private fun observeData() {
        // Observe greeting
        viewLifecycleOwner.lifecycleScope.launch {
            sharedViewModel.greeting.collect { greeting ->
                binding.tvGreeting.text = greeting
            }
        }

        // Observe current date
        viewLifecycleOwner.lifecycleScope.launch {
            sharedViewModel.currentDate.collect { date ->
                binding.tvDate.text = date
            }
        }

        // Observe today's sales
        viewLifecycleOwner.lifecycleScope.launch {
            sharedViewModel.todaySales.collect { sales ->
                binding.tvTodaySales.text = sales
            }
        }

        // Observe stock alerts count
        viewLifecycleOwner.lifecycleScope.launch {
            sharedViewModel.stockAlertsCount.collect { count ->
                binding.tvStockAlerts.text = count
            }
        }

        // Observe total items
        viewLifecycleOwner.lifecycleScope.launch {
            sharedViewModel.totalItems.collect { total ->
                binding.tvTotalItems.text = total
            }
        }

        // Observe low stock items
        viewLifecycleOwner.lifecycleScope.launch {
            sharedViewModel.lowStockItems.collect { items ->
                populateLowStockItems(items)
            }
        }

        // Observe weekly sales data
        viewLifecycleOwner.lifecycleScope.launch {
            sharedViewModel.weeklySalesData.collect { salesData ->
                populateWeeklyBarChart(salesData)
            }
        }

        // Observe loading state
        viewLifecycleOwner.lifecycleScope.launch {
            sharedViewModel.isLoading.collect { isLoading ->
                if (!isLoading && isSyncing) {
                    stopSync()
                }
            }
        }

        // Observe error state
        viewLifecycleOwner.lifecycleScope.launch {
            sharedViewModel.error.collect { error ->
                error?.let {
                    if (isSyncing) {
                        stopSync()
                    }
                    // Show error toast if needed
                }
            }
        }
    }

    private fun populateLowStockItems(items: List<ItemWithCategoryAndVehicles>) {
        binding.layoutLowStockList.removeAllViews()

        if (items.isEmpty()) {
            val emptyView = layoutInflater.inflate(
                R.layout.item_empty_state,
                binding.layoutLowStockList,
                false
            )
            binding.layoutLowStockList.addView(emptyView)
            return
        }

        items.forEachIndexed { index, item ->
            val itemView = layoutInflater.inflate(
                R.layout.item_low_stock,
                binding.layoutLowStockList,
                false
            )

            val tvItemName = itemView.findViewById<TextView>(R.id.tv_item_name)
            val tvStockCount = itemView.findViewById<TextView>(R.id.tv_stock_count)
            val tvStockLabel = itemView.findViewById<TextView>(R.id.tv_stock_label)
            val progressBar = itemView.findViewById<ProgressBar>(R.id.progress_bar)

            tvItemName.text = item.item.name
            tvStockCount.text = item.item.stock.toString()
            tvStockLabel.text = "left"

            val progress = sharedViewModel.getStockProgress(item)
            progressBar.progress = progress

            if (index < items.size - 1) {
                val divider = View(requireContext())
                divider.layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    1
                )
                divider.setBackgroundColor(requireContext().getColor(R.color.divider_color))
                binding.layoutLowStockList.addView(divider)
            }

            binding.layoutLowStockList.addView(itemView)
        }
    }

    private fun populateWeeklyBarChart(salesData: List<DailySalesSummary>) {
        val barChart = binding.chartWeeklySales

        if (salesData.isEmpty() || salesData.all { it.totalAmount == 0.0 }) {
            barChart.clear()
            barChart.setNoDataText("No sales data for the week")
            barChart.invalidate()
            return
        }

        // Convert data to BarEntry
        val entries = salesData.mapIndexed { index, day ->
            BarEntry(index.toFloat(), day.totalAmount.toFloat())
        }

        // Create DataSet and style it
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

        // Prepare day labels
        val days = salesData.map { it.date ?: "" }

        // Setup BarData
        val barData = BarData(dataSet).apply {
            barWidth = 0.6f
        }

        // Configure chart
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

            // Configure X-axis
            xAxis.apply {
                valueFormatter = IndexAxisValueFormatter(days)
                position = XAxis.XAxisPosition.BOTTOM
                setDrawGridLines(false)
                setDrawAxisLine(false)
                setDrawLabels(true)
                textSize = 11f
                textColor = resources.getColor(R.color.text_secondary, null)
            }

            // Configure left Y-axis
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

            // Disable right Y-axis
            axisRight.isEnabled = false

            // Configure legend
            legend.isEnabled = false

            invalidate()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        handler.removeCallbacksAndMessages(null)
        rotateAnimator?.cancel()
        rotateAnimator = null
        _binding = null
    }
}