package com.example.stockwise.ui.auth

import android.content.Intent
import android.os.Bundle
import android.view.animation.AccelerateDecelerateInterpolator
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import com.example.stockwise.MainActivity
import com.example.stockwise.auth.AuthSessionManager
import com.example.stockwise.commons.AppConstants
import com.example.stockwise.databinding.ActivitySplashBinding
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class SplashActivity : AppCompatActivity() {

    private lateinit var binding: ActivitySplashBinding
    private lateinit var session: AuthSessionManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivitySplashBinding.inflate(layoutInflater)
        setContentView(binding.root)

        session = AuthSessionManager.getInstance(this)

        // Fade + scale-in animation
        binding.logoContainer.alpha = 0f
        binding.logoContainer.scaleX = AppConstants.Splash.INITIAL_SCALE
        binding.logoContainer.scaleY = AppConstants.Splash.INITIAL_SCALE
        binding.logoContainer.animate()
            .alpha(1f)
            .scaleX(1f)
            .scaleY(1f)
            .setDuration(AppConstants.Splash.ANIM_DURATION_MS)
            .setInterpolator(AccelerateDecelerateInterpolator())
            .start()

        lifecycleScope.launch {
            delay(AppConstants.Splash.HOLD_DURATION_MS)
            val next = if (session.isLoggedIn()) {
                Intent(this@SplashActivity, MainActivity::class.java)
            } else {
                Intent(this@SplashActivity, AuthActivity::class.java)
            }
            startActivity(next)
            finish()
        }
    }
}