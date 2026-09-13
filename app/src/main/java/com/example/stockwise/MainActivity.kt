package com.example.stockwise

import android.os.Bundle
import android.widget.ImageView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.example.stockwise.ui.fragment.BeltsFragment
import com.example.stockwise.ui.fragment.CalendarFragment
import com.example.stockwise.ui.fragment.DashboardFragment
import com.example.stockwise.ui.fragment.ProductsFragment
import com.example.stockwise.ui.fragment.ProfileFragment
import com.google.android.material.bottomnavigation.BottomNavigationView
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : AppCompatActivity() {

    private lateinit var bottomNavigationView: BottomNavigationView

    private var activeTag: String = TAG_DASHBOARD

    companion object {
        private const val TAG_DASHBOARD = "tag_dashboard"
        private const val TAG_PRODUCTS = "tag_products"
        private const val TAG_BELTS = "tag_belts"
        private const val TAG_CALENDAR = "tag_calendar"
        private const val TAG_PROFILE = "tag_profile"
        private const val KEY_ACTIVE_TAG = "key_active_tag"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bottomNavigationView = findViewById(R.id.bottom_navigation)
        bottomNavigationView.labelVisibilityMode = BottomNavigationView.LABEL_VISIBILITY_LABELED

        findViewById<ImageView>(R.id.iv_profile).setOnClickListener {
            openProfileFragment()
        }

        activeTag = savedInstanceState?.getString(KEY_ACTIVE_TAG) ?: TAG_DASHBOARD

        if (savedInstanceState == null) {
            showFragment(activeTag)
        } else {
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

         
            popActivityBackStackIfAny()

            if (tag == activeTag) {
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

    // ==========================================
    // PROFILE
    // ==========================================

    private fun openProfileFragment() {
        supportFragmentManager.beginTransaction()
            .replace(R.id.content_frame, ProfileFragment(), TAG_PROFILE)
            .addToBackStack(TAG_PROFILE)
            .commit()
    }

    // ==========================================
    // BACK STACK CLEANUP
    // ==========================================

    /**
     * Pops every fragment that was pushed on top of a tab via the activity's
     * back stack (StockProcureFragment from CalendarFragment, ProfileFragment,
     * etc.). Called before a tab switch so the newly chosen tab is what shows.
     */
    private fun popActivityBackStackIfAny() {
        val fm = supportFragmentManager
        if (fm.backStackEntryCount > 0) {
            fm.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        }
    }

    // ==========================================
    // TAB NAVIGATION
    // ==========================================

    private fun closeNestedFragments(tag: String) {
        val current = supportFragmentManager.findFragmentByTag(tag) ?: return
        val childFm = current.childFragmentManager

        if (childFm.backStackEntryCount > 0) {
            childFm.popBackStack(null, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        }
    }

    private fun showFragment(tag: String) {
        val fm = supportFragmentManager

        // Safety net: if profile is still around for any reason, pop it.
        if (fm.findFragmentByTag(TAG_PROFILE) != null) {
            fm.popBackStack(TAG_PROFILE, FragmentManager.POP_BACK_STACK_INCLUSIVE)
        }

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