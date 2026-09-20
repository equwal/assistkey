package dev.equwal.assistkey.channel

import android.app.Activity
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.widget.Toast
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
        val trigger = channel.trigger
        val spec = trigger?.let { Store.bindings(activity)[it] }

        if (spec == null) {
            Log.i(TAG, "channel " + channel.key + " fired with nothing bound")
            activity.finishAndVanish()
            return
        }

        if (ActionRouter.requiresAccessibility(spec)) {
            if (!ServiceHolder.isRunning) {
                Toast.makeText(
                    activity,
                    "Enable the AssistKey accessibility service to use this action",
                    Toast.LENGTH_SHORT
                ).show()
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
