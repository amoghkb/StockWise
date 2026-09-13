package com.example.stockwise.commons

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.stockwise.R
import com.google.android.material.card.MaterialCardView

// Main extension functions for String
fun String.toastSuccess(context: Context, duration: Int = android.widget.Toast.LENGTH_SHORT) {
    context.showCustomToast(this, ToastType.SUCCESS, duration)
}

fun String.toastError(context: Context, duration: Int = android.widget.Toast.LENGTH_SHORT) {
    context.showCustomToast(this, ToastType.ERROR, duration)
}

fun String.toastInfo(context: Context, duration: Int = android.widget.Toast.LENGTH_SHORT) {
    context.showCustomToast(this, ToastType.INFO, duration)
}

// Extension function for Context
fun Context.showCustomToast(message: String, type: ToastType, duration: Int = android.widget.Toast.LENGTH_SHORT) {
    CustomToastManager.show(this, message, type, duration)
}

// Toast Types
enum class ToastType {
    SUCCESS,
    ERROR,
    INFO
}

object CustomToastManager {
    private var currentToast: android.widget.Toast? = null
    private val handler = Handler(Looper.getMainLooper())
    private var dismissRunnable: Runnable? = null
    private const val TAG = "CustomToast"

    fun show(context: Context, message: String, type: ToastType, duration: Int = android.widget.Toast.LENGTH_SHORT) {
        try {
            // Cancel existing toast
            dismiss()

            // Inflate custom layout
            val inflater = LayoutInflater.from(context)
            val customView = inflater.inflate(R.layout.custom_toast, null)

            // Get views
            val cardView = customView.findViewById<MaterialCardView>(R.id.toastCard)
            val iconView = customView.findViewById<ImageView>(R.id.toastIcon)
            val messageView = customView.findViewById<TextView>(R.id.toastMessage)

            // Set message
            messageView.text = message

            // Set white background and black text for all types
            cardView.setCardBackgroundColor(ContextCompat.getColor(context, R.color.white))
            messageView.setTextColor(ContextCompat.getColor(context, R.color.black))

            // Set proper elevation and shadow
            cardView.elevation = 8f
            cardView.cardElevation = 8f
            cardView.strokeWidth = 2
            cardView.useCompatPadding = true
            cardView.isClickable = true

            // Set style based on type
            when (type) {
                ToastType.SUCCESS -> {
                    iconView.setImageResource(R.drawable.ic_correct)
                    iconView.setColorFilter(ContextCompat.getColor(context, R.color.toast_success_border))
                    cardView.strokeColor = ContextCompat.getColor(context, R.color.toast_success_border)
                }
                ToastType.ERROR -> {
                    iconView.setImageResource(R.drawable.ic_error)
                    iconView.setColorFilter(ContextCompat.getColor(context, R.color.toast_error_border))
                    cardView.strokeColor = ContextCompat.getColor(context, R.color.toast_error_border)
                }
                ToastType.INFO -> {
                    iconView.setImageResource(R.drawable.ic_info)
                    iconView.setColorFilter(ContextCompat.getColor(context, R.color.toast_info_border))
                    cardView.strokeColor = ContextCompat.getColor(context, R.color.toast_info_border)
                }
            }

            // Make container clickable to dismiss
            customView.setOnClickListener {
                dismiss()
            }

            // Create toast with better position
            val toast = android.widget.Toast(context)
            // Position at TOP with small offset from status bar
            toast.setGravity(Gravity.TOP or Gravity.CENTER_HORIZONTAL, 0, 80)  // Changed from 100 to 80
            toast.setView(customView)
            toast.duration = android.widget.Toast.LENGTH_LONG
            currentToast = toast

            // Show toast
            currentToast?.show()

            Log.d(TAG, "Toast shown: $message (Type: $type)")

            // Auto dismiss after duration
            val toastDuration = when (duration) {
                android.widget.Toast.LENGTH_SHORT -> 2000L
                android.widget.Toast.LENGTH_LONG -> 3500L
                else -> duration.toLong()
            }

            dismissRunnable = Runnable {
                dismiss()
            }
            handler.postDelayed(dismissRunnable!!, toastDuration)

        } catch (e: Exception) {
            Log.e(TAG, "Error showing custom toast", e)
            // Fallback to default toast
            android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_SHORT).show()
        }
    }

    private fun dismiss() {
        dismissRunnable?.let {
            handler.removeCallbacks(it)
            dismissRunnable = null
        }
        currentToast?.cancel()
        currentToast = null
    }
}