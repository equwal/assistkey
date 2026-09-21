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
        "AssistKey uses the Android AccessibilityService API to remap the hardware " +
            "keys of this device and to carry out the actions you bind to them.\n\n" +
            "With the service on, AssistKey:\n\n" +
            "- receives key presses, so that it can recognise taps, holds and " +
            "combinations;\n\n" +
            "- does the action you chose for you: Back, Home, Recents, a swipe, a " +
            "scroll;\n\n" +
            "- looks at the window in front to find its scrollable area, when you " +
            "use the Scroll action;\n\n" +
            "- puts recognised words into the text field that has the cursor, when " +
            "you use Voice typing. It never writes into password fields;\n\n" +
            "- notes the name of the app in front, in memory only, so that a key " +
            "can take you back to it.\n\n" +
            "It does not record what you type or what is on your screen. It collects " +
            "nothing, keeps nothing and sends nothing. The app has no internet " +
            "permission.\n\n" +
            "Agree to go to the accessibility settings of Android."

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
