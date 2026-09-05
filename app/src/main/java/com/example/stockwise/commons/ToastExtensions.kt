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
import android.widget.Toast
import androidx.core.content.ContextCompat
import com.example.stockwise.R
import com.google.android.material.card.MaterialCardView

// Main extension functions for String
fun String.toastSuccess(context: Context, duration: Int = Toast.LENGTH_SHORT) {
    context.showCustomToast(this, ToastType.SUCCESS, duration)
}

fun String.toastError(context: Context, duration: Int = Toast.LENGTH_SHORT) {
    context.showCustomToast(this, ToastType.ERROR, duration)
}

fun String.toastInfo(context: Context, duration: Int = Toast.LENGTH_SHORT) {
    context.showCustomToast(this, ToastType.INFO, duration)
}

// Extension function for Context
fun Context.showCustomToast(message: String, type: ToastType, duration: Int = Toast.LENGTH_SHORT) {
    CustomToastManager.show(this, message, type, duration)
}

// Toast Types
enum class ToastType {
    SUCCESS,
    ERROR,
    INFO
}

object CustomToastManager {
    private var currentToast: Toast? = null
    private var currentView: View? = null
    private val handler = Handler(Looper.getMainLooper())
    private var dismissRunnable: Runnable? = null
    private const val TAG = "CustomToast"

    fun show(context: Context, message: String, type: ToastType, duration: Int = Toast.LENGTH_SHORT) {
        try {
            // Cancel existing toast
            dismiss()

            // Inflate custom layout
            val inflater = LayoutInflater.from(context)
            val customView = inflater.inflate(R.layout.custom_toast, null)
            currentView = customView

            // Get views
            val cardView = customView.findViewById<MaterialCardView>(R.id.toastCard)
            val iconView = customView.findViewById<ImageView>(R.id.toastIcon)
            val messageView = customView.findViewById<TextView>(R.id.toastMessage)
            val container = customView.findViewById<View>(R.id.toastContainer)

            // Set message
            messageView.text = message

            // Set style based on type
            when (type) {
                ToastType.SUCCESS -> {
                    iconView.setImageResource(R.drawable.ic_correct)
                    cardView.setCardBackgroundColor(ContextCompat.getColor(context, R.color.toast_success_bg))
                    iconView.setColorFilter(ContextCompat.getColor(context, R.color.white))
                    messageView.setTextColor(ContextCompat.getColor(context, R.color.white))
                    cardView.strokeColor = ContextCompat.getColor(context, R.color.toast_success_border)
                    cardView.strokeWidth = 2
                }
                ToastType.ERROR -> {
                    iconView.setImageResource(R.drawable.ic_error)
                    cardView.setCardBackgroundColor(ContextCompat.getColor(context, R.color.toast_error_bg))
                    iconView.setColorFilter(ContextCompat.getColor(context, R.color.white))
                    messageView.setTextColor(ContextCompat.getColor(context, R.color.white))
                    cardView.strokeColor = ContextCompat.getColor(context, R.color.toast_error_border)
                    cardView.strokeWidth = 2
                }
                ToastType.INFO -> {
                    iconView.setImageResource(R.drawable.ic_info)
                    cardView.setCardBackgroundColor(ContextCompat.getColor(context, R.color.toast_info_bg))
                    iconView.setColorFilter(ContextCompat.getColor(context, R.color.white))
                    messageView.setTextColor(ContextCompat.getColor(context, R.color.white))
                    cardView.strokeColor = ContextCompat.getColor(context, R.color.toast_info_border)
                    cardView.strokeWidth = 2
                }
            }

            // Make container clickable to dismiss
            container.setOnClickListener {
                dismiss()
            }

            // Create and setup toast
            val toast = Toast(context)
            toast.setGravity(Gravity.TOP or Gravity.CENTER_HORIZONTAL, 0, 50)
            toast.setView(customView)
            currentToast = toast

            // Show toast
            currentToast?.show()

            Log.d(TAG, "Toast shown: $message (Type: $type)")

            // Auto dismiss
            val toastDuration = when (duration) {
                Toast.LENGTH_SHORT -> 2000L
                Toast.LENGTH_LONG -> 3500L
                else -> duration.toLong()
            }

            dismissRunnable = Runnable {
                dismiss()
            }
            handler.postDelayed(dismissRunnable!!, toastDuration)

        } catch (e: Exception) {
            Log.e(TAG, "Error showing custom toast", e)
            // Fallback to default toast
            Toast.makeText(context, message, Toast.LENGTH_SHORT).show()
        }
    }

    private fun dismiss() {
        dismissRunnable?.let {
            handler.removeCallbacks(it)
            dismissRunnable = null
        }
        currentToast?.cancel()
        currentToast = null
        currentView = null
    }
}