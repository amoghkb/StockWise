package com.example.stockwise.auth

import android.content.Context
import com.example.stockwise.commons.AppConstants
import com.example.stockwise.commons.SharedPrefManager

/**
 * Handles all authentication-session state.
 *
 * Uses [SharedPrefManager] for persistence, and [AppConstants.Prefs] for key names.
 */
class AuthSessionManager private constructor(context: Context) {

    private val prefs = SharedPrefManager.getInstance(context)

    // ==========================================
    // WRITE
    // ==========================================

    /**
     * Persists the user's account AND marks them as logged in.
     * Call this after a successful sign-up or login.
     */
    fun saveSession(name: String, email: String, password: String) {
        prefs.putString(AppConstants.Prefs.KEY_USER_NAME, name)
        prefs.putString(AppConstants.Prefs.KEY_USER_EMAIL, email)
        prefs.putString(AppConstants.Prefs.KEY_USER_PASSWORD, password)
        prefs.putBoolean(AppConstants.Prefs.KEY_IS_LOGGED_IN, true)
        prefs.putLong(AppConstants.Prefs.KEY_LAST_LOGIN_TIME, System.currentTimeMillis())
    }

    /**
     * Updates only the name and email. Leaves the password untouched.
     * Call this from the profile edit dialog.
     */
    fun updateProfile(newName: String, newEmail: String) {
        prefs.putString(AppConstants.Prefs.KEY_USER_NAME, newName)
        prefs.putString(AppConstants.Prefs.KEY_USER_EMAIL, newEmail)
    }

    /**
     * Marks the user as logged out.
     * Keeps name/email/password so the account still exists and the email pre-fills.
     */
    fun logout() {
        prefs.putBoolean(AppConstants.Prefs.KEY_IS_LOGGED_IN, false)
    }

    /**
     * Completely wipes the account (name, email, password, session).
     * Use for a "Delete Account" feature — never for a normal logout.
     */
    fun clearAccount() {
        prefs.remove(AppConstants.Prefs.KEY_USER_NAME)
        prefs.remove(AppConstants.Prefs.KEY_USER_EMAIL)
        prefs.remove(AppConstants.Prefs.KEY_USER_PASSWORD)
        prefs.putBoolean(AppConstants.Prefs.KEY_IS_LOGGED_IN, false)
    }

    // ==========================================
    // READ
    // ==========================================

    fun isLoggedIn(): Boolean =
        prefs.getBoolean(AppConstants.Prefs.KEY_IS_LOGGED_IN, false)

    fun getUserName(): String =
        prefs.getString(AppConstants.Prefs.KEY_USER_NAME)

    fun getUserEmail(): String =
        prefs.getString(AppConstants.Prefs.KEY_USER_EMAIL)

    fun getLastLoginTime(): Long =
        prefs.getLong(AppConstants.Prefs.KEY_LAST_LOGIN_TIME)

    /**
     * True if an account has ever been created (i.e., an email is stored).
     * Independent of the current login state.
     */
    fun hasAccount(): Boolean =
        getUserEmail().isNotBlank()

    /**
     * Case-insensitive email check — returns true if the given email is the saved one.
     */
    fun accountExists(email: String): Boolean {
        val saved = prefs.getString(AppConstants.Prefs.KEY_USER_EMAIL, "")
        return saved.isNotBlank() && saved.equals(email, ignoreCase = true)
    }

    /**
     * True if email AND password match what's saved.
     * Email is case-insensitive; password is exact.
     */
    fun credentialsMatch(email: String, password: String): Boolean {
        val savedEmail = prefs.getString(AppConstants.Prefs.KEY_USER_EMAIL, "")
        val savedPassword = prefs.getString(AppConstants.Prefs.KEY_USER_PASSWORD, "")
        if (savedEmail.isBlank() || savedPassword.isBlank()) return false
        return savedEmail.equals(email, ignoreCase = true) && savedPassword == password
    }

    // ==========================================
    // SINGLETON
    // ==========================================

    companion object {
        @Volatile
        private var instance: AuthSessionManager? = null

        fun getInstance(context: Context): AuthSessionManager {
            return instance ?: synchronized(this) {
                instance ?: AuthSessionManager(context).also { instance = it }
            }
        }
    }
}