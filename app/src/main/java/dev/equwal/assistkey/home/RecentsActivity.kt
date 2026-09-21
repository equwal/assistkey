package dev.equwal.assistkey.home

import android.app.Activity
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.provider.Settings
import android.util.TypedValue
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowInsets
import android.widget.HorizontalScrollView
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import dev.equwal.assistkey.shell.Shell
import dev.equwal.assistkey.ui.ShellActivity
import dev.equwal.assistkey.ui.Ui
import dev.equwal.assistkey.ui.Ui.button
import dev.equwal.assistkey.ui.Ui.dp
import dev.equwal.assistkey.ui.Ui.note
import dev.equwal.assistkey.ui.Ui.title
import kotlin.math.abs

/**
 * Recent apps, as a row of cards.
 *
 * It works like the Android switcher: swipe sideways through the cards, tap a
 * card to go to that app, swipe a card up to close it. It is drawn for e-ink:
 * black outlines on white, and the row jumps from card to card with no
 * animation.
 *
 * Android does not tell apps what is running. With shell access the cards are
 * the real tasks of the system, a card can be closed, and where the shell is
 * root each card shows the picture the system keeps of the task. Without shell
 * access the order comes from the usage log and a card shows the app icon.
 *
 * Bind it to a key from any action list: AssistKey > Recent apps.
 */
class RecentsActivity : Activity() {

    private var row: LinearLayout? = null
    private var scroller: HorizontalScrollView? = null
    private var cardStep = 0

    override fun onResume() {
        super.onResume()
        load()
    }

    private fun load() {
        Apps.recentFromShell(this) { fromShell ->
            if (isFinishing) return@recentFromShell
            when {
                fromShell != null -> show(fromShell, closable = true)
                Apps.hasUsageAccess(this) ->
                    show(Apps.recentFromUsage(this).map { Apps.Recent(it, null) }, closable = false)
                else -> askForAccess()
            }
        }
    }

    // ---- the cards -------------------------------------------------------------------------

    private fun show(recents: List<Apps.Recent>, closable: Boolean) {
        val screenW = resources.displayMetrics.widthPixels
        val screenH = resources.displayMetrics.heightPixels
        val cardW = (screenW * 0.72f).toInt()
        val cardH = (screenH * 0.62f).toInt()
        val gap = dp(16)
        cardStep = cardW + gap

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            setOnApplyWindowInsetsListener { v, insets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars())
                v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                insets
            }
        }
        root.addView(TextView(this).apply {
            text = if (recents.isEmpty()) "Nothing recent" else "Recent apps"
            setTextColor(Color.BLACK)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            typeface = Typeface.DEFAULT_BOLD
            setPadding(dp(20), dp(16), dp(20), dp(12))
        })

        val cards = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            // Side padding puts the first and the last card in the middle too.
            val side = (screenW - cardW) / 2
            setPadding(side, 0, side, 0)
        }
        recents.forEach { r ->
            cards.addView(
                card(r, closable),
                LinearLayout.LayoutParams(cardW, cardH).apply { marginEnd = gap }
            )
        }
        row = cards

        val scroll = object : HorizontalScrollView(this) {
            override fun onTouchEvent(ev: MotionEvent): Boolean {
                val handled = super.onTouchEvent(ev)
                if (ev.actionMasked == MotionEvent.ACTION_UP || ev.actionMasked == MotionEvent.ACTION_CANCEL) snap()
                return handled
            }

            /** No fling and no glide: e-ink shows every frame of one as a smear. */
            override fun fling(velocityX: Int) = Unit
        }.apply {
            isHorizontalScrollBarEnabled = false
            overScrollMode = View.OVER_SCROLL_NEVER
            addView(cards, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT))
        }
        scroller = scroll
        root.addView(scroll, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        root.addView(TextView(this).apply {
            text = if (closable) "Tap a card to open it. Swipe a card up to close it."
            else "Tap a card to open it."
            setTextColor(Color.rgb(70, 70, 70))
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(20), dp(14), dp(20), dp(6))
        })
        if (closable && recents.isNotEmpty()) {
            root.addView(
                android.widget.Button(this).apply {
                    text = "Close all"
                    setOnClickListener {
                        Shell.runAll(recents.mapNotNull { it.taskId }.map { "am stack remove $it" }) { load() }
                    }
                },
                LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply { gravity = Gravity.CENTER_HORIZONTAL }
            )
        }
        setContentView(root)
    }

    /** Puts the nearest card in the middle, in one step. */
    private fun snap() {
        val s = scroller ?: return
        if (cardStep <= 0) return
        val index = ((s.scrollX + cardStep / 2) / cardStep).coerceIn(0, (row?.childCount ?: 1) - 1)
        s.scrollTo(index * cardStep, 0)
    }

    private fun card(r: Apps.Recent, closable: Boolean): View {
        val box = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                setStroke(dp(2), Color.BLACK)
                cornerRadius = dp(14).toFloat()
            }
            val p = dp(3)
            setPadding(p, p, p, p)
            clipToOutline = true
        }

        val icon = runCatching { packageManager.getApplicationIcon(r.app.pkg) }.getOrNull()
        box.addView(LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(dp(12), dp(10), dp(12), dp(10))
            addView(ImageView(context).apply { setImageDrawable(icon) }, LinearLayout.LayoutParams(dp(32), dp(32)))
            addView(TextView(context).apply {
                text = r.app.label
                setTextColor(Color.BLACK)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
                setSingleLine()
                setPadding(dp(10), 0, 0, 0)
            })
        })
        box.addView(View(this).apply { setBackgroundColor(Color.BLACK) }, LinearLayout.LayoutParams(MATCH_PARENT, dp(1)))

        // The body: the app icon now, the task picture when and if it arrives.
        val body = ImageView(this).apply {
            setImageDrawable(icon)
            scaleType = ImageView.ScaleType.CENTER_INSIDE
            val p = dp(64)
            setPadding(p, p, p, p)
        }
        box.addView(body, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))
        r.taskId?.let { id ->
            Apps.snapshot(id) { picture ->
                if (picture != null && !isFinishing) {
                    body.setPadding(0, 0, 0, 0)
                    body.scaleType = ImageView.ScaleType.FIT_START
                    body.setImageBitmap(picture)
                }
            }
        }

        val gestures = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onSingleTapUp(e: MotionEvent): Boolean {
                Apps.launch(this@RecentsActivity, r.app)
                finish()
                return true
            }

            override fun onFling(e1: MotionEvent?, e2: MotionEvent, vx: Float, vy: Float): Boolean {
                val up = e1 != null && e1.y - e2.y > box.height / 5 && abs(vy) > abs(vx)
                if (!up || !closable || r.taskId == null) return false
                Shell.run("am stack remove " + r.taskId) { load() }
                return true
            }
        })
        box.setOnTouchListener { v, e ->
            if (e.actionMasked == MotionEvent.ACTION_UP) v.performClick()
            gestures.onTouchEvent(e)
        }
        return box
    }

    // ---- no access yet ---------------------------------------------------------------------

    private fun askForAccess() {
        val col = Ui.page(this)
        col.title("Recent apps")
        col.note(
            "Android does not tell apps what has been used recently. Give AssistKey " +
                "usage access and it can put your apps in order of last use. On some " +
                "devices the setting is named App usage data." +
                if (Shell.SUPPORTED) " Shell access gives the exact list the system keeps." else ""
        )
        col.button("Give usage access") {
            runCatching { startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS)) }
        }
        if (Shell.SUPPORTED) {
            col.button("Shell access") { startActivity(Intent(this, ShellActivity::class.java)) }
        }
    }
}
