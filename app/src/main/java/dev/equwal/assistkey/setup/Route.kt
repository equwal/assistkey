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
            "Let AssistKey see the buttons",
            "Android calls this an accessibility service. AssistKey uses it to see button presses and to do the action."
        ),
        ASSISTANT(
            "Make AssistKey the assistant app",
            "Android sends a held Power button to the assistant app. That is how the press reaches AssistKey."
        ),
        CAMERA(
            "Make AssistKey the camera app",
            "Android sends a double press of Power to the camera app. That is how the press reaches AssistKey."
        )
    }

    /** What is true of the device and the build at this moment. */
    data class Env(
        /** The app reads the Power button directly (shell access is on and in use). */
        val powerIsDirect: Boolean
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
        val power = HwKey.POWER in trigger.keys
        if (!power || env.powerIsDirect) return Plan(listOf(Need.KEY_FILTER))

        val alone = trigger.keys.size == 1
        val door = when {
            alone && trigger.type == GestureType.HOLD -> Need.ASSISTANT
            alone && trigger.type == GestureType.TAP && trigger.count == 2 -> Need.CAMERA
            // Power held, then one other button: the hold opens the assistant door.
            !alone && trigger.keys.size == 2 && trigger.type == GestureType.TAP && trigger.count == 1 -> Need.ASSISTANT
            else -> return Plan(
                emptyList(),
                "Android does not show this Power press to apps. Hold and double press work."
            )
        }
        val second = !alone
        return Plan(if (actionNeedsFilter || second) listOf(Need.KEY_FILTER, door) else listOf(door))
    }

    /** The ways to press [keys] that can work, for the choice the user is shown. */
    fun gestures(keys: Set<HwKey>, env: Env): List<Trigger> {
        val all = listOf(
            Trigger(keys, GestureType.TAP, 1),
            Trigger(keys, GestureType.TAP, 2),
            Trigger(keys, GestureType.HOLD)
        )
        return all.filter { plan(it, false, env).possible }
    }
}
