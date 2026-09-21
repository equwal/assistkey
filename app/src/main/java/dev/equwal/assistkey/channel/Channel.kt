package dev.equwal.assistkey.channel

import dev.equwal.assistkey.model.GestureType
import dev.equwal.assistkey.model.HwKey
import dev.equwal.assistkey.model.Trigger

/**
 * A capture channel: one way the system can be persuaded to hand us control.
 *
 * Each is independently switchable because they compete for different system
 * slots and their availability varies by firmware. Turning one on enables its
 * manifest components; turning it off disables them, so the app stops showing
 * up as a candidate anywhere in Settings.
 *
 * Power never reaches an input filter - PhoneWindowManager eats it before the
 * dispatcher - so the three impersonation channels exist purely to get the
 * power gestures routed back to us as ordinary app launches.
 */
enum class Channel(
    val key: String,
    val title: String,
    val summary: String,
    /** Manifest components enabled/disabled along with the channel. */
    val components: List<String>
) {
    ACCESSIBILITY(
        "accessibility",
        "Button remapping",
        "Sees button presses and does the action",
        listOf()
    ),

    ASSISTANT(
        "assistant",
        "Hold Power",
        "Through the assistant role",
        listOf("dev.equwal.assistkey.channel.AssistActivity")
    ),

    CAMERA(
        "camera",
        "Double tap Power, by camera",
        "Most devices send it to the camera",
        listOf("dev.equwal.assistkey.channel.CameraShimActivity")
    ),

    WALLET(
        "wallet",
        "Double tap Power, by wallet",
        "Some devices send it to the wallet",
        listOf(
            "dev.equwal.assistkey.channel.WalletActivity",
            "dev.equwal.assistkey.channel.WalletService"
        )
    );

    /**
     * The gesture this channel stands in for, so an impersonation entry point
     * resolves to the same binding the user edited on the Power screen. Null
     * for channels that are not a single fixed gesture.
     */
    val trigger: Trigger?
        get() = when (this) {
            ASSISTANT -> POWER_HOLD
            CAMERA, WALLET -> POWER_DOUBLE
            ACCESSIBILITY -> null
        }

    companion object {
        val POWER_HOLD = Trigger(setOf(HwKey.POWER), GestureType.HOLD)
        val POWER_DOUBLE = Trigger(setOf(HwKey.POWER), GestureType.TAP, 2)

        fun fromKey(k: String): Channel? = entries.firstOrNull { it.key == k }
    }
}
