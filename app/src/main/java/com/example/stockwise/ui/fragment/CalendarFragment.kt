package com.example.stockwise.ui.fragment

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.CalendarView
import androidx.fragment.app.Fragment
import com.example.stockwise.R
import java.util.Calendar

class CalendarFragment : Fragment() {

    private lateinit var calendarView: CalendarView

    // Track last navigation to debounce rapid duplicate events
    private var lastNavigatedMillis = 0L
    private var isFirstSetup = true

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_calendar, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        calendarView = view.findViewById(R.id.calendarView)

        calendarView.setOnDateChangeListener { _, year, month, dayOfMonth ->
            // Skip the very first callback (CalendarView's initial setup)
            if (isFirstSetup) {
                isFirstSetup = false
                return@setOnDateChangeListener
            }

            // Build a comparable millis value for this selected date
            val cal = Calendar.getInstance().apply {
                set(year, month, dayOfMonth, 0, 0, 0)
                set(Calendar.MILLISECOND, 0)
            }
            val millis = cal.timeInMillis

            // Skip if the same date was just navigated to (in the last 800 ms)
            val now = System.currentTimeMillis()
            if (millis == lastNavigatedMillis && now - lastClickTime < 800) return@setOnDateChangeListener

            lastNavigatedMillis = millis
            lastClickTime = now

            navigateToStockProcure(cal)
        }
    }

    private var lastClickTime = 0L

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
}