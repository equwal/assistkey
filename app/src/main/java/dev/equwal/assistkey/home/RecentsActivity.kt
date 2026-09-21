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
    private var strip: LinearLayout? = null
    private var heading: TextView? = null
    private var closeOthers: android.widget.Button? = null
    private var names: List<String> = emptyList()
    private var cardStep = 0
    private var index = 0

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
        val cardW = (screenW * 0.70f).toInt()
        val cardH = (screenH * 0.56f).toInt()
        val gap = dp(16)
        cardStep = cardW + gap
        names = recents.map { it.app.label }
        index = index.coerceIn(0, maxOf(0, recents.size - 1))

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            setOnApplyWindowInsetsListener { v, insets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars())
                v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                insets
            }
        }
        heading = TextView(this).apply {
            text = if (recents.isEmpty()) "Nothing recent" else "Recent apps"
            setTextColor(Color.BLACK)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 22f)
            typeface = Typeface.DEFAULT_BOLD
            setSingleLine()
            setPadding(dp(20), dp(16), dp(20), dp(12))
        }
        root.addView(heading)

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
            private var startX = 0

            override fun onInterceptTouchEvent(ev: MotionEvent): Boolean {
                if (ev.actionMasked == MotionEvent.ACTION_DOWN) startX = scrollX
                return super.onInterceptTouchEvent(ev)
            }

            /**
             * One swipe, one card. The row follows the finger, and on release it
             * goes to the next card in the direction of the swipe, however far
             * or fast the swipe was. A very short drag goes back where it was.
             */
            override fun onTouchEvent(ev: MotionEvent): Boolean {
                if (ev.actionMasked == MotionEvent.ACTION_DOWN) startX = scrollX
                val handled = super.onTouchEvent(ev)
                if (ev.actionMasked == MotionEvent.ACTION_UP || ev.actionMasked == MotionEvent.ACTION_CANCEL) {
                    val moved = scrollX - startX
                    val from = Math.round(startX / cardStep.toFloat())
                    val turn = if (abs(moved) < cardStep / 8) 0 else if (moved > 0) 1 else -1
                    show(from + turn)
                }
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

        // Every recent app at once, as icons. The one in the middle is marked,
        // and a tap on an icon brings its card to the middle.
        val icons = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(12), dp(14), dp(12), dp(4))
        }
        recents.forEachIndexed { i, r ->
            icons.addView(
                ImageView(this).apply {
                    setImageDrawable(runCatching { packageManager.getApplicationIcon(r.app.pkg) }.getOrNull())
                    contentDescription = r.app.label
                    val p = dp(6)
                    setPadding(p, p, p, p)
                    setOnClickListener { show(i) }
                },
                LinearLayout.LayoutParams(dp(52), dp(52)).apply { marginEnd = dp(4) }
            )
        }
        strip = icons
        root.addView(
            HorizontalScrollView(this).apply {
                isHorizontalScrollBarEnabled = false
                addView(icons, LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT))
            },
            LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
        )

        root.addView(TextView(this).apply {
            text = if (closable) "Tap a card to open it.  Swipe a card up to close it."
            else "Tap a card to open it."
            setTextColor(Color.BLACK)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 16f)
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(20), dp(14), dp(20), dp(8))
        })
        closeOthers = null
        if (closable && recents.isNotEmpty()) {
            val buttons = LinearLayout(this).apply {
                orientation = LinearLayout.HORIZONTAL
                gravity = Gravity.CENTER_HORIZONTAL
            }
            buttons.addView(android.widget.Button(this).apply {
                text = "Close all"
                setOnClickListener { close(recents.mapNotNull { it.taskId }) }
            })
            if (recents.size > 1) {
                closeOthers = android.widget.Button(this).apply {
                    // The name is filled in by show(i): it is the card in the middle.
                    setOnClickListener {
                        close(recents.filterIndexed { i, _ -> i != index }.mapNotNull { it.taskId })
                    }
                }
                buttons.addView(closeOthers)
            }
            root.addView(buttons, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        }
        setContentView(root)
        scroll.post { show(index) }
    }

    private fun close(taskIds: List<Int>) {
        index = 0
        Shell.runAll(taskIds.map { "am stack remove $it" }) { load() }
    }

    /** Puts card [i] in the middle, in one step, and says which one it is. */
    private fun show(i: Int) {
        val s = scroller ?: return
        val count = row?.childCount ?: 0
        if (cardStep <= 0 || count == 0) return
        index = i.coerceIn(0, count - 1)
        s.scrollTo(index * cardStep, 0)
        heading?.text = names.getOrElse(index) { "" } + "   " + (index + 1) + " of " + count
        closeOthers?.text = "Close all but " + names.getOrElse(index) { "this one" }
        strip?.let { icons ->
            for (n in 0 until icons.childCount) {
                icons.getChildAt(n).background = if (n != index) null else GradientDrawable().apply {
                    setStroke(dp(2), Color.BLACK)
                    cornerRadius = dp(10).toFloat()
                }
            }
        }
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
        if (closable && r.taskId != null) {
            box.addView(View(this).apply { setBackgroundColor(Color.BLACK) }, LinearLayout.LayoutParams(MATCH_PARENT, dp(1)))
            box.addView(TextView(this).apply {
                text = "\u2191  Swipe up to close"
                setTextColor(Color.BLACK)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                gravity = Gravity.CENTER_HORIZONTAL
                setPadding(0, dp(8), 0, dp(8))
            })
        }
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
