package com.example.stockwise

import android.os.Bundle
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import com.example.stockwise.auth.AuthSessionManager
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
    private lateinit var tvProfile: TextView
    private lateinit var session: AuthSessionManager

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

        session = AuthSessionManager.getInstance(this)

        bottomNavigationView = findViewById(R.id.bottom_navigation)
        bottomNavigationView.labelVisibilityMode = BottomNavigationView.LABEL_VISIBILITY_LABELED

        tvProfile = findViewById(R.id.tv_profile)
        tvProfile.setOnClickListener {
            openProfileFragment()
        }
        updateProfileAvatar()
        supportFragmentManager.addOnBackStackChangedListener {
            updateProfileAvatar()
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

    override fun onResume() {
        super.onResume()
        // Refresh avatar in case the user changed their name in the profile screen
        updateProfileAvatar()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(KEY_ACTIVE_TAG, activeTag)
    }

    // ==========================================
    // PROFILE AVATAR
    // ==========================================

    /**
     * Sets the initials shown in the top-bar avatar to match the logged-in user.
     * Example: "Bala Subramanian" → "BS"
     */
    private fun updateProfileAvatar() {
        val name = session.getUserName().ifBlank { "User" }
        tvProfile.text = initialsFrom(name)
    }

    private fun initialsFrom(name: String): String {
        return name.trim()
            .split(" ")
            .filter { it.isNotBlank() }
            .take(2)
            .mapNotNull { it.firstOrNull()?.uppercaseChar() }
            .joinToString("")
            .ifEmpty { "U" }
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