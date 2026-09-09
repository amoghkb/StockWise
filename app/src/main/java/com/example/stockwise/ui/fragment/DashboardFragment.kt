package com.example.stockwise.ui.fragment

import android.animation.ObjectAnimator
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.stockwise.R
import com.example.stockwise.data.entities.DailySalesSummary
import com.example.stockwise.databinding.FragmentDashboardBinding
import com.example.stockwise.ui.adapter.LowStockAdapter
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

    private var _binding: FragmentDashboardBinding? = null
    private val binding get() = _binding!!

    private val sharedViewModel: SharedDataViewModel by activityViewModels()
    private lateinit var lowStockAdapter: LowStockAdapter

    private var isSyncing = false
    private var rotateAnimator: ObjectAnimator? = null
    private val SYNC_DURATION = 1000L
    private val handler = Handler(Looper.getMainLooper())
    private var isFirstLoad = true

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

        // Show skeleton on first load
        showSkeleton(true)
        sharedViewModel.refreshDashboardData()
    }

    override fun onResume() {
        super.onResume()
        if (!isFirstLoad) {
            sharedViewModel.refreshDashboardData()
        }
    }

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
            // Navigate to Add Item screen
        }
    }

    private fun performSync() {
        isSyncing = true
        binding.btnSync.isEnabled = false
        binding.btnSync.alpha = 0.7f
        startSyncAnimation()

        // Show skeleton while syncing
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
                lowStockAdapter.submitList(items)
                binding.tvLowStockCount.text = "${items.size} items"

                // Hide skeleton when data arrives
                showSkeleton(false)

                if (isSyncing) {
                    stopSync()
                }
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
                    showSkeleton(false)
                    // Show error toast if needed
                }
            }
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

    override fun onDestroyView() {
        super.onDestroyView()
        handler.removeCallbacksAndMessages(null)
        rotateAnimator?.cancel()
        rotateAnimator = null
        _binding = null
    }
}