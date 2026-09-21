package dev.equwal.assistkey.channel

import android.app.Activity
import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import dev.equwal.assistkey.engine.KeyFilterService
import dev.equwal.assistkey.license.License
import dev.equwal.assistkey.model.ActionKind
import dev.equwal.assistkey.model.ActionSpec
import dev.equwal.assistkey.model.GestureType
import dev.equwal.assistkey.model.HwKey
import dev.equwal.assistkey.model.Presets
import dev.equwal.assistkey.model.Trigger
import dev.equwal.assistkey.route.ActionRouter
import dev.equwal.assistkey.route.ServiceHolder
import dev.equwal.assistkey.store.Store

/**
 * Where the AI key lands once the firmware hook points here.
 *
 * Measured on firmware 1.5.6: with the stock hook the firmware opens its AI
 * screen on every press whether or not an app consumed the key, so a binding
 * made through the key filter always fires on top of that screen. With the
 * hook set to anything else the firmware launches that component instead and
 * the filter never sees the key. Pointing it here is therefore the only way to
 * own the AI key cleanly:
 *
 *   adb shell settings put system CustomAiKey \
 *       dev.equwal.assistkey/dev.equwal.assistkey.channel.AiKeyActivity
 *
 * Each press arrives as one launch, which is enough to count taps but says
 * nothing about release - so taps, and "hold Power, then the AI key", but no
 * press-and-hold and no combinations with the volume keys.
 */
class AiKeyActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val svc = ServiceHolder.service as? KeyFilterService

        when {
            // Inside an AI screen the key means "back to what I was doing".
            svc != null && svc.inAiScreen && License.active(this) &&
                dev.equwal.assistkey.device.Device.aiKeyReturns(this) -> svc.returnFromAiScreen()
            // Ahead of the licence check: the navigation lifeline lives here.
            svc != null && svc.takePowerCombo(HwKey.AI) -> Unit
            // Locked means the key behaves as the firmware intended.
            !License.active(this) -> ActionRouter.run(this, STOCK)
            else -> AiKeyTaps.press(this)
        }

        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }

    companion object {
        val STOCK = ActionSpec(ActionKind.LAUNCH_COMPONENT, Presets.STOCK_AI_KEY)
    }
}

/** Counts launches into taps, with the same rules the gesture engine uses. */
object AiKeyTaps {

    private val keys = setOf(HwKey.AI)
    private val handler = Handler(Looper.getMainLooper())
    private var count = 0
    private var pending: Runnable? = null

    fun press(activity: Activity) {
        val app = activity.applicationContext
        val max = Store.bindings(app).maxTaps(keys)
        if (max == 0) {
            // Nothing bound: be the stock key, and do it while still in front,
            // because a background launch would be refused.
            ActionRouter.run(activity, AiKeyActivity.STOCK)
            return
        }
        pending?.let { handler.removeCallbacks(it) }
        count++
        val n = count
        // The highest bound count cannot grow into anything, so it fires now;
        // anything lower has to wait out the multi-tap window.
        val delay = if (n >= max) SETTLE_MS else Store.timing(app).multiTapMs.toLong()
        val r = Runnable { fire(app, n) }
        pending = r
        handler.postDelayed(r, delay)
    }

    private fun fire(app: Context, n: Int) {
        count = 0
        pending = null
        val spec = Store.bindings(app)[Trigger(keys, GestureType.TAP, n)]
            ?: if (n == 1) AiKeyActivity.STOCK else return
        // The accessibility service may start activities from the background;
        // a plain application context may not.
        ActionRouter.run(ServiceHolder.service ?: app, spec)
    }

    /** Long enough for our own invisible window to be gone before Back or Home lands. */
    private const val SETTLE_MS = 150L
}
