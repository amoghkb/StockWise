package com.example.stockwise.ui.fragment

import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.stockwise.R
import com.example.stockwise.commons.parseDateKey
import com.example.stockwise.databinding.FragmentCalendarBinding
import com.example.stockwise.ui.adapter.ScheduledDateAdapter
import com.example.stockwise.viewmodels.SharedDataViewModel
import com.kizitonwose.calendar.core.CalendarDay
import com.kizitonwose.calendar.core.DayPosition
import com.kizitonwose.calendar.core.daysOfWeek
import com.kizitonwose.calendar.view.MonthDayBinder
import com.kizitonwose.calendar.view.ViewContainer
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Calendar
import java.util.Locale

@AndroidEntryPoint
class CalendarFragment : Fragment() {

    private var _binding: FragmentCalendarBinding? = null
    private val binding get() = _binding!!

    private val sharedViewModel: SharedDataViewModel by activityViewModels()

    private lateinit var scheduledAdapter: ScheduledDateAdapter

    /** "yyyy-MM" prefix of the month currently visible in the calendar. */
    private var currentMonthPrefix: String = ""

    /** Collects the current month's date keys. Cancelled on month change. */
    private var monthCollectionJob: Job? = null

    /** Set of LocalDates that have procurement data — used by DayBinder for dots. */
    private var activeDatesForCurrentMonth: Set<LocalDate> = emptySet()

    private val monthTitleFormatter =
        DateTimeFormatter.ofPattern("MMMM yyyy", Locale.US)

    /** Today's date, cached. Recomputed in onResume in case the app stays open past midnight. */
    private var today: LocalDate = LocalDate.now()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCalendarBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // ---------- RecyclerView (list below calendar) ----------
        scheduledAdapter = ScheduledDateAdapter { dateKey ->
            val cal = parseDateKey(dateKey)
            navigateToStockProcure(cal)
        }
        binding.rvScheduled.layoutManager = LinearLayoutManager(requireContext())
        binding.rvScheduled.adapter = scheduledAdapter

        // ---------- kizitonwose CalendarView ----------
        setupCalendarView()
    }

    override fun onResume() {
        super.onResume()

        // Refresh today's date (in case we crossed midnight).
        val newToday = LocalDate.now()
        val dayChanged = newToday != today
        today = newToday

        // Always snap back to the current month when the fragment becomes visible,
        // so returning from another screen lands on "this month with today highlighted".
        val currentMonth = YearMonth.now()
        binding.calendarView.scrollToMonth(currentMonth)

        // Refresh the data for the current month.
        val prefix = monthPrefixOf(currentMonth.year, currentMonth.monthValue)
        if (prefix != currentMonthPrefix) {
            currentMonthPrefix = prefix
            binding.tvMonthYear.text = currentMonth.format(monthTitleFormatter)
        }
        observeMonth(prefix)

        // If the day changed while we were away, redraw so the "today" circle moves.
        if (dayChanged) {
            binding.calendarView.notifyCalendarChanged()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        monthCollectionJob?.cancel()
        monthCollectionJob = null
        _binding = null
    }

    // ============================================================
    // CALENDAR SETUP
    // ============================================================

    private fun setupCalendarView() {
        val currentMonth = YearMonth.now()
        val startMonth = currentMonth.minusMonths(50)
        val endMonth = currentMonth.plusMonths(50)
        val firstDayOfWeek = daysOfWeek().first()

        // 1. Setup range + first day of week
        binding.calendarView.setup(startMonth, endMonth, firstDayOfWeek)

        // 2. Weekday header row (Mon Tue Wed ...) — library doesn't draw this
        setupWeekdayHeader(daysOfWeek())

        // 3. Prev / Next month arrows
        binding.btnPrevMonth.setOnClickListener {
            binding.calendarView.findFirstVisibleMonth()?.let {
                binding.calendarView.scrollToMonth(it.yearMonth.minusMonths(1))
            }
        }
        binding.btnNextMonth.setOnClickListener {
            binding.calendarView.findFirstVisibleMonth()?.let {
                binding.calendarView.scrollToMonth(it.yearMonth.plusMonths(1))
            }
        }

        // 4. Day binder: renders each day cell
        binding.calendarView.dayBinder = object : MonthDayBinder<DayViewContainer> {
            override fun create(view: View) = DayViewContainer(view)

            override fun bind(container: DayViewContainer, day: CalendarDay) {
                val date = day.date
                container.textView.text = date.dayOfMonth.toString()

                val isToday = (date == today)
                val isCurrentMonth = (day.position == DayPosition.MonthDate)

                // ----- Text color -----
                container.textView.setTextColor(
                    when {
                        isToday -> Color.WHITE                              // white on blue circle
                        isCurrentMonth -> Color.BLACK                       // normal day
                        else -> Color.LTGRAY                                // adjacent month day
                    }
                )

                // ----- Background (today gets a blue circle) -----
                if (isToday) {
                    container.textView.background = todayCircleDrawable()
                    container.textView.setTypeface(null, android.graphics.Typeface.BOLD)
                } else {
                    container.textView.background = null
                    container.textView.setTypeface(null, android.graphics.Typeface.NORMAL)
                }

                // ----- Dot for scheduled procurement -----
                container.dotView.visibility =
                    if (activeDatesForCurrentMonth.contains(date)) View.VISIBLE
                    else View.INVISIBLE

                // ----- Tap a day → open StockProcureFragment -----
                container.textView.setOnClickListener {
                    val cal = Calendar.getInstance().apply {
                        set(
                            date.year,
                            date.monthValue - 1,  // Calendar month is 0-indexed
                            date.dayOfMonth,
                            0, 0, 0
                        )
                        set(Calendar.MILLISECOND, 0)
                    }
                    navigateToStockProcure(cal)
                }
            }
        }

        // 5. Month scroll listener: reliably fires when a new month settles
        binding.calendarView.monthScrollListener = { calendarMonth ->
            val yearMonth = calendarMonth.yearMonth

            // Update the month/year title
            binding.tvMonthYear.text = yearMonth.format(monthTitleFormatter)

            val monthPrefix = monthPrefixOf(yearMonth.year, yearMonth.monthValue)
            currentMonthPrefix = monthPrefix
            observeMonth(monthPrefix)
        }

        // 6. Jump to current month. This also triggers monthScrollListener.
        binding.calendarView.scrollToMonth(currentMonth)
    }

    /**
     * Builds the Mon/Tue/Wed... header row above the calendar.
     * The kizitonwose CalendarView does not draw a weekday header by itself.
     */
    private fun setupWeekdayHeader(days: List<DayOfWeek>) {
        binding.weekdayHeader.removeAllViews()
        val labels = days.map { it.getDisplayName(TextStyle.SHORT, Locale.US) }
        labels.forEach { label ->
            val tv = TextView(requireContext()).apply {
                text = label
                gravity = Gravity.CENTER
                layoutParams = LinearLayout.LayoutParams(
                    0,
                    ViewGroup.LayoutParams.WRAP_CONTENT,
                    1f
                )
                setTextColor(Color.GRAY)
                textSize = 12f
            }
            binding.weekdayHeader.addView(tv)
        }
    }

    /** Blue oval background used to highlight today's date. */
    private fun todayCircleDrawable(): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setColor(ContextCompat.getColor(requireContext(), R.color.today_highlight))
        }

    // ============================================================
    // MONTH COLLECTION
    // ============================================================

    private fun observeMonth(monthPrefix: String) {
        monthCollectionJob?.cancel()
        monthCollectionJob = viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                sharedViewModel.getActiveDateKeysForMonth(monthPrefix)
                    .collectLatest { dateKeys ->
                        // 1. Update the list adapter below the calendar
                        scheduledAdapter.submitList(dateKeys)

                        val count = dateKeys.size
                        binding.tvEventsCount.text =
                            "$count Event${if (count != 1) "s" else ""}"

                        if (dateKeys.isEmpty()) {
                            binding.rvScheduled.visibility = View.GONE
                            binding.llScheduledEmpty.visibility = View.VISIBLE
                        } else {
                            binding.rvScheduled.visibility = View.VISIBLE
                            binding.llScheduledEmpty.visibility = View.GONE
                        }

                        // 2. Update the Set used by the DayBinder to render dots
                        activeDatesForCurrentMonth = dateKeys.mapNotNull { key ->
                            runCatching { LocalDate.parse(key) }.getOrNull()
                        }.toSet()

                        // 3. Redraw calendar so dots appear/disappear immediately
                        binding.calendarView.notifyCalendarChanged()
                    }
            }
        }
    }

    // ============================================================
    // NAVIGATION
    // ============================================================

    private fun navigateToStockProcure(cal: Calendar) {
        val fragment = StockProcureFragment().apply {
            arguments = Bundle().apply {
                putInt(StockProcureFragment.KEY_YEAR, cal.get(Calendar.YEAR))
                putInt(StockProcureFragment.KEY_MONTH, cal.get(Calendar.MONTH))
                putInt(StockProcureFragment.KEY_DAY, cal.get(Calendar.DAY_OF_MONTH))
            }
        }

        requireActivity().supportFragmentManager.beginTransaction()
            .replace(R.id.content_frame, fragment)
            .addToBackStack(null)
            .commit()
    }

    // ============================================================
    // HELPERS
    // ============================================================

    private fun monthPrefixOf(year: Int, monthValue: Int): String =
        String.format(Locale.US, "%04d-%02d", year, monthValue)

    /** Custom ViewContainer for a calendar day cell. */
    class DayViewContainer(view: View) : ViewContainer(view) {
        val textView: TextView = view.findViewById(R.id.calendarDayText)
        val dotView: View = view.findViewById(R.id.calendarDayDot)
    }
}