package com.example.stockwise.ui.fragment

import android.annotation.SuppressLint
import android.content.Intent
import android.os.Bundle
import android.util.Patterns
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatDelegate
import androidx.appcompat.widget.AppCompatButton
import androidx.appcompat.widget.SwitchCompat
import androidx.fragment.app.Fragment
import com.example.stockwise.R
import com.example.stockwise.auth.AuthSessionManager
import com.example.stockwise.commons.toastError
import com.example.stockwise.commons.toastSuccess
import com.example.stockwise.ui.auth.AuthActivity

class ProfileFragment : Fragment() {

    private lateinit var session: AuthSessionManager

    private lateinit var tvInitials: TextView
    private lateinit var tvName: TextView
    private lateinit var tvEmail: TextView
    private lateinit var ivEditName: ImageView
    private lateinit var switchDarkMode: SwitchCompat
    private lateinit var rowLogout: LinearLayout
    private lateinit var ivCloseProfile: ImageView

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        return inflater.inflate(R.layout.fragment_profile, container, false)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        session = AuthSessionManager.getInstance(requireContext())

        // Bind views
        tvInitials = view.findViewById(R.id.tvInitials)
        tvName = view.findViewById(R.id.tvName)
        tvEmail = view.findViewById(R.id.tvEmail)
        ivEditName = view.findViewById(R.id.ivEditName)
        switchDarkMode = view.findViewById(R.id.switchDarkMode)
        rowLogout = view.findViewById(R.id.rowLogout)
        ivCloseProfile = view.findViewById(R.id.ivCloseProfile)

        loadUserInfo()

        // Dark mode toggle state
        switchDarkMode.isChecked =
            AppCompatDelegate.getDefaultNightMode() == AppCompatDelegate.MODE_NIGHT_YES

        // ===== Listeners =====
        ivCloseProfile.setOnClickListener { goBack() }
        ivEditName.setOnClickListener { showEditProfileDialog() }

        switchDarkMode.setOnCheckedChangeListener { _, isChecked ->
            AppCompatDelegate.setDefaultNightMode(
                if (isChecked) AppCompatDelegate.MODE_NIGHT_YES
                else AppCompatDelegate.MODE_NIGHT_NO
            )
        }

        rowLogout.setOnClickListener { confirmLogout() }
    }

    // ==========================================
    // LOAD USER DATA
    // ==========================================

    @SuppressLint("SetTextI18n")
    private fun loadUserInfo() {
        val name = session.getUserName().ifBlank { "User" }
        val email = session.getUserEmail().ifBlank { "no-email@example.com" }

        tvName.text = name
        tvEmail.text = email
        tvInitials.text = initialsFrom(name)
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
    // EDIT PROFILE DIALOG
    // ==========================================

    @SuppressLint("InflateParams")
    private fun showEditProfileDialog() {
        val dialogView: View = layoutInflater.inflate(R.layout.dialog_edit_profile, null)
        val etName = dialogView.findViewById<EditText>(R.id.etName)
        val etEmail = dialogView.findViewById<EditText>(R.id.etEmail)

        etName.setText(session.getUserName())
        etEmail.setText(session.getUserEmail())

        val dialog = android.app.Dialog(requireContext()).apply {
            setContentView(dialogView)
            window?.setBackgroundDrawableResource(android.R.color.transparent)
            window?.setLayout(
                (resources.displayMetrics.widthPixels * 0.9f).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setCancelable(true)
        }

        dialogView.findViewById<AppCompatButton>(R.id.btnCancel).setOnClickListener {
            dialog.dismiss()
        }

        dialogView.findViewById<AppCompatButton>(R.id.btnSave).setOnClickListener {
            val newName = etName.text.toString().trim()
            val newEmail = etEmail.text.toString().trim()

            when {
                newName.isEmpty() -> {
                    "Name cannot be empty".toastError(requireContext()); return@setOnClickListener
                }
                newName.length < 2 -> {
                    "Name must be at least 2 characters".toastError(requireContext()); return@setOnClickListener
                }
                newEmail.isEmpty() -> {
                    "Email cannot be empty".toastError(requireContext()); return@setOnClickListener
                }
                !Patterns.EMAIL_ADDRESS.matcher(newEmail).matches() -> {
                    "Please enter a valid email".toastError(requireContext()); return@setOnClickListener
                }
            }

            session.updateProfile(newName, newEmail)
            loadUserInfo()
            dialog.dismiss()
            "Profile updated".toastSuccess(requireContext())
        }

        dialog.show()
    }

    // ==========================================
    // LOGOUT
    // ==========================================

    @SuppressLint("InflateParams", "SetTextI18n")
    private fun confirmLogout() {
        val dialogView: View = layoutInflater.inflate(R.layout.dialog_confirm_delete, null)
        dialogView.findViewById<TextView>(R.id.tvDialogTitle).text = "Log Out"
        dialogView.findViewById<TextView>(R.id.tvDialogMessage).text =
            "Are you sure you want to log out of StockWise?"

        val dialog = android.app.Dialog(requireContext()).apply {
            setContentView(dialogView)
            window?.setBackgroundDrawableResource(android.R.color.transparent)
            window?.setLayout(
                (resources.displayMetrics.widthPixels * 0.88f).toInt(),
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
            setCancelable(true)
        }

        val btnPositive = dialogView.findViewById<AppCompatButton>(R.id.btnDelete)
        btnPositive.text = "Log Out"

        dialogView.findViewById<AppCompatButton>(R.id.btnCancel)
            .setOnClickListener { dialog.dismiss() }

        btnPositive.setOnClickListener {
            dialog.dismiss()
            performLogout()
        }

        dialog.show()
    }

    private fun performLogout() {
        session.logout()
        val intent = Intent(requireContext(), AuthActivity::class.java)
        intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
        startActivity(intent)
        requireActivity().finishAffinity()
    }

    // ==========================================
    // BACK NAVIGATION
    // ==========================================

    private fun goBack() {
        requireActivity().onBackPressedDispatcher.onBackPressed()
    }
}