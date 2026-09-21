package dev.equwal.assistkey.setup

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View
import dev.equwal.assistkey.model.HwKey
import kotlin.math.abs

/**
 * A drawing of the device with its buttons along one edge.
 *
 * It is the way to choose a button: tap a button in the drawing, or swipe up and
 * down through them. The drawing is a diagram, not a photograph: it shows which
 * buttons there are, in a fixed order, and does not claim where they sit on a
 * given device. The chosen button is filled black and its name is bold.
 *
 * Drawn for e-ink: black lines on white, no shading, no animation.
 */
class DeviceView(context: Context) : View(context) {

    var keys: List<HwKey> = emptyList()
        set(value) { field = value; invalidate() }

    var selected: HwKey? = null
        set(value) { field = value; invalidate() }

    var onSelect: ((HwKey) -> Unit)? = null

    private val d = resources.displayMetrics.density
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * d
        color = Color.BLACK
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = Color.BLACK }
    private val paper = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = Color.WHITE }
    private val label = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 17f * d }
    private val rows = ArrayList<Pair<HwKey, RectF>>()

    private val gestures = GestureDetector(context, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDown(e: MotionEvent): Boolean = true

        override fun onSingleTapUp(e: MotionEvent): Boolean {
            rows.firstOrNull { it.second.contains(e.x, e.y) }?.let { choose(it.first) }
            return true
        }

        /** A swipe up or down goes to the next or the previous button. */
        override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
            if (keys.isEmpty() || abs(vy) < abs(vx)) return false
            val i = keys.indexOf(selected)
            val next = if (vy < 0) i + 1 else i - 1
            choose(keys[next.coerceIn(0, keys.size - 1)])
            return true
        }
    })

    private fun choose(key: HwKey) {
        selected = key
        onSelect?.invoke(key)
    }

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_UP) performClick()
        return gestures.onTouchEvent(event)
    }

    override fun performClick(): Boolean = super.performClick()

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        val w = MeasureSpec.getSize(widthSpec)
        setMeasuredDimension(w, (300 * d).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val body = RectF(w * 0.10f, 6 * d, w * 0.46f, h - 6 * d)
        val corner = 18 * d
        canvas.drawRoundRect(body, corner, corner, paper)
        canvas.drawRoundRect(body, corner, corner, line)
        val inset = 12 * d
        canvas.drawRect(body.left + inset, body.top + inset * 1.6f, body.right - inset, body.bottom - inset * 1.6f, line)

        rows.clear()
        if (keys.isEmpty()) return
        val slot = (body.height() - 2 * corner) / keys.size
        keys.forEachIndexed { i, key ->
            val cy = body.top + corner + slot * (i + 0.5f)
            val half = minOf(slot * 0.32f, 20 * d)
            val nub = RectF(body.right - 1 * d, cy - half, body.right + 12 * d, cy + half)
            val chosen = key == selected
            canvas.drawRoundRect(nub, 4 * d, 4 * d, if (chosen) fill else paper)
            canvas.drawRoundRect(nub, 4 * d, 4 * d, line)
            // A thin leader from the button to its name.
            canvas.drawLine(nub.right, cy, w * 0.56f, cy, line)
            label.typeface = if (chosen) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            canvas.drawText(key.label, w * 0.58f, cy + label.textSize * 0.35f, label)
            rows.add(key to RectF(body.right - 8 * d, cy - slot / 2, w, cy + slot / 2))
        }
    }
}
