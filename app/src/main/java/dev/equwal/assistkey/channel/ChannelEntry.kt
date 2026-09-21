package dev.equwal.assistkey.channel

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
import dev.equwal.assistkey.engine.KeyFilterService
import dev.equwal.assistkey.license.License
import dev.equwal.assistkey.route.ActionRouter
import dev.equwal.assistkey.route.ServiceHolder
import dev.equwal.assistkey.store.Store

/**
 * Shared body of the three impersonation entry points. They differ only in
 * which intent filters bring them to life.
 */
object ChannelEntry {

    private const val TAG = "AssistKey"

    /**
     * Ordering matters. A global accessibility action such as Back or Recents
     * applies to whatever window is on top, so we have to be gone first;
     * anything else we run while still foreground, otherwise the background
     * activity launch restrictions would drop it.
     */
    fun handle(activity: Activity, channel: Channel) {
        val licensed = License.active(activity)

        // A Power hold with combinations bound does not act yet: it opens a
        // short window in which the next key press decides what happens.
        if (channel == Channel.ASSISTANT && Store.bindings(activity).hasPowerCombos) {
            val svc = ServiceHolder.service as? KeyFilterService
            if (svc != null) {
                val plain = channel.trigger?.let { Store.bindings(activity)[it] }
                svc.armPowerCombo(plain, lifeline = !licensed)
                activity.finishAndVanish()
                return
            }
        }

        if (!licensed) {
            Toast.makeText(
                activity,
                "Rebind is locked. Open the app.",
                Toast.LENGTH_SHORT
            ).show()
            activity.finishAndVanish()
            return
        }

        val trigger = channel.trigger
        val spec = trigger?.let { Store.bindings(activity)[it] }

        if (spec == null) {
            Log.i(TAG, "channel " + channel.key + " fired with nothing bound")
            activity.finishAndVanish()
            return
        }

        if (ActionRouter.requiresAccessibility(spec)) {
            if (!ServiceHolder.isRunning) {
                // The user tried the button, and something is not allowed yet. Ask now.
                dev.equwal.assistkey.setup.GuidedSetupActivity.askIfMissing(activity.applicationContext, trigger)
                activity.finishAndVanish()
                return
            }
            val app = activity.applicationContext
            activity.finishAndVanish()
            Handler(Looper.getMainLooper()).postDelayed({ ActionRouter.run(app, spec) }, 150L)
        } else {
            ActionRouter.run(activity, spec)
            activity.finishAndVanish()
        }
    }

    /** No window was ever drawn; make sure no transition animates one in. */
    private fun Activity.finishAndVanish() {
        finish()
        @Suppress("DEPRECATION")
        overridePendingTransition(0, 0)
    }
}
