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

    // Track the current tab by TAG (a String), not by holding a live Fragment
    // reference. Tags survive process death / config changes cleanly via
    // onSaveInstanceState; a cached Fragment reference does not.
    private var activeTag: String = TAG_DASHBOARD

    companion object {
        private const val TAG_DASHBOARD = "tag_dashboard"
        private const val TAG_PRODUCTS = "tag_products"
        private const val TAG_BELTS = "tag_belts"
        private const val TAG_CALENDAR = "tag_calendar"
        private const val KEY_ACTIVE_TAG = "key_active_tag"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bottomNavigationView = findViewById(R.id.bottom_navigation)
        bottomNavigationView.labelVisibilityMode = BottomNavigationView.LABEL_VISIBILITY_LABELED

        activeTag = savedInstanceState?.getString(KEY_ACTIVE_TAG) ?: TAG_DASHBOARD

        if (savedInstanceState == null) {
            // Fresh start: create + show the first tab.
            showFragment(activeTag)
        } else {
            // FragmentManager has already restored all previously-added
            // fragments (correctly hidden/shown) by this point. We don't
            // need to guess from view visibility or re-attach anything —
            // just make sure the bottom nav highlight matches activeTag.
            syncBottomNavSelection(activeTag)
        }

        bottomNavigationView.setOnItemSelectedListener { menuItem ->
            val tag = when (menuItem.itemId) {
                R.id.nav_dashboard -> TAG_DASHBOARD
                R.id.nav_products -> TAG_PRODUCTS
                R.id.nav_belts -> TAG_BELTS
                R.id.nav_calendar -> TAG_CALENDAR
                else -> return@setOnItemSelectedListener false
            }

            if (tag == activeTag) {
                // Re-tapping the current tab: just pop it back to its root,
                // no tab switch needed.
                closeNestedFragments(tag)
            } else {
                closeNestedFragments(tag)
                showFragment(tag)
            }
            true
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_ACTIVE_TAG, activeTag)
    }

    /**
     * Clears any child fragments the given tab has pushed on its own
     * back stack (e.g. StockProcureFragment nested inside CalendarFragment),
     * so the tab's root screen is what's visible next.
     *
     * Uses the normal async popBackStack()/commit() — NOT the Immediate /
     * *AllowingStateLoss variants. Those force a synchronous main-thread
     * execution on every single nav click, which is what was causing the
     * visible lag. The async calls get queued on the main thread and run
     * before the next frame is drawn, so there's no visual difference,
     * just no forced synchronous work.
     */
    private fun closeNestedFragments(tag: String) {
        val current = supportFragmentManager.findFragmentByTag(tag) ?: return
        val childFm = current.childFragmentManager

        if (childFm.backStackEntryCount > 0) {
            childFm.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        }
    }

    private fun showFragment(tag: String) {
        val fm = supportFragmentManager
        val current = fm.findFragmentByTag(activeTag)
        var target = fm.findFragmentByTag(tag)

        val transaction = fm.beginTransaction().setReorderingAllowed(true)

        if (current != null && current !== target) {
            transaction.hide(current)
        }

        if (target == null) {
            target = createFragment(tag)
            transaction.add(R.id.content_frame, target, tag)
        } else {
            transaction.show(target)
        }

        transaction.commit()

        activeTag = tag
        syncBottomNavSelection(tag)
    }

    private fun syncBottomNavSelection(tag: String) {
        val itemId = when (tag) {
            TAG_DASHBOARD -> R.id.nav_dashboard
            TAG_PRODUCTS -> R.id.nav_products
            TAG_BELTS -> R.id.nav_belts
            TAG_CALENDAR -> R.id.nav_calendar
            else -> return
        }
        if (bottomNavigationView.selectedItemId != itemId) {
            bottomNavigationView.selectedItemId = itemId
        }
    }

    private fun createFragment(tag: String): Fragment = when (tag) {
        TAG_DASHBOARD -> DashboardFragment()
        TAG_PRODUCTS -> ProductsFragment()
        TAG_BELTS -> BeltsFragment()
        TAG_CALENDAR -> CalendarFragment()
        else -> throw IllegalArgumentException("Unknown fragment tag: $tag")
    }
}