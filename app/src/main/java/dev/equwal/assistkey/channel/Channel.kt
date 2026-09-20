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
        "Accessibility key filter",
        "Captures the AI key and both volume keys. Required for multi-tap, " +
            "holds, combinations, and for Back / Recents / swipe actions.",
        listOf()
    ),

    ASSISTANT(
        "assistant",
        "Digital assistant",
        "Captures long-press Power by holding the assistant role.",
        listOf("dev.equwal.assistkey.channel.AssistActivity")
    ),

    CAMERA(
        "camera",
        "Camera app",
        "Captures double-press Power. The firmware opens the camera on a " +
            "double press, so this app has to be the default camera app.",
        listOf("dev.equwal.assistkey.channel.CameraShimActivity")
    ),

    WALLET(
        "wallet",
        "Wallet app",
        "Captures the quick-settings wallet tile and the lock-screen wallet " +
            "button, and double-press Power on firmware that sends it to the " +
            "wallet. This reader sends it to the camera instead.",
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
