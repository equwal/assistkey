package dev.equwal.assistkey.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.util.TypedValue
import android.view.Gravity
import android.view.ViewGroup.LayoutParams.MATCH_PARENT
import android.view.ViewGroup.LayoutParams.WRAP_CONTENT
import android.widget.LinearLayout
import android.widget.TextView
import dev.equwal.assistkey.channel.Channel
import dev.equwal.assistkey.channel.Channels
import dev.equwal.assistkey.home.HomeActivity
import dev.equwal.assistkey.license.License
import dev.equwal.assistkey.license.PlayBilling
import dev.equwal.assistkey.model.GestureType
import dev.equwal.assistkey.model.HwKey
import dev.equwal.assistkey.model.Trigger
import dev.equwal.assistkey.native.NavNative
import dev.equwal.assistkey.store.Store
import dev.equwal.assistkey.ui.Ui.dp
import dev.equwal.assistkey.ui.Ui.header
import dev.equwal.assistkey.ui.Ui.note
import dev.equwal.assistkey.ui.Ui.primaryButton
import dev.equwal.assistkey.ui.Ui.row
import dev.equwal.assistkey.ui.Ui.rule
import dev.equwal.assistkey.ui.Ui.tiles
import dev.equwal.assistkey.voice.Dictation

/**
 * The hub, and the only screen the app opens on.
 *
 * It fits on one screen at 412 x 824 dp and says three things: what the licence
 * is, the one action that sets up a button, and what is bound now. Everything
 * else is a tile that leads to a screen of its own.
 *
 * Rebuilt in onResume rather than onCreate, because almost every setup step
 * happens in another app - Settings, a role dialog - and the user comes back
 * expecting the state lines to have caught up.
 */
class MainActivity : Activity() {

    /** How many bindings the hub lists before it points at Advanced. */
    private val listed = 4

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Channels.syncComponents(this)
        dev.equwal.assistkey.shell.Shell.connect(this)
        PermissionsActivity.showOnce(this)
    }

    override fun onResume() {
        super.onResume()
        build()
        // Play answers asynchronously; redraw only if the answer changed the tier.
        val before = License.state(this).tier
        PlayBilling.refresh(this) {
            if (License.state(this).tier != before && !isFinishing) build()
        }
    }

    private fun build() {
        val col = Ui.page(this)
        appBar(col)
        col.rule()
        callToAction(col)
        yourButtons(col)
        hubTiles(col)
    }

    // ---- app bar -----------------------------------------------------------

    /** The name, and the licence state beside it. The whole bar opens Licence. */
    private fun appBar(col: LinearLayout) {
        val s = License.state(this)
        val bar = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            minimumHeight = dp(56)
            isClickable = true
            setOnClickListener { startActivity(Intent(this@MainActivity, LicenseActivity::class.java)) }
        }
        bar.addView(TextView(this).apply {
            text = "AssistKey"
            setTextColor(Ui.INK)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
            setTypeface(Typeface.DEFAULT_BOLD)
        }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        bar.addView(Ui.chip(this, Summary.licenceChip(s.tier, s.trialDaysLeft)))
        col.addView(bar, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
    }

    // ---- the one action ----------------------------------------------------

    /**
     * Press a button, choose what it does. The flow finds the way in by itself,
     * so nobody has to know what a channel or a role is.
     */
    private fun callToAction(col: LinearLayout) {
        col.primaryButton("Set up a button") {
            startActivity(
                Intent().setClassName(this, "dev.equwal.assistkey.setup.GuidedSetupActivity")
            )
        }
        col.note("Pick a button, choose what it does, and AssistKey does the rest.")
    }

    // ---- what is bound now -------------------------------------------------

    private fun yourButtons(col: LinearLayout) {
        col.header("Your buttons")
        val all = Store.bindings(this).all().entries
            .sortedWith(
                compareBy(
                    { t -> t.key.keys.minOf { it.ordinal } },
                    { t -> t.key.keys.size },
                    { t -> t.key.type.ordinal },
                    { t -> t.key.count }
                )
            )
        if (all.isEmpty()) {
            col.note("Nothing is bound yet. Set up a button to make a start.")
            return
        }
        val overflow = all.size > listed
        val shown = all.take(if (overflow) listed - 1 else listed)
        shown.forEach { (trigger, spec) ->
            col.row(Summary.binding(plainGesture(trigger), spec.describe())) {
                startActivity(ActionPickerActivity.intent(this, trigger))
            }
        }
        if (overflow) {
            col.row(
                (all.size - shown.size).toString() + " more",
                "See them all under Advanced > Full control"
            ) { startActivity(Intent(this, AdvancedActivity::class.java)) }
        }
    }

    private fun plainGesture(t: Trigger): String = Summary.gesture(
        t.keys.sortedBy { it.ordinal }.map { it.label },
        t.type == GestureType.HOLD,
        t.count
    )

    // ---- tiles -------------------------------------------------------------

    private fun hubTiles(col: LinearLayout) {
        col.tiles(
            listOf(
                Ui.Tile("Navigation", navigationSummary()) {
                    startActivity(Intent(this, NavigationActivity::class.java))
                },
                Ui.Tile("Voice typing", voiceSummary()) {
                    startActivity(Intent(this, VoiceActivity::class.java))
                },
                Ui.Tile("Home and recents", homeSummary()) {
                    startActivity(Intent(this, HomeHubActivity::class.java))
                },
                Ui.Tile("Setup", setupSummary()) {
                    startActivity(Intent(this, SetupActivity::class.java))
                },
                Ui.Tile("Advanced", "Every key, the Power button, testing and backup") {
                    startActivity(Intent(this, AdvancedActivity::class.java))
                }
            )
        )
    }

    private fun navigationSummary(): String {
        val p = getSharedPreferences("assistkey_nav", Context.MODE_PRIVATE)
        val buttonsNow = NavNative.buttonsShowing(this)
        val gesturesNow = NavNative.gesturesOn(this) ?: buttonsNow?.not()
        val power = setOf(HwKey.POWER)
        return Summary.navigation(
            p.getBoolean("buttons", buttonsNow ?: true),
            p.getBoolean("gestures", gesturesNow ?: true),
            p.getBoolean("keys", Store.bindings(this).all().keys.any { it.keys == power })
        )
    }

    private fun voiceSummary(): String {
        val engines = Dictation.engines(this)
        return Summary.voice(
            Dictation.hasMicrophone(this),
            engines.size,
            engines.any(Dictation::isOnDevice)
        )
    }

    private fun homeSummary(): String {
        val cn = android.content.ComponentName(this, HomeActivity::class.java)
        val offered = packageManager.getComponentEnabledSetting(cn) ==
            android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        val isDefault = packageManager.resolveActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME),
            android.content.pm.PackageManager.MATCH_DEFAULT_ONLY
        )?.activityInfo?.packageName == packageName
        return Summary.homeAndRecents(offered, isDefault)
    }

    private fun setupSummary(): String {
        val granted = PermissionsActivity.granted(this)
        return Summary.setup(
            Channels.isSatisfied(this, Channel.ACCESSIBILITY),
            granted.count { it },
            granted.size
        )
    }
}
