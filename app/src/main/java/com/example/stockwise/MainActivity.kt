package com.example.stockwise

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import com.example.stockwise.ui.fragment.BeltsFragment
import com.example.stockwise.ui.fragment.CalendarFragment
import com.example.stockwise.ui.fragment.DashboardFragment
import com.example.stockwise.ui.fragment.ProductsFragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var bottomNavigationView: BottomNavigationView
    private var activeFragment: Fragment? = null

    companion object {
        private const val TAG_DASHBOARD = "tag_dashboard"
        private const val TAG_PRODUCTS = "tag_products"
        private const val TAG_BELTS = "tag_belts"
        private const val TAG_CALENDAR = "tag_calendar"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bottomNavigationView = findViewById(R.id.bottom_navigation)
        bottomNavigationView.labelVisibilityMode = BottomNavigationView.LABEL_VISIBILITY_LABELED

        if (savedInstanceState == null) {
            showFragment(TAG_DASHBOARD)
        } else {
            // After a config change, find whichever fragment is currently visible
            // so we hide/show correctly on the next nav tap.
            activeFragment = supportFragmentManager.fragments.firstOrNull { it.isVisible }
        }

        bottomNavigationView.setOnItemSelectedListener { menuItem ->
            when (menuItem.itemId) {
                R.id.nav_dashboard -> { showFragment(TAG_DASHBOARD); true }
                R.id.nav_products -> { showFragment(TAG_PRODUCTS); true }
                R.id.nav_belts -> { showFragment(TAG_BELTS); true }
                R.id.nav_calendar -> { showFragment(TAG_CALENDAR); true }
                else -> false
            }
        }
    }

    /**
     * Adds each tab's fragment lazily on first visit, then just show()/hide()s it
     * afterwards — the fragment (and its ViewModel, and its already-loaded data)
     * stays alive in the FragmentManager instead of being torn down and rebuilt
     * every time the user switches tabs.
     */
    private fun showFragment(tag: String) {
        val fm = supportFragmentManager
        if (activeFragment != null && fm.findFragmentByTag(tag) === activeFragment) return

        val transaction = fm.beginTransaction()
        activeFragment?.let { transaction.hide(it) }

        var fragment = fm.findFragmentByTag(tag)
        if (fragment == null) {
            fragment = createFragment(tag)
            transaction.add(R.id.content_frame, fragment, tag)
        } else {
            transaction.show(fragment)
        }

        transaction.commit()
        activeFragment = fragment
    }

    private fun createFragment(tag: String): Fragment = when (tag) {
        TAG_DASHBOARD -> DashboardFragment()
        TAG_PRODUCTS -> ProductsFragment()
        TAG_BELTS -> BeltsFragment()
        TAG_CALENDAR -> CalendarFragment()
        else -> throw IllegalArgumentException("Unknown fragment tag: $tag")
    }

    private fun loadFragment(fragment: Fragment) {
        // Kept for compatibility if referenced elsewhere; prefer showFragment().
        supportFragmentManager.beginTransaction()
            .replace(R.id.content_frame, fragment)
            .commit()
    }
}