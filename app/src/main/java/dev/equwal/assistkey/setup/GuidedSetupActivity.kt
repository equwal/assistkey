package dev.equwal.assistkey.setup

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.LinearLayout
import android.widget.Toast
import dev.equwal.assistkey.channel.Channel
import dev.equwal.assistkey.channel.Channels
import dev.equwal.assistkey.device.Device
import dev.equwal.assistkey.engine.KeyFilterService
import dev.equwal.assistkey.menu.MenuEditActivity
import dev.equwal.assistkey.model.ActionKind
import dev.equwal.assistkey.model.ActionSpec
import dev.equwal.assistkey.model.GestureType
import dev.equwal.assistkey.model.GlobalAction
import dev.equwal.assistkey.model.HwKey
import dev.equwal.assistkey.model.Trigger
import dev.equwal.assistkey.route.ActionRouter
import dev.equwal.assistkey.route.ServiceHolder
import dev.equwal.assistkey.setup.Route.Need
import dev.equwal.assistkey.shell.PowerControl
import dev.equwal.assistkey.shell.Shell
import dev.equwal.assistkey.store.Store
import dev.equwal.assistkey.ui.AccessibilityDisclosure
import dev.equwal.assistkey.ui.ActionPickerActivity
import dev.equwal.assistkey.ui.PowerActivity
import dev.equwal.assistkey.ui.ShellActivity
import dev.equwal.assistkey.ui.TriggerListActivity
import dev.equwal.assistkey.ui.Ui
import dev.equwal.assistkey.ui.Ui.button
import dev.equwal.assistkey.ui.Ui.header
import dev.equwal.assistkey.ui.Ui.note
import dev.equwal.assistkey.ui.Ui.row
import dev.equwal.assistkey.ui.Ui.primaryButton
import dev.equwal.assistkey.voice.Dictation

/**
 * Set up one button by saying what you want, in four short steps.
 *
 *  1. Which button: tap it in the drawing. Two buttons make a combination.
 *     The main screen has the same drawing, and then this step is skipped.
 *  2. How you press it: tap, double tap, hold, and the longer tap counts.
 *  3. What it does.
 *  4. Allow what that needs - and only that.
 *
 * The user never chooses a service, a role or a channel. [Route] works out
 * which of them the wish needs, and step 4 asks for those by what they do.
 * Everything here ends in an ordinary binding, so the full-control screens
 * show and edit the same thing.
 */
class GuidedSetupActivity : Activity() {

    private enum class Step { BUTTON, PRESS, ACTION, ALLOW, DONE }

    private var step = Step.BUTTON
    private var keys: List<HwKey> = emptyList()
    private var trigger: Trigger? = null

    /** True when the main screen chose the buttons. Back from step 2 then leaves this screen. */
    private var fromHub = false

    /** True when another screen made the binding. This screen then only asks, and leaves. */
    private var askOnly = false

    private fun tokens(text: String?): List<HwKey> =
        text.orEmpty().split('+').mapNotNull(HwKey::fromToken)

    private val env: Route.Env get() = env(this)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedInstanceState?.let { s ->
            step = Step.valueOf(s.getString("step", Step.BUTTON.name))
            keys = tokens(s.getString("keys"))
            trigger = s.getString("trigger")?.let(Trigger::parse)
            fromHub = s.getBoolean("fromHub")
        } ?: intent.getStringExtra(EXTRA_ASK)?.let(Trigger::parse)?.let {
            // Another screen made this binding. Ask for what it needs, and nothing else.
            trigger = it
            keys = it.keys.toList()
            askOnly = true
            step = Step.ALLOW
        } ?: tokens(intent.getStringExtra(EXTRA_KEYS)).takeIf { it.isNotEmpty() }?.let {
            // The main screen chose the buttons already.
            keys = it
            fromHub = true
            step = Step.PRESS
        }
    }

    override fun onSaveInstanceState(out: Bundle) {
        super.onSaveInstanceState(out)
        out.putString("step", step.name)
        out.putString("keys", keys.joinToString("+") { it.token })
        out.putBoolean("fromHub", fromHub)
        trigger?.let { out.putString("trigger", it.id) }
    }

    override fun onResume() {
        super.onResume()
        Shell.connect(this)
        // Back from the action list or the menu editor: a binding now exists, or not.
        if (step == Step.ACTION && trigger?.let { Store.bindings(this).isBound(it) } == true) step = Step.ALLOW
        build()
    }

    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        when (step) {
            Step.BUTTON, Step.DONE -> finish()
            Step.ALLOW -> if (askOnly) finish() else go(Step.ACTION)
            Step.PRESS -> if (fromHub) finish() else go(Step.BUTTON)
            else -> { step = Step.entries[step.ordinal - 1]; build() }
        }
    }

    private fun go(next: Step) {
        step = next
        build()
    }

    private fun build() {
        val heading = when (step) {
            Step.BUTTON -> "Which button?"
            Step.PRESS -> keys.joinToString(" + ") { it.label }.ifEmpty { "Which button?" }
            Step.ACTION -> trigger?.label() ?: "What should it do?"
            Step.ALLOW -> "Almost there"
            Step.DONE -> "Done"
        }
        val col = Ui.page(this, heading)
        when (step) {
            Step.BUTTON -> whichButton(col)
            Step.PRESS -> press(col)
            Step.ACTION -> action(col)
            Step.ALLOW -> allow(col)
            Step.DONE -> done(col)
        }
    }

    // ---- 1. which button ------------------------------------------------------------------

    private fun whichButton(col: LinearLayout) {
        col.note("Tap two for a combination.")
        val view = DeviceView(this).apply {
            keys = drawn(this@GuidedSetupActivity)
            selected = this@GuidedSetupActivity.keys
            onSelect = { this@GuidedSetupActivity.keys = it; build() }
        }
        col.addView(view)
        // A device can have buttons the drawing does not know. The user names the button.
        val others = HwKey.interceptable.filterNot { it in view.keys }
        if (others.isNotEmpty()) {
            col.row("My button is not in the drawing", keys.filter { it in others }.joinToString { it.label }.ifEmpty { null }) {
                Ui.pick(this, "Which button?", others.map { it.label }) { i ->
                    keys = Route.toggle(keys, others[i])
                    build()
                }
            }
        }
        if (keys.isNotEmpty()) {
            col.primaryButton("Next: " + keys.joinToString(" + ") { it.label }) { go(Step.PRESS) }
        }
    }

    // ---- 2. how it is pressed ---------------------------------------------------------------

    private fun press(col: LinearLayout) {
        if (keys.isEmpty()) return go(Step.BUTTON)
        val set = keys.toSet()
        col.header("How do you press it?")
        val b = Store.bindings(this)
        val offered = Route.gestures(set, env)
        offered.forEach { t ->
            val state = b.raw(t).describe().takeIf { b.isBound(t) }?.let { "Now: $it" }
                ?: "Needs shell access".takeIf { Need.SHELL_POWER in Route.plan(t, false, env).needs }
            col.row(gestureName(t), state) {
                trigger = t
                go(Step.ACTION)
            }
        }
        when {
            HwKey.SCREEN in set -> col.note("A small button floats over every app. Drag to move it.")
            HwKey.POWER in set && set.size > 1 ->
                col.note("Hold Power, then press the other button.")
            HwKey.POWER in set && !env.powerIsDirect && !env.shellSupported ->
                col.note("A tap cannot reach any app. Hold and double tap work.")
        }
        if (HwKey.SCREEN !in set) {
            col.row("Advanced", "Every gesture of this button") {
                startActivity(
                    if (HwKey.POWER in set) Intent(this, PowerActivity::class.java)
                    else TriggerListActivity.intent(this, set)
                )
            }
        }
    }

    private fun gestureName(t: Trigger): String = when {
        HwKey.POWER in t.keys && t.keys.size > 1 -> "Hold Power, then press " + (t.keys - HwKey.POWER).first().label
        t.type == GestureType.HOLD -> "Press and hold"
        t.count == 1 -> "Tap"
        t.count == 2 -> "Double tap"
        t.count == 3 -> "Triple tap"
        else -> t.count.toString() + " taps"
    }

    // ---- 3. what it does ------------------------------------------------------------------------

    private fun action(col: LinearLayout) {
        val t = trigger ?: return go(Step.PRESS)
        col.header("What should it do?")

        fun global(a: GlobalAction) = ActionSpec(ActionKind.GLOBAL, a.name, a.label)
        val common = listOf(
            global(GlobalAction.BACK),
            global(GlobalAction.HOME_CLOSE_IME),
            ActionSpec(ActionKind.LAUNCH_COMPONENT, packageName + "/dev.equwal.assistkey.home.RecentsActivity", "Recent apps"),
            ActionSpec(ActionKind.SWIPE, "left", "Next page"),
            ActionSpec(ActionKind.SWIPE, "right", "Previous page"),
            ActionSpec(ActionKind.VOICE, "", "Voice typing"),
            global(GlobalAction.LOCK_SCREEN),
            global(GlobalAction.SCREENSHOT),
            ActionSpec(ActionKind.NONE, "", "Nothing - switch the button off")
        )
        common.forEach { spec ->
            col.row(spec.describe(), null) {
                Store.bind(this, t, spec)
                go(Step.ALLOW)
            }
        }
        col.row("A menu of actions", "One press, many choices") { startActivity(MenuEditActivity.intent(this, t)) }
        col.row("Advanced", "Every action") {
            startActivity(ActionPickerActivity.intent(this, t).putExtra(ActionPickerActivity.EXTRA_NO_ASK, true))
        }
    }

    // ---- 4. allow what it needs --------------------------------------------------------------------

    private fun satisfied(n: Need): Boolean = satisfied(this, n)

    private fun ask(n: Need) {
        when (n) {
            Need.KEY_FILTER -> {
                Channels.setEnabled(this, Channel.ACCESSIBILITY, true)
                AccessibilityDisclosure.show(this, onAgree = {
                    Channels.safeStart(this, Channels.claimIntent(this, Channel.ACCESSIBILITY))
                })
            }
            Need.ASSISTANT -> {
                Channels.setEnabled(this, Channel.ASSISTANT, true)
                Channels.safeStart(this, Channels.claimIntent(this, Channel.ASSISTANT))
            }
            Need.CAMERA -> {
                Channels.setEnabled(this, Channel.CAMERA, true)
                Toast.makeText(this, "Choose Camera app, then pick Rebind", Toast.LENGTH_LONG).show()
                Channels.safeStart(this, Channels.claimIntent(this, Channel.CAMERA))
            }
            Need.SHELL_POWER -> {
                PowerControl.setWanted(this, true)
                startActivity(Intent(this, ShellActivity::class.java))
            }
        }
    }

    private fun allow(col: LinearLayout) {
        val t = trigger ?: return go(Step.PRESS)
        val spec = Store.bindings(this)[t] ?: return go(Step.ACTION)
        val needsFilter = ActionRouter.requiresAccessibility(spec) || spec.kind == ActionKind.MENU
        val plan = Route.plan(t, needsFilter, env)

        // The switches that go with the route, set without a question.
        if (HwKey.POWER in t.keys && env.powerIsDirect) (ServiceHolder.service as? KeyFilterService)?.syncPower()
        Channels.setEnabled(this, Channel.ACCESSIBILITY, true)

        val missing = plan.needs.filterNot(::satisfied)
        val microphone = spec.kind == ActionKind.VOICE && !Dictation.hasMicrophone(this)
        if (plan.possible && missing.isEmpty() && !microphone) return if (askOnly) finish() else go(Step.DONE)

        plan.blocked?.let {
            col.note(it)
            col.button("Press it another way") { go(Step.PRESS) }
            return
        }
        col.note(t.label() + " will do: " + spec.describe() + ". Allow this first:")
        missing.forEach { n -> col.row(n.title, n.why) { ask(n) } }
        if (microphone) {
            col.row("Allow the microphone", "For Voice typing") {
                requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1)
            }
        }
        if (Need.KEY_FILTER in missing) {
            col.note("If the switch turns itself off, allow restricted settings in App info.")
            col.button("Open App info") { Channels.safeStart(this, Channels.appInfoIntent(this)) }
        }
    }

    override fun onRequestPermissionsResult(code: Int, permissions: Array<out String>, results: IntArray) {
        super.onRequestPermissionsResult(code, permissions, results)
        build()
    }

    // ---- done -------------------------------------------------------------------------------------------

    private fun done(col: LinearLayout) {
        val t = trigger
        val spec = t?.let { Store.bindings(this)[it] }
        if (t != null && spec != null) col.note(t.label() + " now does: " + spec.describe() + ". Try it.")
        if (spec?.kind == ActionKind.VOICE && Dictation.engines(this).none(Dictation::isOnDevice)) {
            col.note("See Voice typing for an on-device speech app.")
        }
        // Some firmware sends the double press of Power to the wallet app, not to the camera app.
        val powerDouble = t == Trigger(setOf(HwKey.POWER), GestureType.TAP, 2) && !env.powerIsDirect
        if (powerDouble && !Channels.isSatisfied(this, Channel.WALLET)) {
            col.row("The double tap does nothing?", "Take the wallet place too") {
                Channels.setEnabled(this, Channel.WALLET, true)
                Channels.safeStart(this, Channels.claimIntent(this, Channel.WALLET))
            }
        }
        col.button("Set up another button") {
            keys = emptyList()
            trigger = null
            fromHub = false
            go(Step.BUTTON)
        }
        col.primaryButton("Finish") { finish() }
    }

    companion object {
        private const val EXTRA_KEYS = "keys"
        private const val EXTRA_ASK = "ask"

        /**
         * For a screen that has just made a binding for [t]. Opens the allow
         * step when the binding needs something the user has not allowed yet.
         */
        fun askIfMissing(a: Activity, t: Trigger) {
            val spec = Store.bindings(a)[t] ?: return
            val needsFilter = ActionRouter.requiresAccessibility(spec) || spec.kind == ActionKind.MENU
            val plan = Route.plan(t, needsFilter, env(a))
            val microphone = spec.kind == ActionKind.VOICE && !Dictation.hasMicrophone(a)
            if (plan.possible && plan.needs.all { satisfied(a, it) } && !microphone) return
            a.startActivity(Intent(a, GuidedSetupActivity::class.java).putExtra(EXTRA_ASK, t.id))
        }

        private fun env(c: android.content.Context): Route.Env =
            Route.Env(powerIsDirect = Shell.ready && PowerControl.wanted(c), shellSupported = Shell.SUPPORTED)

        private fun satisfied(c: android.content.Context, n: Need): Boolean = when (n) {
            Need.KEY_FILTER -> Channels.isSatisfied(c, Channel.ACCESSIBILITY)
            Need.ASSISTANT -> Channels.isSatisfied(c, Channel.ASSISTANT)
            Need.CAMERA -> Channels.isSatisfied(c, Channel.CAMERA)
            Need.SHELL_POWER -> env(c).powerIsDirect
        }

        /** With [keys], the flow starts at "how do you press it". */
        fun intent(a: Activity, keys: List<HwKey> = emptyList()): Intent =
            Intent(a, GuidedSetupActivity::class.java).putExtra(EXTRA_KEYS, keys.joinToString("+") { it.token })

        /** The buttons in the drawing, from the top down. */
        fun drawn(c: android.content.Context): List<HwKey> = listOf(HwKey.POWER) + Device.keys(c) + HwKey.SCREEN
    }
}
