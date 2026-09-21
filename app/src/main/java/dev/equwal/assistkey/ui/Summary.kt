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

    /** "Power, hold" - the gesture, in the words a person would use. */
    fun gesture(keys: List<String>, hold: Boolean, taps: Int): String {
        val what = when {
            hold -> "hold"
            taps <= 1 -> "tap"
            taps == 2 -> "double tap"
            taps == 3 -> "triple tap"
            else -> taps.toString() + " taps"
        }
        return keys.joinToString(" + ") + ", " + what
    }

    /** "Power, hold: Home" - one line of the "Your buttons" list. */
    fun binding(gesture: String, action: String): String = gesture + ": " + action

    /** How many gestures are bound, over how many keys. */
    fun keys(keyCount: Int, boundCount: Int): String {
        if (boundCount == 0) return "Nothing bound yet"
        return count(boundCount, "action") + " on " + count(keyCount, "key")
    }

    /** The Power button, which has its own screen. */
    fun power(boundCount: Int): String =
        if (boundCount == 0) "Default behaviour" else count(boundCount, "action") + " bound"

    /** Which ways of getting around are switched on. */
    fun navigation(buttons: Boolean, gestures: Boolean, keys: Boolean): String {
        val parts = ArrayList<String>(3)
        if (buttons) parts.add("Buttons")
        if (gestures) parts.add("Gestures")
        if (keys) parts.add("Power key")
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
     * Setup shows the blocker first. Key remapping being off stops everything
     * else, so it is said before any count of permissions.
     */
    fun setup(keyRemapping: Boolean, granted: Int, total: Int): String = when {
        !keyRemapping -> "Key remapping is off"
        granted >= total -> "All set"
        else -> (total - granted).toString() + " to set up"
    }

    /** The licence chip in the app bar. Short, because it sits beside the name. */
    fun licenceChip(tier: License.Tier, trialDaysLeft: Int): String = when (tier) {
        License.Tier.LICENSED -> "Unlocked"
        License.Tier.BETA -> "Beta"
        License.Tier.TRIAL -> count(trialDaysLeft, "day") + " left"
        License.Tier.LOCKED -> "Locked"
    }

    /** The licence line on its own screen row. */
    fun licence(tier: License.Tier, trialDaysLeft: Int): String = when (tier) {
        License.Tier.LICENSED -> "Bought. Thank you."
        License.Tier.BETA -> "Free while the beta is open"
        License.Tier.TRIAL -> count(trialDaysLeft, "day") + " of the trial left"
        License.Tier.LOCKED -> "Remapping is off until a licence is bought"
    }

    /** "1 key", "3 keys". */
    fun count(n: Int, one: String): String =
        n.toString() + " " + (if (n == 1) one else one + "s")
}
