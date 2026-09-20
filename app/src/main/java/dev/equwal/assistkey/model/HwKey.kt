package dev.equwal.assistkey.model

import android.view.KeyEvent

/**
 * The physical keys this app can bind.
 *
 * POWER is listed because it can carry actions, but it is deliberately not
 * [interceptable]: PhoneWindowManager consumes KEYCODE_POWER in
 * interceptKeyBeforeQueueing() before the event reaches the input dispatcher,
 * so no AccessibilityService on an unrooted device ever sees it. Power is
 * driven through the role channels and the native power_button_* settings
 * instead - see dev.equwal.assistkey.channel.
 */
enum class HwKey(
    val code: Int,
    val label: String,
    /** True when an AccessibilityService can see and consume this key. */
    val interceptable: Boolean
) {
    AI(KeyEvent.KEYCODE_F1, "AI key", true),
    VOL_UP(KeyEvent.KEYCODE_VOLUME_UP, "Volume up", true),
    VOL_DOWN(KeyEvent.KEYCODE_VOLUME_DOWN, "Volume down", true),
    POWER(KeyEvent.KEYCODE_POWER, "Power", false);

    /** Stable token used in persisted trigger ids. Never change these. */
    val token: String get() = name.lowercase()

    companion object {
        fun fromCode(code: Int): HwKey? = entries.firstOrNull { it.code == code }
        fun fromToken(t: String): HwKey? = entries.firstOrNull { it.token == t }

        /** The keys the accessibility engine is allowed to grab. */
        val interceptable: List<HwKey> get() = entries.filter { it.interceptable }
    }
}
