package dev.equwal.assistkey.setup

import dev.equwal.assistkey.model.GestureType
import dev.equwal.assistkey.model.HwKey
import dev.equwal.assistkey.model.Trigger

/**
 * How a gesture can be made to work, and what that needs from the user.
 *
 * The user says what he wants: this button, pressed this way, does that. This
 * object turns the wish into the list of things that must be allowed for it,
 * so that no screen has to talk about services, roles or channels first.
 *
 * It has no Android types, so a test can run it with no device.
 */
object Route {

    /** One thing the user must allow. The order of the entries is the order to ask in. */
    enum class Need(val title: String, val why: String) {
        KEY_FILTER(
            "Let Rebind see the buttons",
            "Android calls this an accessibility service."
        ),
        ASSISTANT(
            "Let Rebind take the hold of Power",
            "Rebind stands in for the assistant app."
        ),
        CAMERA(
            "Let Rebind take the double press of Power",
            "Rebind stands in for the camera app. Your camera still works."
        ),
        SHELL_POWER(
            "Open the full Power button",
            "Shell access makes every Power press work."
        )
    }

    /** What is true of the device and the build at this moment. */
    data class Env(
        /** The app reads the Power button directly (shell access is on and in use). */
        val powerIsDirect: Boolean,
        /** This build can have shell access. The Google Play build cannot. */
        val shellSupported: Boolean = false
    )

    data class Plan(
        val needs: List<Need>,
        /** Null when the gesture can work. Otherwise the reason, in words for the user. */
        val blocked: String? = null
    ) {
        val possible: Boolean get() = blocked == null
    }

    /**
     * [actionNeedsFilter] is true for an action that the key filter carries out:
     * Back, Home, a swipe, a scroll, voice typing.
     */
    fun plan(trigger: Trigger, actionNeedsFilter: Boolean, env: Env): Plan {
        if (HwKey.SCREEN in trigger.keys) {
            val plainTap = trigger.keys.size == 1 && trigger.type == GestureType.TAP && trigger.count == 1
            // The key filter draws the button and carries out the action.
            return if (plainTap) Plan(listOf(Need.KEY_FILTER))
            else Plan(emptyList(), "The on-screen button has one gesture: a tap.")
        }
        val power = HwKey.POWER in trigger.keys
        if (power && trigger.keys.size > 1 && trigger != Trigger.powerThen((trigger.keys - HwKey.POWER).first())) {
            return Plan(emptyList(), "With Power, one way works: hold Power, then press the other button.")
        }
        if (!power || env.powerIsDirect) return Plan(listOf(Need.KEY_FILTER))

        val alone = trigger.keys.size == 1
        val door = when {
            alone && trigger.type == GestureType.HOLD -> Need.ASSISTANT
            alone && trigger.type == GestureType.TAP && trigger.count == 2 -> Need.CAMERA
            // Power held, then one other button: the hold opens the assistant door.
            !alone && trigger.keys.size == 2 && trigger.type == GestureType.TAP && trigger.count == 1 -> Need.ASSISTANT
            // No door of Android leads here. Shell access does, where the build has it.
            else -> return if (env.shellSupported) Plan(listOf(Need.KEY_FILTER, Need.SHELL_POWER)) else Plan(
                emptyList(),
                "Android does not show this Power press to apps. Hold and double press work."
            )
        }
        val second = !alone
        return Plan(if (actionNeedsFilter || second) listOf(Need.KEY_FILTER, door) else listOf(door))
    }

    /** The most buttons that one combination can have. */
    const val MAX_TOGETHER = 2

    /**
     * The selection after a tap on [key] in the drawing. A tap on a selected
     * button lets it go. The on-screen button cannot be part of a combination,
     * so it is always alone. A tap past [MAX_TOGETHER] drops the oldest button.
     */
    fun toggle(selection: List<HwKey>, key: HwKey): List<HwKey> = when {
        key in selection -> selection - key
        key == HwKey.SCREEN || HwKey.SCREEN in selection -> listOf(key)
        else -> (selection + key).takeLast(MAX_TOGETHER)
    }

    /** The ways to press [keys] that can work, for the choice the user is shown. */
    fun gestures(keys: Set<HwKey>, env: Env): List<Trigger> {
        // Tap, double tap, triple tap and hold come first. The long tap counts come last.
        val all = Trigger.allFor(keys).sortedBy { if (it.type == GestureType.TAP && it.count > 3) 1 else 0 }
        return all.filter { plan(it, false, env).possible }
    }
}
