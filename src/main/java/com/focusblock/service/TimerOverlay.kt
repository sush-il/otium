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
 * A tiny handle pinned to the middle of the LEFT edge while a limited app is open.
 * It shows no number by default — just a small arrow. Tap it to reveal the time left;
 * tap again to hide it. Drag up/down to reposition. A plain View, not Compose, because
 * a WindowManager overlay would need lifecycle/saved-state owners wired up by hand.
 */
class TimerOverlay(private val context: Context) {

    private val windowManager = context.getSystemService(WindowManager::class.java)
    private var view: TextView? = null
    private var expanded = false

    // Latest values, so a tap can redraw without waiting for the next tick
    private var lastLabel = ""
    private var lastKind = TimeLeft.Kind.LIMIT
    private var lastRemaining = 0L

    private val params = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        // Never takes keyboard focus, touches outside go to the app underneath
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
        PixelFormat.TRANSLUCENT,
    ).apply {
        gravity = Gravity.START or Gravity.CENTER_VERTICAL
    }

    val isShowing get() = view != null

    fun update(appLabel: String, kind: TimeLeft.Kind, remainingMs: Long) {
        lastLabel = appLabel
        lastKind = kind
        lastRemaining = remainingMs
        render()
    }

    fun hide() {
        view?.let { windowManager.removeView(it) }
        view = null
        expanded = false
    }

    private fun render() {
        val v = view ?: createView().also { view = it }
        if (!expanded) {
            // Collapsed: a bigger arrow that's easy to see and tap
            v.text = "›"
            v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            v.setPadding(dp(12), dp(16), dp(14), dp(16))
        } else {
            // Expanded: just the time (you're already in the app, so no name needed)
            v.text = "${formatCountdown(lastRemaining)} ‹"
            v.setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            v.setPadding(dp(14), dp(12), dp(14), dp(12))
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun createView(): TextView {
        val v = TextView(context).apply {
            setTextColor(0xFFFFFFFF.toInt())
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            background = GradientDrawable().apply {
                // Rounded on the right only, so it reads as a tab coming from the left edge
                val r = dp(22).toFloat()
                cornerRadii = floatArrayOf(0f, 0f, r, r, r, r, 0f, 0f)
                setColor(0xE6202124.toInt())
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
                        // y is an offset from the vertical centre, +/- either way
                        params.y = (startY + dy).toInt()
                        windowManager.updateViewLayout(v, params)
                    }
                }
                MotionEvent.ACTION_UP -> if (!dragging) {
                    expanded = !expanded
                    render()
                }
            }
            true
        }
        windowManager.addView(v, params)
        return v
    }

    private fun dp(value: Int) = (value * context.resources.displayMetrics.density).toInt()
}
