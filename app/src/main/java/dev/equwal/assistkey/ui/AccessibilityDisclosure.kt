package dev.equwal.assistkey.ui

import android.app.Activity
import android.app.AlertDialog

/**
 * What the accessibility service does, said before the user is sent to switch
 * it on.
 *
 * Google Play requires this of an app that uses the accessibility interface for
 * a purpose other than assistive technology: in the app, in plain words, with a
 * yes before anything else happens. It is one text in one place, so that every
 * screen which leads to the switch shows the same words, and so that the words
 * change when the service learns to do something new.
 */
object AccessibilityDisclosure {

    const val TEXT =
        "Rebind uses the Android AccessibilityService API to remap the buttons " +
            "of this device.\n\n" +
            "With it on, Rebind:\n\n" +
            "- sees button presses;\n" +
            "- does the action you chose;\n" +
            "- reads the front window only for the Scroll action;\n" +
            "- puts dictated words in the focused text field, never a password " +
            "field;\n" +
            "- shows a small on-screen button only when you bind one;\n" +
            "- remembers the front app's name, in memory only;\n" +
            "- sees the recent apps of the system open, to open Ink Recents in " +
            "their place, if you have it.\n\n" +
            "Nothing is collected, stored or sent. The app has no internet permission."

    fun show(a: Activity, onAgree: () -> Unit, onDecline: () -> Unit = {}) {
        AlertDialog.Builder(a)
            .setTitle("Accessibility service")
            .setMessage(TEXT)
            .setPositiveButton("Agree") { _, _ -> onAgree() }
            .setNegativeButton("Not now") { _, _ -> onDecline() }
            .setOnCancelListener { onDecline() }
            .show()
    }
}
