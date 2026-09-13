package com.example.stockwise.commons


/**
 * Central place for all app-wide constants.
 * Add new SharedPreferences keys here so they never collide.
 */
object AppConstants {

    // ===== SharedPreferences =====
    object Prefs {
        /** The name of the SharedPreferences file on disk. */
        const val FILE_NAME = "stockwise_prefs"

        // --- Auth keys ---
        const val KEY_USER_NAME = "user_name"
        const val KEY_USER_EMAIL = "user_email"
        const val KEY_USER_PASSWORD = "user_password"
        const val KEY_IS_LOGGED_IN = "is_logged_in"

        // --- Reserved for future use ---
        const val KEY_IS_FIRST_LAUNCH = "is_first_launch"
        const val KEY_IS_ONBOARDING_DONE = "is_onboarding_done"
        const val KEY_THEME_MODE = "theme_mode"          // "light" | "dark" | "system"
        const val KEY_LAST_LOGIN_TIME = "last_login_time"
    }

    // ===== Auth rules =====
    object Auth {
        const val MIN_NAME_LENGTH = 2
        const val MIN_PASSWORD_LENGTH = 6
    }

    // ===== Splash =====
    object Splash {
        const val HOLD_DURATION_MS = 1500L
        const val ANIM_DURATION_MS = 600L
        const val INITIAL_SCALE = 0.85f
    }
}