package dev.equwal.assistkey.setup

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.text.TextUtils
import android.text.TextPaint
import android.view.MotionEvent
import android.view.View
import dev.equwal.assistkey.model.HwKey

/**
 * A drawing of the device with its buttons along one edge.
 *
 * It is the way to choose buttons: tap a button in the drawing to select it,
 * tap it again to let it go. Two buttons can be selected together, which makes
 * a combination. [Route.toggle] holds the rules. The buttons are drawn from the
 * top down in the order of [keys], which the device profile gives. The
 * on-screen button is drawn on the screen of the device, because that is
 * where it is. A selected button is filled black and its name is bold.
 *
 * Drawn for e-ink: black lines on white, no shading, no animation.
 */
class DeviceView(context: Context, private val heightDp: Int = 300) : View(context) {

    var keys: List<HwKey> = emptyList()
        set(value) { field = value; invalidate() }

    var selected: List<HwKey> = emptyList()
        set(value) { field = value; invalidate() }

    /** A short second line under the name of a button, for example what it does now. */
    var captions: Map<HwKey, String> = emptyMap()
        set(value) { field = value; invalidate() }

    var onSelect: ((List<HwKey>) -> Unit)? = null

    private val d = resources.displayMetrics.density
    private val line = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = 2.5f * d
        color = Color.BLACK
    }
    private val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = Color.BLACK }
    private val paper = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL; color = Color.WHITE }
    private val label = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = Color.BLACK; textSize = 17f * d }
    private val caption = TextPaint(Paint.ANTI_ALIAS_FLAG).apply { color = 0xFF444444.toInt(); textSize = 13f * d }
    private val rows = ArrayList<Pair<HwKey, RectF>>()

    override fun onTouchEvent(event: MotionEvent): Boolean {
        if (event.actionMasked == MotionEvent.ACTION_UP) {
            rows.firstOrNull { it.second.contains(event.x, event.y) }?.let { (key, _) ->
                selected = Route.toggle(selected, key)
                onSelect?.invoke(selected)
            }
            performClick()
        }
        return true
    }

    override fun performClick(): Boolean = super.performClick()

    override fun onMeasure(widthSpec: Int, heightSpec: Int) {
        val w = MeasureSpec.getSize(widthSpec)
        setMeasuredDimension(w, (heightDp * d).toInt())
    }

    override fun onDraw(canvas: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val body = RectF(w * 0.06f, 6 * d, w * 0.40f, h - 6 * d)
        val corner = 18 * d
        canvas.drawRoundRect(body, corner, corner, paper)
        canvas.drawRoundRect(body, corner, corner, line)
        val inset = 12 * d
        canvas.drawRect(body.left + inset, body.top + inset * 1.6f, body.right - inset, body.bottom - inset * 1.6f, line)

        rows.clear()
        if (keys.isEmpty()) return
        val slot = (body.height() - 2 * corner) / keys.size
        // A device with many buttons gives each name less room. Shrink the
        // name to the slot so the list still reads.
        val twoLines = captions.isNotEmpty()
        label.textSize = minOf(17f * d, slot * (if (twoLines) 0.42f else 0.7f))
        caption.textSize = minOf(13f * d, slot * 0.32f)
        val textLeft = w * 0.50f
        val textWidth = w - textLeft
        keys.forEachIndexed { i, key ->
            val cy = body.top + corner + slot * (i + 0.5f)
            val chosen = key in selected
            val leaderFrom: Float
            if (key == HwKey.SCREEN) {
                // A round button on the screen of the device.
                val r = minOf(slot * 0.30f, 13 * d)
                val cx = body.right - inset - r - 6 * d
                canvas.drawCircle(cx, cy, r, if (chosen) fill else paper)
                canvas.drawCircle(cx, cy, r, line)
                leaderFrom = cx + r
            } else {
                val half = minOf(slot * 0.32f, 20 * d)
                val nub = RectF(body.right - 1 * d, cy - half, body.right + 12 * d, cy + half)
                canvas.drawRoundRect(nub, 4 * d, 4 * d, if (chosen) fill else paper)
                canvas.drawRoundRect(nub, 4 * d, 4 * d, line)
                leaderFrom = nub.right
            }
            // A thin leader from the button to its name.
            canvas.drawLine(leaderFrom, cy, textLeft - 8 * d, cy, line)
            label.typeface = if (chosen) Typeface.DEFAULT_BOLD else Typeface.DEFAULT
            val note = captions[key]
            val base = if (note == null) cy + label.textSize * 0.35f else cy - 2 * d
            canvas.drawText(key.label, textLeft, base, label)
            if (note != null) {
                val cut = TextUtils.ellipsize(note, caption, textWidth, TextUtils.TruncateAt.END).toString()
                canvas.drawText(cut, textLeft, base + caption.textSize * 1.35f, caption)
            }
            rows.add(key to RectF(body.right - inset - 40 * d, cy - slot / 2, w, cy + slot / 2))
        }
    }
}
