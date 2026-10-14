package com.focusblock.service

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.PixelFormat
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.MotionEvent
import android.view.ViewConfiguration
import android.view.WindowManager
import android.widget.TextView
import com.focusblock.domain.model.TimeLeft
import com.focusblock.util.formatCountdown
import kotlin.math.abs

/**
 * Small floating pill on the right edge showing time left. Drag it up/down; tap to
 * show the app name too. A plain View, not Compose: Compose in a WindowManager
 * overlay needs lifecycle/saved-state owners set up by hand.
 */
class TimerOverlay(private val context: Context) {

    private val windowManager = context.getSystemService(WindowManager::class.java)
    private var view: TextView? = null
    private var expanded = false

    private val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        // Never takes keyboard focus, touches outside go to the app underneath
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.END or Gravity.TOP
        y = dp(160)
    }

    val isShowing get() = view != null

    fun update(appLabel: String, kind: TimeLeft.Kind, remainingMs: Long) {
        val v = view ?: createView().also { view = it }
        val time = formatCountdown(remainingMs)
        v.text = when {
            !expanded -> time
            kind == TimeLeft.Kind.LIMIT -> "$appLabel · $time left today"
            else -> "$appLabel · blocked in $time"
        }
    }

    fun hide() {
        view?.let { windowManager.removeView(it) }
        view = null
        expanded = false
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createView(): TextView {
        val v = TextView(context).apply {
            setTextColor(0xFFFFFFFF.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            setPadding(dp(12), dp(6), dp(12), dp(6))
            background = GradientDrawable().apply {
                cornerRadius = dp(16).toFloat()
                setColor(0xCC202124.toInt()) // dark, slightly see-through
            }
        }
        val slop = ViewConfiguration.get(context).scaledTouchSlop
        var downY = 0f
        var startY = 0
        var dragging = false
        v.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    downY = event.rawY
                    startY = params.y
                    dragging = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dy = event.rawY - downY
                    if (abs(dy) > slop) dragging = true
                    if (dragging) {
                        params.y = (startY + dy).toInt().coerceAtLeast(0)
                        windowManager.updateViewLayout(v, params)
                    }
                }
                MotionEvent.ACTION_UP -> if (!dragging) expanded = !expanded
            }
            true
        }
        windowManager.addView(v, params)
        return v
    }

    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()
}
