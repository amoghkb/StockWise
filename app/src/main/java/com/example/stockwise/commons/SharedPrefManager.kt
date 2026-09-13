package com.example.stockwise.commons

import android.content.Context
import androidx.core.content.edit

/**
 * Thin, typed wrapper around SharedPreferences.
 *
 * Use this for any persisted key-value data in the app.
 * Auth-specific logic lives in [AuthSessionManager], which uses this class internally.
 */
class SharedPrefManager private constructor(context: Context) {

    private val prefs = context.applicationContext
        .getSharedPreferences(AppConstants.Prefs.FILE_NAME, Context.MODE_PRIVATE)

    // ==========================================
    // WRITE
    // ==========================================

    fun putString(key: String, value: String) {
        prefs.edit { putString(key, value) }
    }

    fun putBoolean(key: String, value: Boolean) {
        prefs.edit { putBoolean(key, value) }
    }

    fun putInt(key: String, value: Int) {
        prefs.edit { putInt(key, value) }
    }

    fun putLong(key: String, value: Long) {
        prefs.edit { putLong(key, value) }
    }

    fun putFloat(key: String, value: Float) {
        prefs.edit { putFloat(key, value) }
    }

    // ==========================================
    // READ
    // ==========================================

    fun getString(key: String, default: String = ""): String =
        prefs.getString(key, default) ?: default

    fun getBoolean(key: String, default: Boolean = false): Boolean =
        prefs.getBoolean(key, default)

    fun getInt(key: String, default: Int = 0): Int =
        prefs.getInt(key, default)

    fun getLong(key: String, default: Long = 0L): Long =
        prefs.getLong(key, default)

    fun getFloat(key: String, default: Float = 0f): Float =
        prefs.getFloat(key, default)

    // ==========================================
    // UTILITY
    // ==========================================

    fun contains(key: String): Boolean = prefs.contains(key)

    fun remove(key: String) {
        prefs.edit { remove(key) }
    }

    fun clearAll() {
        prefs.edit { clear() }
    }

    // ==========================================
    // SINGLETON
    // ==========================================

    companion object {
        @Volatile
        private var instance: SharedPrefManager? = null

        fun getInstance(context: Context): SharedPrefManager {
            return instance ?: synchronized(this) {
                instance ?: SharedPrefManager(context).also { instance = it }
            }
        }
    }
}