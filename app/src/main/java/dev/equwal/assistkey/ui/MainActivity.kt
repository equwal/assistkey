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
import dev.equwal.assistkey.device.Detect
import dev.equwal.assistkey.home.HomeActivity
import dev.equwal.assistkey.license.License
import dev.equwal.assistkey.license.PlayBilling
import dev.equwal.assistkey.model.GestureType
import dev.equwal.assistkey.model.HwKey
import dev.equwal.assistkey.model.Trigger
import dev.equwal.assistkey.native.NavNative
import dev.equwal.assistkey.setup.DeviceView
import dev.equwal.assistkey.setup.GuidedSetupActivity
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
 * is, and the drawing of the device that shows and sets up the buttons. Everything
 * else is a tile that leads to a screen of its own.
 *
 * Rebuilt in onResume rather than onCreate, because almost every setup step
 * happens in another app - Settings, a role dialog - and the user comes back
 * expecting the state lines to have caught up.
 */
class MainActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        Channels.syncComponents(this)
        dev.equwal.assistkey.shell.Shell.connect(this)
        // Finds the buttons and the capabilities of this device. It runs at
        // first start, and again after a firmware or app change.
        Detect.refreshIfStale(this)
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
        buttons(col)
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
            text = "Rebind"
            setTextColor(Ui.INK)
            setTextSize(TypedValue.COMPLEX_UNIT_SP, 24f)
            setTypeface(Typeface.DEFAULT_BOLD)
        }, LinearLayout.LayoutParams(0, WRAP_CONTENT, 1f))
        bar.addView(Ui.chip(this, Summary.licenceChip(s.tier, s.trialDaysLeft)))
        col.addView(bar, LinearLayout.LayoutParams(MATCH_PARENT, WRAP_CONTENT))
    }

    // ---- the buttons ---------------------------------------------------------

    /** The buttons the user tapped in the drawing, oldest first. */
    private var selected: List<HwKey> = emptyList()

    /**
     * The drawing of the device is the way in. Each button shows what it does
     * now. A tap selects a button, two selected buttons make a combination, and
     * the button under the drawing opens the setup of that selection.
     */
    private fun buttons(col: LinearLayout) {
        val bound = Store.bindings(this).all()
        val drawn = GuidedSetupActivity.drawn(this)
        col.addView(DeviceView(this, heightDp = 270).apply {
            keys = drawn
            selected = this@MainActivity.selected
            captions = drawn.associateWith { key ->
                Summary.caption(bound.filterKeys { key in it.keys }.values.map { it.describe() })
            }
            onSelect = { this@MainActivity.selected = it; build() }
        })
        if (selected.isEmpty()) {
            col.note("Tap a button. Tap two for a combination.")
        } else {
            col.primaryButton("Set up " + selected.joinToString(" + ") { it.label }) {
                startActivity(GuidedSetupActivity.intent(this, selected))
            }
        }
    }

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
                Ui.Tile("Advanced", "Every button, tools and backup") {
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
