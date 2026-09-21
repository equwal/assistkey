package dev.equwal.assistkey.menu

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import dev.equwal.assistkey.model.ActionSpec
import dev.equwal.assistkey.route.ActionRouter
import dev.equwal.assistkey.route.ServiceHolder
import dev.equwal.assistkey.ui.Ui.dp

/**
 * The menu on screen: a box of actions over the app in front.
 *
 * A tap on an action closes the menu first and runs the action a moment later.
 * The order matters: Back, a swipe or voice typing act on the app in front, and
 * until the menu is gone the app in front is the menu. A tap outside the box
 * closes the menu and does nothing.
 */
class MenuActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val items = Menu.decode(intent.getStringExtra(EXTRA_ITEMS).orEmpty())
        if (items.isEmpty()) return leave()

        val list = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        items.forEachIndexed { i, spec ->
            if (i > 0) list.addView(View(this).apply { setBackgroundColor(Color.BLACK) }, LinearLayout.LayoutParams(MATCH_PARENT, dp(1)))
            list.addView(TextView(this).apply {
                text = spec.describe()
                setTextColor(Color.BLACK)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
                setPadding(dp(20), dp(18), dp(20), dp(18))
                setOnClickListener { pick(spec) }
            })
        }
        val box = ScrollView(this).apply {
            background = GradientDrawable().apply {
                setColor(Color.WHITE)
                setStroke(dp(2), Color.BLACK)
                cornerRadius = dp(12).toFloat()
            }
            val p = dp(3)
            setPadding(p, p, p, p)
            addView(list, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
        }
        setContentView(
            FrameLayout(this).apply {
                setOnClickListener { leave() }
                addView(
                    box,
                    FrameLayout.LayoutParams((resources.displayMetrics.widthPixels * 0.8f).toInt(), WRAP_CONTENT, Gravity.CENTER)
                )
            }
        )
    }

    private fun pick(spec: ActionSpec) {
        val app = applicationContext
        leave()
        Handler(Looper.getMainLooper()).postDelayed(
            // The accessibility service may start activities from the background;
            // a plain application context may not.
            { ActionRouter.run(ServiceHolder.service ?: app, spec) },
            AFTER_CLOSE_MS
        )
    }

    private fun leave() {
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    companion object {
        private const val EXTRA_ITEMS = "items"

        /** Time for the app behind the menu to be in front again. */
        private const val AFTER_CLOSE_MS = 300L

        fun open(c: Context, payload: String): Boolean = runCatching {
            c.startActivity(
                Intent(c, MenuActivity::class.java).putExtra(EXTRA_ITEMS, payload)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_NO_ANIMATION)
            )
            true
        }.getOrDefault(false)
    }
}
