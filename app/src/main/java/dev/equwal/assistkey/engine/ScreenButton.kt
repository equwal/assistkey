package dev.equwal.assistkey.engine

import android.accessibilityservice.AccessibilityService
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewConfiguration
import android.view.WindowManager
import kotlin.math.abs

/**
 * A small round button that floats above every app.
 *
 * The key filter draws it in an accessibility overlay window, which needs no
 * permission of its own. It is on the screen only while a binding for it
 * exists, so a user who does not want it never sees it. A tap runs the
 * binding. A drag moves the button, and the place is kept.
 *
 * Android has its own accessibility button. It is not used: Android shows that
 * button to every user as soon as a service asks for it, bound or not.
 *
 * Drawn for e-ink: a white disc with a black ring, no shadow, no animation.
 */
class ScreenButton(private val service: AccessibilityService, private val onTap: () -> Unit) {

    private val wm = service.getSystemService(WindowManager::class.java)
    private val prefs = service.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private val d = service.resources.displayMetrics.density
    private val size = (48 * d).toInt()
    private var view: View? = null

    private val params = WindowManager.LayoutParams(
        size, size,
        WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
        PixelFormat.TRANSLUCENT
    ).apply { gravity = Gravity.TOP or Gravity.START }

    val showing: Boolean get() = view != null

    fun show() {
        if (view != null) return
        val screen = service.resources.displayMetrics
        params.x = prefs.getInt(K_X, screen.widthPixels - size - (12 * d).toInt())
        params.y = prefs.getInt(K_Y, screen.heightPixels * 2 / 3)
        val v = Disc(service)
        runCatching { wm.addView(v, params) }.onSuccess { view = v }
    }

    fun hide() {
        view?.let { runCatching { wm.removeView(it) } }
        view = null
    }

    private inner class Disc(c: Context) : View(c) {
        private val ring = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE; strokeWidth = 3 * d; color = Color.BLACK
        }
        private val paper = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = Color.WHITE }
        private val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = Color.BLACK }
        private val slop = ViewConfiguration.get(c).scaledTouchSlop
        private var downX = 0f
        private var downY = 0f
        private var startX = 0
        private var startY = 0
        private var dragged = false

        override fun onDraw(canvas: Canvas) {
            val r = width / 2f
            canvas.drawCircle(r, r, r - 2 * d, paper)
            canvas.drawCircle(r, r, r - 3 * d, ring)
            canvas.drawCircle(r, r, r * 0.30f, dot)
        }

        override fun onTouchEvent(e: MotionEvent): Boolean {
            when (e.actionMasked) {
                MotionEvent.ACTION_DOWN -> {
                    downX = e.rawX; downY = e.rawY
                    startX = params.x; startY = params.y
                    dragged = false
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = e.rawX - downX
                    val dy = e.rawY - downY
                    if (!dragged && (abs(dx) > slop || abs(dy) > slop)) dragged = true
                    if (dragged) {
                        params.x = startX + dx.toInt()
                        params.y = startY + dy.toInt()
                        runCatching { wm.updateViewLayout(this, params) }
                    }
                }
                MotionEvent.ACTION_UP -> {
                    if (dragged) {
                        prefs.edit().putInt(K_X, params.x).putInt(K_Y, params.y).apply()
                    } else {
                        performClick()
                    }
                }
            }
            return true
        }

        override fun performClick(): Boolean {
            super.performClick()
            onTap()
            return true
        }
    }

    private companion object {
        const val PREFS = "assistkey_screen_button"
        const val K_X = "x"
        const val K_Y = "y"
    }
}
