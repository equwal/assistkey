package dev.equwal.assistkey.ui

import dev.equwal.assistkey.license.License

/**
 * The short state lines the hub shows: one line for each tile, and one line
 * for each binding in the "Your buttons" list.
 *
 * Every function here takes plain values and returns text. It holds no Android
 * type, so the wording is tested on the computer.
 */
object Summary {

    /** What a button does now, under its name in the drawing: "Back · Recent apps". */
    fun caption(actions: List<String>): String =
        if (actions.isEmpty()) "Not set" else actions.distinct().joinToString(" · ")

    /** How many gestures are bound, over how many buttons. */
    fun keys(keyCount: Int, boundCount: Int): String {
        if (boundCount == 0) return "Nothing bound yet"
        return count(boundCount, "action") + " on " + count(keyCount, "button")
    }

    /** The detection row: how many buttons the last detection found. */
    fun detected(keyCount: Int?): String = when {
        keyCount == null -> "Not run yet"
        keyCount == 0 -> "The device declares no named keys"
        else -> count(keyCount, "key") + " declared by the device"
    }

    /** The Power button, which has its own screen. */
    fun power(boundCount: Int): String =
        if (boundCount == 0) "Default behaviour" else count(boundCount, "action") + " bound"

    /** Which ways of getting around are switched on. */
    fun navigation(buttons: Boolean, gestures: Boolean, keys: Boolean): String {
        val parts = ArrayList<String>(3)
        if (buttons) parts.add("Bar")
        if (gestures) parts.add("Gestures")
        if (keys) parts.add("Power button")
        if (parts.isEmpty()) return "Nothing switched on"
        return parts.joinToString(" · ")
    }

    /** Whether voice typing can work, and what is missing if it cannot. */
    fun voice(microphone: Boolean, speechApps: Int, onDevice: Boolean): String = when {
        speechApps == 0 -> "Needs a speech app"
        !microphone -> "Needs the microphone"
        onDevice -> "Ready, on this device"
        else -> "Ready"
    }

    /** The home screen and the recent-apps cards. */
    fun homeAndRecents(homeOffered: Boolean, isDefault: Boolean): String = when {
        isDefault -> "Home screen in use"
        homeOffered -> "Home screen ready"
        else -> "Recent apps only"
    }

    /**
     * Setup shows the blocker first. Button remapping being off stops everything
     * else, so it is said before any count of permissions.
     */
    fun setup(keyRemapping: Boolean, granted: Int, total: Int): String = when {
        !keyRemapping -> "Button remapping is off"
        granted >= total -> "All set"
        else -> (total - granted).toString() + " to set up"
    }

    /** The licence chip in the app bar. Short, because it sits beside the name. */
    fun licenceChip(tier: License.Tier, trialDaysLeft: Int): String = when (tier) {
        License.Tier.LICENSED -> "Unlocked"
        License.Tier.NO_STORE -> "Free"
        License.Tier.BETA -> "Beta"
        License.Tier.TRIAL -> count(trialDaysLeft, "day") + " left"
        License.Tier.LOCKED -> "Locked"
    }

    /** "1 key", "3 keys". */
    fun count(n: Int, one: String): String =
        n.toString() + " " + (if (n == 1) one else one + "s")
}
