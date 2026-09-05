package com.example.stockwise.commons

import android.animation.ObjectAnimator
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.FrameLayout
import androidx.annotation.LayoutRes
import com.google.android.material.bottomsheet.BottomSheetBehavior
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment

class ReusableBottomSheet : BottomSheetDialogFragment() {

    private var layoutRes: Int = 0
    private var heightPercent: Float? = null
    private var contentBinder: ((View) -> Unit)? = null
    private var shouldWrapContent: Boolean = false
    private var isDismissing = false
    private val handler = Handler(Looper.getMainLooper())

    companion object {
        private const val ARG_LAYOUT_RES = "arg_layout_res"
        private const val ARG_HEIGHT_PERCENT = "arg_height_percent"
        private const val ARG_WRAP_CONTENT = "arg_wrap_content"

        /**
         * Creates a bottom sheet that wraps its content height
         * @param layoutRes the XML layout to inflate
         */
        fun newInstanceWrapContent(@LayoutRes layoutRes: Int): ReusableBottomSheet {
            val sheet = ReusableBottomSheet()
            sheet.shouldWrapContent = true
            sheet.arguments = Bundle().apply {
                putInt(ARG_LAYOUT_RES, layoutRes)
                putBoolean(ARG_WRAP_CONTENT, true)
            }
            return sheet
        }

        /**
         * Creates a bottom sheet with a fixed percentage height
         * @param layoutRes the XML layout to inflate
         * @param heightPercent percentage of screen height (0.0 - 1.0)
         */
        fun newInstance(@LayoutRes layoutRes: Int, heightPercent: Float? = null): ReusableBottomSheet {
            val sheet = ReusableBottomSheet()
            sheet.arguments = Bundle().apply {
                putInt(ARG_LAYOUT_RES, layoutRes)
                heightPercent?.let { putFloat(ARG_HEIGHT_PERCENT, it) }
            }
            return sheet
        }
    }

    fun setContentBinder(binder: (View) -> Unit) {
        this.contentBinder = binder
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        layoutRes = arguments?.getInt(ARG_LAYOUT_RES) ?: 0
        heightPercent = if (arguments?.containsKey(ARG_HEIGHT_PERCENT) == true)
            arguments?.getFloat(ARG_HEIGHT_PERCENT) else null
        shouldWrapContent = arguments?.getBoolean(ARG_WRAP_CONTENT, false) ?: false

        val wrapper = FrameLayout(requireContext()).apply {
            layoutParams = ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT
            )
        }

        val contentView = inflater.inflate(layoutRes, wrapper, false)
        wrapper.addView(contentView)

        contentBinder?.invoke(contentView)

        return wrapper
    }

    override fun onStart() {
        super.onStart()
        val dialog = dialog as? BottomSheetDialog ?: return

        // Let the sheet resize when the keyboard opens
        dialog.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)

        val bottomSheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            ?: return
        val behavior = BottomSheetBehavior.from(bottomSheet)

        if (shouldWrapContent) {
            // Let the sheet wrap to content height
            bottomSheet.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
            bottomSheet.requestLayout()
            behavior.peekHeight = BottomSheetBehavior.PEEK_HEIGHT_AUTO
            behavior.state = BottomSheetBehavior.STATE_EXPANDED
            behavior.skipCollapsed = true
        } else {
            val percent = heightPercent
            if (percent != null) {
                // Fixed percentage of screen height
                val screenHeight = resources.displayMetrics.heightPixels
                val targetHeight = (screenHeight * percent).toInt()
                bottomSheet.layoutParams.height = targetHeight
                bottomSheet.requestLayout()
                behavior.peekHeight = targetHeight
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
            } else {
                // Default: wrap content
                bottomSheet.layoutParams.height = ViewGroup.LayoutParams.WRAP_CONTENT
                bottomSheet.requestLayout()
                behavior.peekHeight = BottomSheetBehavior.PEEK_HEIGHT_AUTO
                behavior.state = BottomSheetBehavior.STATE_EXPANDED
                behavior.skipCollapsed = true
            }
        }
    }

    override fun dismiss() {
        if (isDismissing) return
        isDismissing = true

        val dialog = dialog as? BottomSheetDialog ?: run {
            super.dismiss()
            isDismissing = false
            return
        }

        val bottomSheet = dialog.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
        if (bottomSheet != null) {
            // Animate sheet sliding down
            val slideDown = ObjectAnimator.ofFloat(
                bottomSheet,
                "translationY",
                0f,
                bottomSheet.height.toFloat() + 200f
            ).apply {
                duration = 350
                start()
            }

            // Fade out background
            dialog.window?.decorView?.let { decorView ->
                ObjectAnimator.ofFloat(decorView, "alpha", 1f, 0f).apply {
                    duration = 350
                    start()
                }
            }

            // Dismiss after animation completes
            handler.postDelayed({
                if (isAdded) {
                    super.dismiss()
                }
                isDismissing = false
            }, 400)
        } else {
            super.dismiss()
            isDismissing = false
        }
    }

    // Dismiss without animation (for immediate dismiss)
    fun dismissImmediate() {
        if (isDismissing) return
        isDismissing = true
        super.dismiss()
        isDismissing = false
    }

    override fun onDestroyView() {
        contentBinder = null
        handler.removeCallbacksAndMessages(null)
        super.onDestroyView()
    }
}