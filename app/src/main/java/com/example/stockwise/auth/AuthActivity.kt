package com.example.stockwise.ui.auth

import android.content.Intent
import android.os.Bundle
import android.text.method.HideReturnsTransformationMethod
import android.text.method.PasswordTransformationMethod
import android.util.Patterns
import android.view.View
import androidx.appcompat.app.AppCompatActivity
import com.example.stockwise.MainActivity
import com.example.stockwise.R
import com.example.stockwise.auth.AuthSessionManager
import com.example.stockwise.commons.AppConstants
import com.example.stockwise.commons.toastError
import com.example.stockwise.commons.toastSuccess
import com.example.stockwise.databinding.ActivityAuthBinding

class AuthActivity : AppCompatActivity() {

    private lateinit var binding: ActivityAuthBinding
    private lateinit var session: AuthSessionManager

    /** True = Sign Up mode, False = Login mode */
    private var isSignUpMode = false

    private var passwordVisible = false
    private var confirmVisible = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityAuthBinding.inflate(layoutInflater)
        setContentView(binding.root)

        session = AuthSessionManager.getInstance(this)

        // Pre-fill saved email in login mode
        val savedEmail = session.getUserEmail()
        if (savedEmail.isNotEmpty()) binding.etEmail.setText(savedEmail)

        setupPasswordToggles()
        applyMode(isSignUpMode)

        binding.btnPrimary.setOnClickListener {
            if (isSignUpMode) attemptSignUp() else attemptLogin()
        }

        binding.tvSwitchAction.setOnClickListener {
            applyMode(!isSignUpMode)
        }
    }

    // ==========================================
    // MODE TOGGLE
    // ==========================================

    private fun applyMode(signUp: Boolean) {
        isSignUpMode = signUp

        if (signUp) {
            binding.tvTitle.text = "Create Account"
            binding.tvSubtitle.text = "Join StockWise and manage your inventory"
            binding.btnPrimary.text = "Create Account"
            binding.tvSwitchPrompt.text = "Already have an account? "
            binding.tvSwitchAction.text = "Sign In"
            binding.nameSection.visibility = View.VISIBLE
            binding.confirmSection.visibility = View.VISIBLE
        } else {
            binding.tvTitle.text = "Welcome Back"
            binding.tvSubtitle.text = "Sign in to continue to StockWise"
            binding.btnPrimary.text = "Sign In"
            binding.tvSwitchPrompt.text = "Don't have an account? "
            binding.tvSwitchAction.text = "Sign Up"
            binding.nameSection.visibility = View.GONE
            binding.confirmSection.visibility = View.GONE

            binding.etName.text?.clear()
            binding.etConfirmPassword.text?.clear()
        }
    }

    // ==========================================
    // PASSWORD TOGGLES
    // ==========================================

    private fun setupPasswordToggles() {
        binding.ivTogglePassword.setOnClickListener {
            passwordVisible = !passwordVisible
            binding.etPassword.transformationMethod =
                if (passwordVisible) HideReturnsTransformationMethod.getInstance()
                else PasswordTransformationMethod.getInstance()
            binding.etPassword.setSelection(binding.etPassword.text.length)
            binding.ivTogglePassword.setImageResource(
                if (passwordVisible) R.drawable.ic_eye_off else R.drawable.ic_eye
            )
        }

        binding.ivToggleConfirmPassword.setOnClickListener {
            confirmVisible = !confirmVisible
            binding.etConfirmPassword.transformationMethod =
                if (confirmVisible) HideReturnsTransformationMethod.getInstance()
                else PasswordTransformationMethod.getInstance()
            binding.etConfirmPassword.setSelection(binding.etConfirmPassword.text.length)
            binding.ivToggleConfirmPassword.setImageResource(
                if (confirmVisible) R.drawable.ic_eye_off else R.drawable.ic_eye
            )
        }
    }

    // ==========================================
    // LOGIN
    // ==========================================

    private fun attemptLogin() {
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString()

        when {
            email.isEmpty() -> { "Please enter your email".toastError(this); return }
            !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> {
                "Please enter a valid email".toastError(this); return
            }
            password.isEmpty() -> { "Please enter your password".toastError(this); return }
            password.length < AppConstants.Auth.MIN_PASSWORD_LENGTH -> {
                "Password must be at least ${AppConstants.Auth.MIN_PASSWORD_LENGTH} characters"
                    .toastError(this); return
            }
        }

        if (!session.accountExists(email)) {
            "No account found. Please sign up first.".toastError(this)
            return
        }

        if (!session.credentialsMatch(email, password)) {
            "Incorrect email or password".toastError(this)
            return
        }

        session.saveSession(
            name = session.getUserName(),
            email = email,
            password = password
        )

        "Welcome back!".toastSuccess(this)
        goToMain()
    }

    // ==========================================
    // SIGN UP
    // ==========================================

    private fun attemptSignUp() {
        val name = binding.etName.text.toString().trim()
        val email = binding.etEmail.text.toString().trim()
        val password = binding.etPassword.text.toString()
        val confirmPassword = binding.etConfirmPassword.text.toString()

        when {
            name.isEmpty() -> { "Please enter your name".toastError(this); return }
            name.length < AppConstants.Auth.MIN_NAME_LENGTH -> {
                "Name must be at least ${AppConstants.Auth.MIN_NAME_LENGTH} characters"
                    .toastError(this); return
            }
            email.isEmpty() -> { "Please enter your email".toastError(this); return }
            !Patterns.EMAIL_ADDRESS.matcher(email).matches() -> {
                "Please enter a valid email".toastError(this); return
            }
            password.isEmpty() -> { "Please enter a password".toastError(this); return }
            password.length < AppConstants.Auth.MIN_PASSWORD_LENGTH -> {
                "Password must be at least ${AppConstants.Auth.MIN_PASSWORD_LENGTH} characters"
                    .toastError(this); return
            }
            confirmPassword.isEmpty() -> { "Please confirm your password".toastError(this); return }
            password != confirmPassword -> { "Passwords do not match".toastError(this); return }
            session.accountExists(email) -> {
                "An account with this email already exists".toastError(this); return
            }
        }

        session.saveSession(name = name, email = email, password = password)

        "Account created. Welcome!".toastSuccess(this)
        goToMain()
    }

    private fun goToMain() {
        startActivity(Intent(this, MainActivity::class.java))
        finishAffinity()
    }
}