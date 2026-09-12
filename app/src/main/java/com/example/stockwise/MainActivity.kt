package com.example.stockwise

import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
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
            activeFragment = supportFragmentManager.fragments.firstOrNull { it.isVisible }
        }

        bottomNavigationView.setOnItemSelectedListener { menuItem ->
            // Close any nested fragment (e.g. StockProcureFragment inside CalendarFragment)
            // before switching tabs, so the user always lands on the root screen.
            closeNestedFragments()

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
     * Clears any child fragments that the current tab (or its children) has
     * pushed on its back stack. For example, if the Calendar tab is currently
     * showing StockProcureFragment as a nested child, this removes it so the
     * calendar root is restored before the tab switch happens.
     */
    private fun closeNestedFragments() {
        val current = activeFragment ?: return

        // 1) Close children of the currently active tab fragment
        val childFm = current.childFragmentManager
        if (childFm.backStackEntryCount > 0) {
            childFm.popBackStackImmediate(
                null,
                FragmentManager.POP_BACK_STACK_INCLUSIVE
            )
        }
        // Belt-and-braces: remove any remaining children
        childFm.fragments.toList().forEach { child ->
            childFm.beginTransaction().remove(child).commitNowAllowingStateLoss()
        }

        // 2) Clear anything the active tab pushed on the *activity's* back stack
        //    (covers fragments added via supportFragmentManager by the tab).
        supportFragmentManager.popBackStackImmediate(
            null,
            FragmentManager.POP_BACK_STACK_INCLUSIVE
        )
    }

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
        supportFragmentManager.beginTransaction()
            .replace(R.id.content_frame, fragment)
            .commit()
    }
}