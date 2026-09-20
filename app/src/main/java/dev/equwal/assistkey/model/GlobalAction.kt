package dev.equwal.assistkey.model

import android.accessibilityservice.AccessibilityService as AS
import android.os.Build

/**
 * The AccessibilityService global actions worth exposing, with the SDK level
 * each one needs. Anything above the running SDK is filtered out of the picker
 * rather than failing at press time.
 */
enum class GlobalAction(val id: Int, val label: String, val minSdk: Int = 30) {
    BACK(AS.GLOBAL_ACTION_BACK, "Back"),
    HOME(AS.GLOBAL_ACTION_HOME, "Home"),
    RECENTS(AS.GLOBAL_ACTION_RECENTS, "Recent apps"),
    NOTIFICATIONS(AS.GLOBAL_ACTION_NOTIFICATIONS, "Notification shade"),
    QUICK_SETTINGS(AS.GLOBAL_ACTION_QUICK_SETTINGS, "Quick settings"),
    POWER_DIALOG(AS.GLOBAL_ACTION_POWER_DIALOG, "Power menu"),
    LOCK_SCREEN(AS.GLOBAL_ACTION_LOCK_SCREEN, "Lock screen"),
    SCREENSHOT(AS.GLOBAL_ACTION_TAKE_SCREENSHOT, "Screenshot"),
    DPAD_UP(16, "D-pad up", Build.VERSION_CODES.UPSIDE_DOWN_CAKE),
    DPAD_DOWN(17, "D-pad down", Build.VERSION_CODES.UPSIDE_DOWN_CAKE),
    DPAD_LEFT(18, "D-pad left", Build.VERSION_CODES.UPSIDE_DOWN_CAKE),
    DPAD_RIGHT(19, "D-pad right", Build.VERSION_CODES.UPSIDE_DOWN_CAKE),
    DPAD_CENTER(20, "D-pad centre", Build.VERSION_CODES.UPSIDE_DOWN_CAKE),
    MEDIA_PLAY_PAUSE(21, "Play / pause", Build.VERSION_CODES.UPSIDE_DOWN_CAKE);

    val available: Boolean get() = Build.VERSION.SDK_INT >= minSdk

    companion object {
        fun fromName(n: String): GlobalAction? = entries.firstOrNull { it.name == n }
        fun usable(): List<GlobalAction> = entries.filter { it.available }
    }
}
