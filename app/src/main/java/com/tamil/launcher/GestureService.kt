package com.tamil.launcher

import android.accessibilityservice.AccessibilityService
import android.annotation.SuppressLint
import android.content.SharedPreferences
import android.graphics.PixelFormat
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.view.accessibility.AccessibilityEvent

/** Edge-swipe navigation: left/right edge = Back, swipe up from bottom = Home, swipe up and hold = Recents. */
class GestureService : AccessibilityService() {
    private enum class Mode { LEFT, RIGHT, BOTTOM }

    private val views = mutableListOf<View>()
    private val handler = Handler(Looper.getMainLooper())
    private val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key -> if (key == "gestureNav") refresh() }

    override fun onServiceConnected() {
        getSharedPreferences("launcher", MODE_PRIVATE).registerOnSharedPreferenceChangeListener(listener)
        refresh()
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onDestroy() {
        remove()
        getSharedPreferences("launcher", MODE_PRIVATE).unregisterOnSharedPreferenceChangeListener(listener)
        super.onDestroy()
    }

    private fun remove() {
        val wm = getSystemService(WINDOW_SERVICE) as WindowManager
        views.forEach { try { wm.removeView(it) } catch (_: Exception) {} }
        views.clear()
    }

    private fun refresh() {
        remove()
        if (!getSharedPreferences("launcher", MODE_PRIVATE).getBoolean("gestureNav", true)) return
        val m = resources.displayMetrics
        val edge = (22 * m.density).toInt()
        val tall = (m.heightPixels * 0.7f).toInt()
        add(edge, tall, Gravity.START or Gravity.CENTER_VERTICAL, Mode.LEFT)
        add(edge, tall, Gravity.END or Gravity.CENTER_VERTICAL, Mode.RIGHT)
        add(WindowManager.LayoutParams.MATCH_PARENT, edge, Gravity.BOTTOM, Mode.BOTTOM)
    }

    @SuppressLint("ClickableViewAccessibility")
    private fun add(w: Int, h: Int, grav: Int, mode: Mode) {
        val v = View(this)
        val th = 40 * resources.displayMetrics.density
        var sx = 0f
        var sy = 0f
        var done = false
        var armed = false
        val recents = Runnable { if (!done) { done = true; fire(v, GLOBAL_ACTION_RECENTS) } }
        v.setOnTouchListener { _, e ->
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> { sx = e.rawX; sy = e.rawY; done = false; armed = false }
                MotionEvent.ACTION_MOVE -> if (!done) {
                    val dx = e.rawX - sx
                    val dy = e.rawY - sy
                    when (mode) {
                        Mode.LEFT -> if (dx > th) { done = true; fire(v, GLOBAL_ACTION_BACK) }
                        Mode.RIGHT -> if (-dx > th) { done = true; fire(v, GLOBAL_ACTION_BACK) }
                        Mode.BOTTOM -> if (-dy > th && !armed) { armed = true; handler.postDelayed(recents, 350) }
                    }
                }
                MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                    handler.removeCallbacks(recents)
                    if (armed && !done && e.actionMasked == MotionEvent.ACTION_UP) { done = true; fire(v, GLOBAL_ACTION_HOME) }
                }
            }
            true
        }
        val lp = WindowManager.LayoutParams(
            w, h, WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply { gravity = grav }
        try {
            (getSystemService(WINDOW_SERVICE) as WindowManager).addView(v, lp)
            views += v
        } catch (_: Exception) {
        }
    }

    private fun fire(v: View, action: Int) {
        v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY)
        performGlobalAction(action)
    }
}
