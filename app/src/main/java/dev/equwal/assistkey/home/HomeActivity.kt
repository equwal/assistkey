package dev.equwal.assistkey.home

import android.app.Activity
import android.app.AlertDialog
import android.content.Intent
import android.graphics.Color
import android.graphics.Typeface
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.view.WindowInsets
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextClock
import android.widget.TextView
import dev.equwal.assistkey.ui.Ui.dp

/**
 * A home screen with nothing on it.
 *
 * A clock, the handful of apps you chose, and a line to type in. The full app
 * list is never shown: type a few letters and the matches appear, and when only
 * one is left it opens by itself. On e-ink that is also the fastest design
 * there is - no icons to draw, no grid to scroll, nothing that animates.
 *
 * Original code. The idea of a launcher that hides everything until you type is
 * shared with several open-source launchers; none of their code is here.
 */
class HomeActivity : Activity() {

    private lateinit var results: LinearLayout
    private lateinit var favourites: LinearLayout
    private lateinit var search: EditText
    private var apps: List<Apps.App> = emptyList()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        build()
    }

    override fun onResume() {
        super.onResume()
        apps = Apps.visible(this)
        search.setText("")
        showFavourites()
    }

    /** Home is the bottom of the stack. Back has nowhere to go. */
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (search.text.isNotEmpty()) search.setText("")
    }

    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        // Home pressed while already home: clear the search.
        if (::search.isInitialized) search.setText("")
    }

    // ---- layout ---------------------------------------------------------------------------------

    private fun build() {
        val col = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            val p = dp(28)
            setPadding(p, dp(36), p, p)
        }

        if (Apps.showClock(this)) {
            col.addView(TextClock(this).apply {
                format12Hour = "h:mm"
                format24Hour = "H:mm"
                setTextColor(Color.BLACK)
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 64f)
                typeface = Typeface.create("sans-serif-light", Typeface.NORMAL)
                setOnClickListener { open(Intent(android.provider.AlarmClock.ACTION_SHOW_ALARMS)) }
            })
            col.addView(TextClock(this).apply {
                format12Hour = "EEEE, d MMMM"
                format24Hour = "EEEE, d MMMM"
                setTextColor(Color.rgb(70, 70, 70))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 18f)
                setPadding(0, 0, 0, dp(28))
            })
        }

        favourites = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(favourites)

        search = EditText(this).apply {
            hint = "Type to find an app"
            setSingleLine()
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
            imeOptions = EditorInfo.IME_ACTION_GO
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
            setTextColor(Color.BLACK)
            setPadding(0, dp(20), 0, dp(12))
            addTextChangedListener(object : TextWatcher {
                override fun afterTextChanged(s: Editable?) = filter(s?.toString().orEmpty())
                override fun beforeTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
                override fun onTextChanged(s: CharSequence?, a: Int, b: Int, c: Int) = Unit
            })
            setOnEditorActionListener { _, _, _ -> matches(text.toString()).firstOrNull()?.let(::launch); true }
        }
        col.addView(search, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))

        results = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        col.addView(results)

        val scroll = ScrollView(this).apply {
            setBackgroundColor(Color.WHITE)
            isFillViewport = true
            clipToPadding = false
            addView(col, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
            setOnApplyWindowInsetsListener { v, insets ->
                val bars = insets.getInsets(WindowInsets.Type.systemBars() or WindowInsets.Type.ime())
                v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
                insets
            }
            // Long-press on empty space is the only way into settings: there is
            // deliberately no button for it.
            setOnLongClickListener { startActivity(Intent(this@HomeActivity, HomeSettingsActivity::class.java)); true }
        }
        col.setOnLongClickListener { startActivity(Intent(this, HomeSettingsActivity::class.java)); true }
        setContentView(scroll)
    }

    private fun line(app: Apps.App, size: Float): TextView = TextView(this).apply {
        text = app.label
        setTextColor(Color.BLACK)
        setTextSize(TypedValue.COMPLEX_UNIT_SP, size)
        gravity = Gravity.START
        setPadding(0, dp(12), 0, dp(12))
        setOnClickListener { launch(app) }
        setOnLongClickListener { menu(app); true }
    }

    // ---- behaviour -----------------------------------------------------------------------------

    private fun showFavourites() {
        favourites.removeAllViews()
        val byPkg = apps.associateBy { it.pkg }
        val favs = Apps.favourites(this).mapNotNull { byPkg[it] }
        favs.forEach { favourites.addView(line(it, 26f)) }
        if (favs.isEmpty()) {
            favourites.addView(TextView(this).apply {
                text = "Find an app below, then press and hold it to keep it here."
                setTextColor(Color.rgb(90, 90, 90))
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 15f)
            })
        }
    }

    private fun matches(q: String): List<Apps.App> {
        val needle = q.trim().lowercase()
        if (needle.isEmpty()) return emptyList()
        // Starts-with first, then anywhere in the name, then initials ("gm" finds Google Maps).
        val starts = apps.filter { it.label.lowercase().startsWith(needle) }
        val inside = apps.filter { needle in it.label.lowercase() } - starts.toSet()
        val initials = apps.filter { a ->
            a.label.split(' ', '-', '_').mapNotNull { it.firstOrNull()?.lowercaseChar() }.joinToString("").startsWith(needle)
        } - starts.toSet() - inside.toSet()
        return starts + inside + initials
    }

    private fun filter(q: String) {
        results.removeAllViews()
        val found = matches(q)
        if (found.size == 1 && q.trim().length >= 2 && Apps.autoOpen(this)) {
            launch(found[0])
            return
        }
        found.take(12).forEach { results.addView(line(it, 22f)) }
    }

    private fun launch(app: Apps.App) {
        getSystemService(InputMethodManager::class.java)?.hideSoftInputFromWindow(search.windowToken, 0)
        Apps.launch(this, app)
    }

    private fun open(i: Intent) {
        runCatching { startActivity(i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
    }

    private fun menu(app: Apps.App) {
        val fav = app.pkg in Apps.favourites(this)
        val items = arrayOf(if (fav) "Remove from home" else "Keep on home", "Hide from search", "App info")
        AlertDialog.Builder(this).setTitle(app.label).setItems(items) { _, which ->
            when (which) {
                0 -> Apps.toggleFavourite(this, app.pkg)
                1 -> Apps.toggleHidden(this, app.pkg)
                2 -> open(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:" + app.pkg)))
            }
            apps = Apps.visible(this)
            search.setText("")
            showFavourites()
        }.show()
    }
}
