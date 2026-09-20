package dev.equwal.assistkey.engine

import android.accessibilityservice.AccessibilityService
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import dev.equwal.assistkey.channel.Channel
import dev.equwal.assistkey.channel.Channels
import dev.equwal.assistkey.license.License
import dev.equwal.assistkey.license.PlayBilling
import dev.equwal.assistkey.model.ActionKind
import dev.equwal.assistkey.model.ActionSpec
import dev.equwal.assistkey.model.HwKey
import dev.equwal.assistkey.model.Trigger
import dev.equwal.assistkey.native.ViwoodsBridge
import dev.equwal.assistkey.route.ActionRouter
import dev.equwal.assistkey.route.ServiceHolder
import dev.equwal.assistkey.store.Store

/**
 * The accessibility channel: filters hardware key events and feeds them to
 * [GestureEngine].
 *
 * Power never arrives here - the framework consumes it upstream - so this
 * service only ever sees the AI key and the volume keys.
 */
class KeyFilterService : AccessibilityService(), GestureEngine.Host {

    private val handler = Handler(Looper.getMainLooper())
    private val pending = HashMap<String, Runnable>()
    private lateinit var engine: GestureEngine

    override fun onServiceConnected() {
        super.onServiceConnected()
        engine = GestureEngine(this, Store.timing(this))
        ServiceHolder.service = this
        // The service starts at boot and may run for weeks without the settings
        // screen ever opening, so it keeps the licence fresh on its own.
        PlayBilling.refresh(this)
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        cancelAll()
        ServiceHolder.service = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        cancelAll()
        ServiceHolder.service = null
        super.onDestroy()
    }

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val key = HwKey.fromCode(event.keyCode)
        val consumed = when {
            !Channels.isEnabled(this, Channel.ACCESSIBILITY) -> false
            // Locked means inert, not broken: every key goes to the firmware.
            !License.active(this) -> false
            key == null || !key.interceptable -> false
            event.action == KeyEvent.ACTION_DOWN ->
                engine.onDown(key, event.eventTime, event.repeatCount)
            event.action == KeyEvent.ACTION_UP -> engine.onUp(key, event.eventTime)
            else -> false
        }
        // Every event, not just the ones we act on: the tester screen is the
        // only way to find out whether a key reaches a filter on this firmware.
        // KeyLog drops it on the floor unless that screen is open.
        KeyLog.record(event, key?.label ?: ("Key " + event.keyCode), consumed)
        return consumed
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) { /* not used */ }

    /**
     * The settings screen lives in this same process, so new timing values can
     * be handed straight to the running engine - no service restart, and no
     * gesture lost mid-press.
     */
    fun reloadTiming() {
        if (::engine.isInitialized) engine.cfg = Store.timing(this)
    }

    override fun onInterrupt() {
        engine.onCancel()
    }

    // ---- GestureEngine.Host -------------------------------------------------

    private fun bindings() = Store.bindings(this)

    override fun maxTaps(keys: Set<HwKey>): Int = bindings().maxTaps(keys)

    override fun hasHold(keys: Set<HwKey>): Boolean = bindings().hasHold(keys)

    override fun isBound(trigger: Trigger): Boolean = bindings().isBound(trigger)

    override fun chordPartners(key: HwKey): Set<HwKey> = bindings().chordPartners(key)

    override fun fire(trigger: Trigger) {
        val spec = bindings()[trigger] ?: return
        ActionRouter.run(this, spec)
    }

    /**
     * We swallowed the key-down, so anything the system would normally have
     * done has to be reproduced by hand.
     */
    override fun passThrough(keys: Set<HwKey>, longPress: Boolean) {
        if (keys.size != 1) return
        when (keys.first()) {
            HwKey.VOL_UP -> adjust(AudioManager.ADJUST_RAISE)
            HwKey.VOL_DOWN -> adjust(AudioManager.ADJUST_LOWER)
            // Whatever the firmware would have opened, not a guess at it.
            HwKey.AI -> ActionRouter.run(
                this,
                ActionSpec(ActionKind.LAUNCH_COMPONENT, ViwoodsBridge.aiTarget(this))
            )
            HwKey.POWER -> Unit
        }
    }

    private fun adjust(direction: Int) {
        getSystemService(AudioManager::class.java)?.adjustSuggestedStreamVolume(
            direction,
            AudioManager.USE_DEFAULT_STREAM_TYPE,
            AudioManager.FLAG_SHOW_UI
        )
    }

    override fun schedule(token: String, delayMs: Long, action: () -> Unit) {
        cancel(token)
        val r = Runnable {
            pending.remove(token)
            action()
        }
        pending[token] = r
        handler.postDelayed(r, delayMs)
    }

    override fun cancel(token: String) {
        pending.remove(token)?.let { handler.removeCallbacks(it) }
    }

    private fun cancelAll() {
        pending.values.forEach { handler.removeCallbacks(it) }
        pending.clear()
    }
}
