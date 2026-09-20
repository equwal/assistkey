package dev.equwal.assistkey.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
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
 * greys. So everything here is black on white at generous sizes, and nothing
 * animates.
 */
object Ui {

    const val INK = Color.BLACK
    val DIM = Color.rgb(90, 90, 90)
    val RULE = Color.rgb(200, 200, 200)

    fun Context.dp(v: Int): Int = TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics
    ).toInt()

    /** Root scrolling column; returns the column so callers can keep filling it. */
    fun page(a: Activity): LinearLayout {
        val col = LinearLayout(a).apply {
            orientation = LinearLayout.VERTICAL
            val p = a.dp(16)
            setPadding(p, p, p, a.dp(40))
        }
        val scroll = ScrollView(a).apply {
            isFillViewport = true
            setBackgroundColor(Color.WHITE)
            addView(col, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        }
        a.setContentView(scroll)
        return col
    }

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
        setPadding(0, context.dp(20), 0, context.dp(6))
    })

    fun LinearLayout.note(text: String) = add(TextView(context).apply {
        this.text = text
        setTextColor(DIM)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
        setPadding(0, 0, 0, context.dp(8))
    })

    fun LinearLayout.rule() = add(View(context).apply {
        setBackgroundColor(RULE)
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, 1)
    })

    /** A tappable two-line row: what it is, and what it currently does. */
    fun LinearLayout.row(
        title: String,
        subtitle: String? = null,
        enabled: Boolean = true,
        onClick: (() -> Unit)? = null
    ): LinearLayout {
        val box = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            val v = context.dp(12)
            setPadding(0, v, 0, v)
            isClickable = onClick != null && enabled
            if (isClickable) {
                setBackgroundResource(
                    android.R.drawable.list_selector_background
                )
                setOnClickListener { onClick?.invoke() }
            }
        }
        box.addView(TextView(context).apply {
            text = title
            setTextColor(if (enabled) INK else DIM)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
        })
        if (!subtitle.isNullOrBlank()) {
            box.addView(TextView(context).apply {
                text = subtitle
                setTextColor(DIM)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setPadding(0, context.dp(2), 0, 0)
            })
        }
        add(box)
        rule()
        return box
    }

    fun LinearLayout.check(
        title: String,
        subtitle: String?,
        checked: Boolean,
        onChange: (Boolean) -> Unit
    ): CheckBox {
        val cb = CheckBox(context).apply {
            text = title
            isChecked = checked
            setTextColor(INK)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 17f)
            setPadding(context.dp(8), context.dp(10), 0, context.dp(2))
            setOnClickListener { onChange(isChecked) }
        }
        add(cb)
        if (!subtitle.isNullOrBlank()) {
            add(TextView(context).apply {
                text = subtitle
                setTextColor(DIM)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 14f)
                setPadding(context.dp(44), 0, 0, context.dp(10))
            })
        }
        return cb
    }

    fun LinearLayout.button(text: String, onClick: () -> Unit) = add(Button(context).apply {
        this.text = text
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
        setOnClickListener { onClick() }
        layoutParams = LinearLayout.LayoutParams(WRAP_CONTENT, WRAP_CONTENT).apply {
            topMargin = context.dp(4)
            bottomMargin = context.dp(8)
            gravity = Gravity.START
        }
    })

    /** Monospace block for adb commands the user has to run themselves. */
    fun LinearLayout.code(text: String) = add(TextView(context).apply {
        this.text = text
        setTextColor(INK)
        setTypeface(Typeface.MONOSPACE)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, 12f)
        setBackgroundColor(Color.rgb(240, 240, 240))
        val p = context.dp(8)
        setPadding(p, p, p, p)
        setTextIsSelectable(true)
        layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT).apply {
            bottomMargin = context.dp(10)
        }
    })

    /** Full-width by default; callers that want otherwise set params first. */
    private fun LinearLayout.add(v: View) {
        if (v.layoutParams == null) {
            v.layoutParams = LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT)
        }
        addView(v)
    }

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
