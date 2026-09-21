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
 *  1. Which button: tap it in the drawing.
 *  2. How you press it: tap, double tap, hold.
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
    private var key: HwKey? = null
    private var trigger: Trigger? = null

    private val env: Route.Env
        get() = Route.Env(powerIsDirect = Shell.ready && PowerControl.wanted(this))

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        savedInstanceState?.let { s ->
            step = Step.valueOf(s.getString("step", Step.BUTTON.name))
            key = s.getString("key")?.let(HwKey::fromToken)
            trigger = s.getString("trigger")?.let(Trigger::parse)
        }
    }

    override fun onSaveInstanceState(out: Bundle) {
        super.onSaveInstanceState(out)
        out.putString("step", step.name)
        key?.let { out.putString("key", it.token) }
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
            Step.PRESS -> key?.label ?: "Which button?"
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
        col.note("Tap the button in the drawing.")
        val view = DeviceView(this).apply {
            keys = listOf(HwKey.POWER) + Device.keys(this@GuidedSetupActivity)
            selected = key
            onSelect = { key = it; build() }
        }
        col.addView(view)
        // A device can have buttons the drawing does not know. The user names the button.
        val others = HwKey.interceptable.filterNot { it in view.keys }
        if (others.isNotEmpty()) {
            col.row("My button is not in the drawing", key?.takeIf { it in others }?.label) {
                Ui.pick(this, "Which button?", others.map { it.label }) { i -> key = others[i]; build() }
            }
        }
        key?.let { k -> col.primaryButton("Next: " + k.label) { go(Step.PRESS) } }
    }

    // ---- 2. how it is pressed ---------------------------------------------------------------

    private fun press(col: LinearLayout) {
        val k = key ?: return go(Step.BUTTON)
        col.header("How do you press it?")
        val b = Store.bindings(this)
        val offered = Route.gestures(setOf(k), env)
        offered.forEach { t ->
            col.row(gestureName(t), b.raw(t).describe().takeIf { b.isBound(t) }?.let { "Now: $it" }) {
                trigger = t
                go(Step.ACTION)
            }
        }
        if (offered.size < 3) {
            col.note("Android does not show a single tap of Power to apps. Hold and double press work.")
        }
        col.note("More ways to press, and two buttons together, are under Advanced > Full control.")
    }

    private fun gestureName(t: Trigger): String = when {
        t.type == GestureType.HOLD -> "Press and hold"
        t.count == 2 -> if (HwKey.POWER in t.keys && !env.powerIsDirect) "Double press" else "Double tap"
        else -> "Tap"
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
            ActionSpec(ActionKind.NONE, "", "Nothing - switch the button off")
        )
        common.forEach { spec ->
            col.row(spec.describe(), null) {
                Store.bind(this, t, spec)
                go(Step.ALLOW)
            }
        }
        col.row("A menu of actions", "One press, many choices") { startActivity(MenuEditActivity.intent(this, t)) }
        col.row("Open an app, or something else", "The full list of actions") {
            startActivity(ActionPickerActivity.intent(this, t))
        }
    }

    // ---- 4. allow what it needs --------------------------------------------------------------------

    private fun satisfied(n: Need): Boolean = when (n) {
        Need.KEY_FILTER -> Channels.isSatisfied(this, Channel.ACCESSIBILITY)
        Need.ASSISTANT -> Channels.isSatisfied(this, Channel.ASSISTANT)
        Need.CAMERA -> Channels.isSatisfied(this, Channel.CAMERA)
    }

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
                Toast.makeText(this, "Choose Camera app, then pick AssistKey", Toast.LENGTH_LONG).show()
                Channels.safeStart(this, Channels.claimIntent(this, Channel.CAMERA))
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
        if (plan.possible && missing.isEmpty() && !microphone) return go(Step.DONE)

        plan.blocked?.let {
            col.note(it)
            col.button("Choose another way to press") { go(Step.PRESS) }
            return
        }
        col.note(t.label() + " will do: " + spec.describe() + ". Android asks you to allow this first:")
        missing.forEach { n -> col.row(n.title, n.why) { ask(n) } }
        if (microphone) {
            col.row("Allow the microphone", "For Voice typing. A speech app on this device does the listening.") {
                requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 1)
            }
        }
        col.note("Tap each one. When you come back here, it is ticked off.")
        if (Need.KEY_FILTER in missing) {
            col.note("If the switch turns itself back off: open App info, the three-dot menu, Allow restricted settings.")
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
            col.note("Voice typing works best with a speech app that listens on the device. See Voice typing on the main screen.")
        }
        col.button("Set up another button") {
            key = null
            trigger = null
            go(Step.BUTTON)
        }
        col.primaryButton("Finish") { finish() }
    }

    companion object {
        fun intent(a: Activity): Intent = Intent(a, GuidedSetupActivity::class.java)
    }
}
