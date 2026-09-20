package dev.equwal.assistkey.route

import android.accessibilityservice.AccessibilityService

/**
 * Lets any entry point (assist activity, wallet service, camera shim) reach the
 * running AccessibilityService without a bind round-trip, which would add tens
 * of milliseconds to something the user perceives as a button press.
 */
object ServiceHolder {
    @Volatile
    var service: AccessibilityService? = null

    val isRunning: Boolean get() = service != null
}
