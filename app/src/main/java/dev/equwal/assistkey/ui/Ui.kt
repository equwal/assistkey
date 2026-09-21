package dev.equwal.assistkey.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.LayerDrawable
import android.graphics.drawable.StateListDrawable
import android.text.InputType
import android.text.TextUtils
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/**
 * View construction, in code, with no support library.
 *
 * The screen is e-ink: no colour, slow refresh, and a hard time with subtle
 * greys. So everything here is black on white at generous sizes, outlines are
 * real black lines, and nothing animates or ripples.
 *
 * The rules this file keeps, so that every screen looks the same:
 *
 *  - 16 dp side margins, and spacing on an 8 dp grid.
 *  - Rows and checks are at least 56 dp high, and the whole row is the
 *    touch target.
 *  - Titles are 17 sp, summaries 14 sp, section headers 13 sp small caps.
 *  - Secondary text is #444444. Nothing lighter carries a fact.
 *  - Buttons are 48 dp high and outlined. The one primary button on a screen
 *    is 56 dp high and filled black.
 */
object Ui {

    const val INK = Color.BLACK

    /** Secondary text. The lightest grey that still reads on e-ink. */
    val DIM = Color.rgb(68, 68, 68)

    /** Separator lines between rows. */
    val RULE = Color.rgb(150, 150, 150)

    fun Context.dp(v: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics
    ).toInt()

    private fun Context.px(v: Int): Int = maxOf(1, dp(v))

    // ---- page ------------------------------------------------------------

    /**
     * Root of a screen; returns the column that callers fill.
     *
     * With a [title] the screen gets the standard top bar: a back arrow with a
     * 48 dp touch target, the title, and a rule under it. The content below
     * scrolls only when it does not fit.
     */
    fun page(a: Activity, title: String? = null): LinearLayout {
        val root = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
        }
        if (title != null) {
            root.addView(topBar(a, title))
            root.addView(View(a).apply {
                setBackgroundColor(RULE)
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, 1)
            })
        }

        val col = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(a.dp(16), a.dp(if (title == null) 8 else 12), a.dp(16), a.dp(24))
        }
        val scroll = ScrollView(a).apply {
            isFillViewport = true
            clipToPadding = false
            addView(col, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        }
        root.addView(scroll, LinearLayout.LayoutParams(MATCH_PARENT, 0, 1f))

        // Edge-to-edge is enforced from Android 15, so the system bars overlap
        // the window. The top bar moves down by whatever the status bar covers,
        // and the scrolling content keeps clear of the navigation bar.
        root.setOnApplyWindowInsetsListener { v, insets ->
            val bars = insets.getInsets(
                WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout()
            )
            v.setPadding(bars.left, bars.top, bars.right, 0)
            col.setPadding(
                col.paddingLeft, col.paddingTop, col.paddingRight, a.dp(24) + bars.bottom
            )
            insets
        }
        a.setContentView(root)
        root.requestApplyInsets()
        return col
    }

    private fun topBar(a: Activity, text: String): View {
        val bar = LinearLayout(a).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(a.dp(4), 0, a.dp(16), 0)
            layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, a.dp(56))
        }
        bar.addView(Glyph(a, Glyph.BACK).apply {
            contentDescription = "Back"
            isClickable = true
            isFocusable = true
            @Suppress("DEPRECATION")
            setOnClickListener { a.onBackPressed() }
            layoutParams = LinearLayout.LayoutParams(a.dp(48), a.dp(48))
        })
        bar.addView(TextView(a).apply {
            this.text = text
            setTextColor(INK)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            setTypeface(Typeface.DEFAULT_BOLD)
            maxLines = 1
            ellipsize = TextUtils.TruncateAt.END
            layoutParams = LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply {
                marginStart = a.dp(4)
            }
        })
        return bar
    }

    // ---- text ------------------------------------------------------------

    /** Screen title, for a screen that draws no top bar. */
    fun LinearLayout.title(text: String) = add(TextView(context).apply {
        this.text = text
        setTextColor(INK)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
        setTypeface(Typeface.DEFAULT_BOLD)
        setPadding(0, context.dp(4), 0, context.dp(8))
    })

    fun LinearLayout.header(text: String) = add(TextView(context).apply {
        this.text = text.uppercase()
        setTextColor(DIM)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
        setTypeface(Typeface.DEFAULT_BOLD)
        letterSpacing = 0.08f
        setPadding(0, context.dp(24), 0, context.dp(8))
    })

    fun LinearLayout.note(text: String) = add(TextView(context).apply {
        this.text = text
        setTextColor(DIM)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        setLineSpacing(context.dp(3).toFloat(), 1f)
        setPadding(0, 0, 0, context.dp(8))
    })

    fun LinearLayout.rule() = add(View(context).apply {
        setBackgroundColor(RULE)
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, 1)
    })

    // ---- rows ------------------------------------------------------------

    /**
     * A tappable row: what it is, what it currently does, and either a state
     * chip or a chevron on the right. The whole row is the touch target.
     */
    fun LinearLayout.row(
        title: String,
        subtitle: String? = null,
        enabled: Boolean = true,
        state: String? = null,
        onClick: (() -> Unit)? = null
    ): LinearLayout {
        val box = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = context.dp(56)
            val v = context.dp(10)
            setPadding(0, v, 0, v)
            isClickable = onClick != null && enabled
            if (isClickable) setOnClickListener { onClick?.invoke() }
        }
        val texts = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(TextView(context).apply {
            text = title
            setTextColor(if (enabled) INK else DIM)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
        })
        if (!subtitle.isNullOrBlank()) {
            texts.addView(TextView(context).apply {
                text = subtitle
                setTextColor(DIM)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setPadding(0, context.dp(2), 0, 0)
            })
        }
        box.addView(texts, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))

        if (state != null) {
            box.addView(chip(context, state).apply {
                layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
                    marginStart = context.dp(8)
                }
            })
        } else if (box.isClickable) {
            box.addView(Glyph(context, Glyph.CHEVRON).apply {
                layoutParams = LinearLayout.LayoutParams(context.dp(24), context.dp(24)).apply {
                    marginStart = context.dp(8)
                }
            })
        }
        add(box)
        rule()
        return box
    }

    /**
     * A check whose whole row is the touch target. The box is drawn, not
     * themed, so it is a real black outline on every firmware.
     */
    fun LinearLayout.check(
        title: String,
        subtitle: String?,
        checked: Boolean,
        onChange: (Boolean) -> Unit
    ): CheckBox {
        val cb = CheckBox(context).apply {
            text = ""
            isChecked = checked
            buttonDrawable = checkBox(context)
            isClickable = false
            isFocusable = false
            minWidth = 0
            minimumWidth = 0
            setPadding(0, 0, 0, 0)
        }
        val box = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = context.dp(56)
            val v = context.dp(10)
            setPadding(0, v, 0, v)
            isClickable = true
            setOnClickListener {
                cb.isChecked = !cb.isChecked
                onChange(cb.isChecked)
            }
        }
        box.addView(cb, LinearLayout.LayoutParams(context.dp(24), context.dp(24)).apply {
            marginEnd = context.dp(14)
        })
        val texts = LinearLayout(context).apply { orientation = LinearLayout.VERTICAL }
        texts.addView(TextView(context).apply {
            text = title
            setTextColor(INK)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
        })
        if (!subtitle.isNullOrBlank()) {
            texts.addView(TextView(context).apply {
                text = subtitle
                setTextColor(DIM)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setPadding(0, context.dp(2), 0, 0)
            })
        }
        box.addView(texts, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        add(box)
        rule()
        return cb
    }

    // ---- tiles -----------------------------------------------------------

    /** One hub tile: where it leads, and what state it is in now. */
    class Tile(val title: String, val summary: String, val onClick: () -> Unit)

    /**
     * Two columns of outlined tiles. A tile left over at the end takes the
     * full width, so no row is ever half empty.
     */
    fun LinearLayout.tiles(items: List<Tile>) {
        var i = 0
        while (i < items.size) {
            if (items.size - i == 1) {
                add(tile(context, items[i]).apply {
                    layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
                        topMargin = context.dp(8)
                    }
                })
                i += 1
                continue
            }
            val pair = LinearLayout(context).apply {
                orientation = LinearLayout.HORIZONTAL
                layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
                    topMargin = context.dp(8)
                }
            }
            pair.addView(tile(context, items[i]), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f).apply {
                marginEnd = context.dp(8)
            })
            pair.addView(tile(context, items[i + 1]), LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
            add(pair)
            i += 2
        }
    }

    private fun tile(c: Context, t: Tile): View {
        val box = LinearLayout(c).apply {
            orientation = LinearLayout.VERTICAL
            background = outline(c)
            minimumHeight = c.dp(84)
            val p = c.dp(14)
            setPadding(p, p, p, p)
            isClickable = true
            isFocusable = true
            setOnClickListener { t.onClick() }
        }
        box.addView(TextView(c).apply {
            text = t.title
            setTextColor(INK)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            setTypeface(Typeface.DEFAULT_BOLD)
        })
        box.addView(TextView(c).apply {
            text = t.summary
            setTextColor(DIM)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
            maxLines = 2
            ellipsize = TextUtils.TruncateAt.END
            setPadding(0, c.dp(4), 0, 0)
        })
        return box
    }

    // ---- buttons ---------------------------------------------------------

    /** The one action a screen is really for: filled black, 56 dp, full width. */
    fun LinearLayout.primaryButton(text: String, onClick: () -> Unit) =
        add(flatButton(context, text, 56, Color.WHITE, INK, 17f).apply {
            setOnClickListener { onClick() }
        })

    /** Every other action: outlined, 48 dp, full width. */
    fun LinearLayout.button(text: String, onClick: () -> Unit) =
        add(flatButton(context, text, 48, INK, Color.WHITE, 16f).apply {
            setOnClickListener { onClick() }
        })

    /**
     * A button with the platform look taken off: our own outline or fill, no
     * elevation, no ripple - which smears on an e-ink panel.
     */
    private fun flatButton(
        c: Context,
        text: String,
        height: Int,
        textColor: Int,
        fill: Int,
        size: Float
    ): Button = Button(c).apply {
        this.text = text
        isAllCaps = false
        gravity = Gravity.CENTER
        setTextColor(textColor)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
        setTypeface(Typeface.DEFAULT_BOLD)
        background = outline(c, fill)
        stateListAnimator = null
        elevation = 0f
        minWidth = 0
        minimumWidth = 0
        minHeight = 0
        minimumHeight = 0
        setPadding(c.dp(16), 0, c.dp(16), 0)
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, c.dp(height)).apply {
            topMargin = c.dp(8)
            bottomMargin = c.dp(8)
        }
    }

    /** Monospace block for adb commands the user has to run themselves. */
    fun LinearLayout.code(text: String) = add(TextView(context).apply {
        this.text = text
        setTextColor(INK)
        setTypeface(Typeface.MONOSPACE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        background = outline(context)
        val p = context.dp(10)
        setPadding(p, p, p, p)
        setTextIsSelectable(true)
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
            topMargin = context.dp(4)
            bottomMargin = context.dp(12)
        }
    })

    // ---- drawing -----------------------------------------------------------

    /** A black outline on a fill: the one shape this app is built from. */
    fun outline(c: Context, fill: Int = Color.WHITE, radius: Int = 2): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(fill)
            cornerRadius = c.dp(radius).toFloat()
            setStroke(c.px(1), INK)
        }

    /** A short state word - On, Off, Granted, Missing - in an outlined box. */
    fun chip(c: Context, text: String): TextView = TextView(c).apply {
        this.text = text
        setTextColor(INK)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        setTypeface(Typeface.DEFAULT_BOLD)
        background = outline(c, radius = 10)
        setPadding(c.dp(8), c.dp(3), c.dp(8), c.dp(3))
        maxLines = 1
    }

    /** Empty square, or square with a solid black block in it. */
    private fun checkBox(c: Context): StateListDrawable {
        val size = c.dp(24)
        val empty = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(Color.WHITE)
            cornerRadius = c.dp(2).toFloat()
            setStroke(c.px(2), INK)
            setSize(size, size)
        }
        val frame = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(Color.WHITE)
            cornerRadius = c.dp(2).toFloat()
            setStroke(c.px(2), INK)
            setSize(size, size)
        }
        val block = GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(INK)
        }
        val ticked = LayerDrawable(arrayOf(frame, block)).apply {
            val inset = c.dp(6)
            setLayerInset(1, inset, inset, inset, inset)
        }
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_checked), ticked)
            addState(intArrayOf(), empty)
        }
    }

    /**
     * The back arrow and the row chevron, drawn rather than typed, because a
     * firmware font may not carry the characters.
     */
    private class Glyph(c: Context, private val kind: Int) : View(c) {

        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = INK
            style = Paint.Style.STROKE
            strokeWidth = c.resources.displayMetrics.density * 2f
            strokeCap = Paint.Cap.ROUND
            strokeJoin = Paint.Join.ROUND
        }
        private val path = Path()

        override fun onDraw(canvas: Canvas) {
            val cx = width / 2f
            val cy = height / 2f
            path.reset()
            if (kind == BACK) {
                val r = minOf(width, height) * 0.24f
                val h = r * 0.62f
                path.moveTo(cx + r, cy)
                path.lineTo(cx - r, cy)
                path.moveTo(cx - r + h, cy - h)
                path.lineTo(cx - r, cy)
                path.lineTo(cx - r + h, cy + h)
            } else {
                val w = minOf(width, height) * 0.17f
                val h = w * 1.7f
                path.moveTo(cx - w, cy - h)
                path.lineTo(cx + w, cy)
                path.lineTo(cx - w, cy + h)
            }
            canvas.drawPath(path, paint)
        }

        companion object {
            const val BACK = 0
            const val CHEVRON = 1
        }
    }

    /** Full-width by default; callers that want otherwise set params first. */
    private fun LinearLayout.add(v: View) {
        if (v.layoutParams == null) {
            v.layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
        }
        addView(v)
    }

    // ---- dialogs -----------------------------------------------------------

    fun pick(a: Activity, title: String, labels: List<String>, onPick: (Int) -> Unit) {
        AlertDialog.Builder(a)
            .setTitle(title)
            .setItems(labels.toTypedArray()) { _, which -> onPick(which) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    fun textInput(
        a: Activity,
        title: String,
        hint: String,
        initial: String = "",
        onOk: (String) -> Unit
    ) {
        val field = EditText(a).apply {
            this.hint = hint
            setText(initial)
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            val p = a.dp(16)
            setPadding(p, p, p, p)
        }
        AlertDialog.Builder(a)
            .setTitle(title)
            .setView(field)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val v = field.text.toString().trim()
                if (v.isNotEmpty()) onOk(v)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}
