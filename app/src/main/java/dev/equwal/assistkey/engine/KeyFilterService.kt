package dev.equwal.assistkey.engine

import android.accessibilityservice.AccessibilityService
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.KeyEvent
import android.view.accessibility.AccessibilityEvent
import dev.equwal.assistkey.channel.Channel
import dev.equwal.assistkey.channel.Channels
import dev.equwal.assistkey.license.License
import dev.equwal.assistkey.license.PlayBilling
import dev.equwal.assistkey.model.ActionKind
import dev.equwal.assistkey.model.ActionSpec
import dev.equwal.assistkey.model.GlobalAction
import dev.equwal.assistkey.model.HwKey
import dev.equwal.assistkey.model.Trigger
import dev.equwal.assistkey.native.ViwoodsBridge
import dev.equwal.assistkey.route.ActionRouter
import dev.equwal.assistkey.route.ServiceHolder
import dev.equwal.assistkey.shell.PowerControl
import dev.equwal.assistkey.shell.Shell
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
        dev.equwal.assistkey.store.AutoBackup.watch(this)
        // The service starts at boot and may run for weeks without the settings
        // screen ever opening, so it keeps the licence fresh on its own.
        PlayBilling.refresh(this) { syncPower() }

        Shell.onChange(shellChanged)
        Store.onBindingsChanged = { syncPower(); handler.post { syncScreenButton() } }
        registerReceiver(
            screenState,
            android.content.IntentFilter(android.content.Intent.ACTION_SCREEN_ON).apply {
                addAction(android.content.Intent.ACTION_SCREEN_OFF)
            }
        )
        Shell.connect(this)
        syncPower()
        syncScreenButton()
    }

    // ---- the on-screen button --------------------------------------------------------------

    private val screenTap = Trigger(setOf(HwKey.SCREEN), dev.equwal.assistkey.model.GestureType.TAP, 1)
    private val screenButton by lazy { ScreenButton(this) { fire(screenTap) } }

    /** The button floats only while it has a binding, and only while the app may act. */
    fun syncScreenButton() {
        // "Nothing" on a physical key stops the key. On this button it means: no button.
        val action = bindings()[screenTap]?.kind
        val wanted = action != null && action != ActionKind.NONE &&
            Channels.isEnabled(this, Channel.ACCESSIBILITY) && License.active(this)
        if (wanted) screenButton.show() else screenButton.hide()
    }

    // ---- the Power button, when there is shell access -----------------------------

    private val shellChanged: () -> Unit = { syncPower() }
    private var powerIgnoredUntilUp = false

    /**
     * True from the moment the screen goes off until it is on again. The
     * broadcast for "off" arrives before the device sleeps, so this is a better
     * witness than PowerManager.isInteractive, which the firmware may already
     * have flipped by the time a Power press reaches this process.
     */
    private var screenOff = false
    private var wokeAt = 0L

    private val screenState = object : android.content.BroadcastReceiver() {
        override fun onReceive(c: android.content.Context?, i: android.content.Intent?) {
            if (i?.action == android.content.Intent.ACTION_SCREEN_OFF) {
                screenOff = true
            } else {
                screenOff = false
                wokeAt = SystemClock.uptimeMillis()
                dev.equwal.assistkey.display.ExtraDim.reapply(this@KeyFilterService)
            }
            powerIgnoredUntilUp = true
            if (::engine.isInitialized) engine.onCancel()
        }
    }

    fun syncPower() = PowerControl.sync(this, serviceRunning = ServiceHolder.service === this, ::onRawPower)

    /**
     * A Power press on a sleeping or locked device has one job: wake it and
     * unlock it. It is never a gesture. The firmware normally wakes the device
     * by itself, but the button is ours now, so this does not rely on that: the
     * wake and the keyguard dismissal are sent through the shell as well.
     */
    private fun onRawPower(down: Boolean, at: Long) {
        if (!::engine.isInitialized) return
        if (!down) {
            if (powerIgnoredUntilUp) powerIgnoredUntilUp = false else engine.onUp(HwKey.POWER, at)
            return
        }
        val asleep = screenOff ||
            getSystemService(android.os.PowerManager::class.java)?.isInteractive == false
        val locked = getSystemService(android.app.KeyguardManager::class.java)?.isKeyguardLocked == true
        val justWoke = SystemClock.uptimeMillis() - wokeAt < WAKE_GRACE_MS
        if (asleep || locked || justWoke) {
            powerIgnoredUntilUp = true
            engine.onCancel()
            if (asleep || locked) Shell.run("input keyevent KEYCODE_WAKEUP; wm dismiss-keyguard")
            return
        }
        powerIgnoredUntilUp = false
        engine.onDown(HwKey.POWER, at, 0)
    }

    private fun stopPower() {
        Shell.removeOnChange(shellChanged)
        Store.onBindingsChanged = null
        runCatching { unregisterReceiver(screenState) }
        ServiceHolder.service = null
        syncPower() // with no service running this hands Power back to the firmware
    }

    override fun onUnbind(intent: android.content.Intent?): Boolean {
        cancelAll()
        stopPower()
        screenButton.hide()
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        cancelAll()
        stopPower()
        super.onDestroy()
    }

    // ---- hold Power, then press a key --------------------------------------

    private var armedUntil = 0L
    private var lifelineOnly = false
    private var holdFallback: Runnable? = null
    private val swallowed = HashSet<HwKey>()

    /**
     * Called when the firmware reports a Power hold. For [POWER_COMBO_WINDOW_MS]
     * the next key press is read as a Power combination. If none comes, the
     * plain hold action runs - late by exactly that window, which is the price
     * of having combinations at all and is only paid when one is bound.
     *
     * [lifeline] is the locked state. A reader set up with no button bar and no
     * gestures is navigated entirely from here, and must not become a brick
     * because a trial ran out: Back, Home and Recents always work.
     */
    fun armPowerCombo(plainHold: ActionSpec?, lifeline: Boolean) {
        holdFallback?.let { handler.removeCallbacks(it) }
        armedUntil = SystemClock.uptimeMillis() + POWER_COMBO_WINDOW_MS
        lifelineOnly = lifeline
        val r = Runnable {
            armedUntil = 0L
            holdFallback = null
            if (!lifeline) plainHold?.let { ActionRouter.run(this, it) }
        }
        holdFallback = r
        handler.postDelayed(r, POWER_COMBO_WINDOW_MS)
    }

    private fun powerCombo(event: KeyEvent, key: HwKey): Boolean {
        if (event.action == KeyEvent.ACTION_UP) return swallowed.remove(key)
        if (event.action != KeyEvent.ACTION_DOWN) return false
        if (key in swallowed) return true // auto-repeat of a press already taken
        if (!takePowerCombo(key)) return false
        swallowed.add(key)
        return true
    }

    /**
     * If a Power hold is waiting for its second key and [key] is bound as one,
     * run it and report true. Public because the AI key, once its firmware hook
     * points at this app, arrives as an activity launch rather than a key event.
     */
    fun takePowerCombo(key: HwKey): Boolean {
        if (SystemClock.uptimeMillis() >= armedUntil) return false
        val spec = bindings().powerCombo(key) ?: return false
        if (lifelineOnly && !isLifeline(spec)) return false

        armedUntil = 0L
        holdFallback?.let { handler.removeCallbacks(it) }
        holdFallback = null
        // A beat, so that whichever invisible window of ours is in front has
        // gone before Back or Home lands on it.
        handler.postDelayed({ ActionRouter.run(this, spec) }, 150L)
        return true
    }

    private fun isLifeline(spec: ActionSpec): Boolean =
        spec.kind == ActionKind.GLOBAL && spec.payload in LIFELINE

    override fun onKeyEvent(event: KeyEvent): Boolean {
        val key = HwKey.fromCode(event.keyCode)
        if (key != null && event.action == KeyEvent.ACTION_DOWN && event.repeatCount == 0) {
            dev.equwal.assistkey.device.Device.noteSeen(this, key)
        }
        val consumed = when {
            !Channels.isEnabled(this, Channel.ACCESSIBILITY) -> false
            key != null && License.active(this) && aiKeyReturn(event, key) -> true
            // Ahead of the licence check on purpose - see armPowerCombo.
            key != null && key.interceptable && powerCombo(event, key) -> true
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
        if (key == null && KeyLog.recording && event.action == KeyEvent.ACTION_DOWN) {
            dev.equwal.assistkey.device.Device.noteUnknown(
                this, event.keyCode, event.scanCode, event.device?.name
            )
        }
        return consumed
    }

    // ---- the AI key, inside an AI screen ------------------------------------------

    /** True while the Viwoods AI screen or crop screen is in front. */
    var inAiScreen = false
        private set

    /** The package in front before the AI screen opened. Held in memory only. */
    private var lastApp: String? = null
    private val aiSwallowed = HashSet<HwKey>()

    /**
     * Window changes are watched for one purpose: to know which app to go back
     * to. Only the package name is kept, only the latest one, and only in
     * memory.
     */
    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return
        val pkg = event.packageName?.toString() ?: return
        val cls = event.className?.toString()
        when {
            // Before the line that skips the system UI: stock Android has its recents there.
            dev.equwal.assistkey.device.Device.isSystemRecents(pkg, cls) -> swapInInkRecents()
            pkg == packageName || pkg == "android" || pkg == "com.android.systemui" -> Unit
            dev.equwal.assistkey.device.Device.isAiScreen(pkg, cls) -> inAiScreen = true
            // Dialogs and keyboards report window changes too; only activities count.
            cls != null && isActivity(pkg, cls) -> {
                inAiScreen = false
                lastApp = pkg
            }
        }
    }

    /**
     * The Recents button of the bar, the swipe, and the Recents action all open
     * the recent-apps screen of the system. Where the user has Ink Recents, this
     * closes that screen and opens Ink Recents. Android has no other way to put
     * an app in that place.
     */
    private fun swapInInkRecents() {
        if (!License.active(this) || !dev.equwal.assistkey.device.Device.inkRecentsForSystem(this)) return
        val open = android.content.Intent(dev.equwal.assistkey.home.RecentsActivity.ACTION_OPEN)
            .setPackage(dev.equwal.assistkey.home.RecentsActivity.INK_RECENTS)
            .addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK)
        if (open.resolveActivity(packageManager) == null) return
        // Back first, so that the screen of the system is not left under Ink Recents.
        performGlobalAction(GLOBAL_ACTION_BACK)
        handler.postDelayed({ runCatching { startActivity(open) } }, 120L)
    }

    private fun isActivity(pkg: String, cls: String): Boolean = runCatching {
        packageManager.getActivityInfo(android.content.ComponentName(pkg, cls), 0); true
    }.getOrDefault(false)

    /**
     * Leaves the AI screen for the app that was in use before it. The firmware
     * reacts to the AI key by itself and may open its AI screen again, so this
     * waits for that to land first.
     */
    fun returnFromAiScreen() {
        handler.postDelayed({
            val pkg = lastApp
            val launch = pkg?.let { packageManager.getLaunchIntentForPackage(it) }
            val isHome = pkg != null && packageManager.resolveActivity(
                android.content.Intent(android.content.Intent.ACTION_MAIN)
                    .addCategory(android.content.Intent.CATEGORY_HOME),
                0
            )?.activityInfo?.packageName == pkg
            if (launch == null || isHome) {
                performGlobalAction(GLOBAL_ACTION_HOME)
            } else {
                // The same intent the launcher sends: it brings the task back as it was.
                startActivity(
                    launch.addFlags(
                        android.content.Intent.FLAG_ACTIVITY_NEW_TASK or
                            android.content.Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED
                    )
                )
            }
        }, AI_RETURN_DELAY_MS)
    }

    private fun aiKeyReturn(event: KeyEvent, key: HwKey): Boolean {
        if (key != HwKey.AI) return false
        if (event.action == KeyEvent.ACTION_UP) return aiSwallowed.remove(key)
        if (key in aiSwallowed) return true
        if (!inAiScreen || !dev.equwal.assistkey.device.Device.aiKeyReturns(this)) return false
        aiSwallowed.add(key)
        returnFromAiScreen()
        return true
    }

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

    override fun maxTaps(keys: Set<HwKey>): Int = bindings().maxTaps(keys, PowerControl.active)

    override fun hasHold(keys: Set<HwKey>): Boolean = bindings().hasHold(keys, PowerControl.active)

    override fun isBound(trigger: Trigger): Boolean = bindings().isBound(trigger)

    override fun chordPartners(key: HwKey): Set<HwKey> =
        bindings().chordPartners(key, PowerControl.active)

    companion object {
        /** How long after a Power hold a key press still counts as a combination. */
        const val POWER_COMBO_WINDOW_MS = 1000L

        /** Longer than the firmware takes to open its AI screen on an AI key press. */
        private const val AI_RETURN_DELAY_MS = 350L

        /** A press this soon after the screen came on is the press that woke it. */
        private const val WAKE_GRACE_MS = 1000L

        private val LIFELINE = setOf(
            GlobalAction.BACK.name, GlobalAction.HOME.name, GlobalAction.RECENTS.name
        )
    }

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
            // An unbound Power gesture does what Power always did.
            HwKey.POWER -> performGlobalAction(GLOBAL_ACTION_LOCK_SCREEN)
            // A swallowed press cannot be replayed to the app in front. Keys
            // with a system-wide meaning are handled above; the rest are lost,
            // which is why a key with nothing bound is never swallowed at all.
            else -> Unit
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
