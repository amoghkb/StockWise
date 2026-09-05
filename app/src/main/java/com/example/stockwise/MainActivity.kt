package com.example.stockwise

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.example.stockwise.fragment.BeltsFragment
import com.example.stockwise.fragment.CalendarFragment
import com.example.stockwise.fragment.DashboardFragment
import com.example.stockwise.fragment.ProductsFragment
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : AppCompatActivity() {

    private lateinit var bottomNavigationView: BottomNavigationView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bottomNavigationView = findViewById(R.id.bottom_navigation)

        // This shows text ONLY for the selected tab
        bottomNavigationView.labelVisibilityMode = BottomNavigationView.LABEL_VISIBILITY_LABELED
        if (savedInstanceState == null) {
            loadFragment(DashboardFragment())
        }

        // Set up navigation listener
        bottomNavigationView.setOnItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_dashboard -> {
                    loadFragment(DashboardFragment())
                    true
                }
                R.id.nav_products -> {
                    loadFragment(ProductsFragment())
                    true
                }
                R.id.nav_belts -> {
                    loadFragment(BeltsFragment())
                    true
                }
                R.id.nav_calendar -> {
                    loadFragment(CalendarFragment())
                    true
                }
                else -> false
            }
        }
    }

    private fun loadFragment(fragment: Fragment) {
        supportFragmentManager.beginTransaction()
            .replace(R.id.content_frame, fragment)
            .commit()
    }
}