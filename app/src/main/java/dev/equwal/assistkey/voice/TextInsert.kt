package dev.equwal.assistkey.voice

import android.accessibilityservice.AccessibilityService
import android.content.ClipData
import android.content.ClipboardManager
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Puts text where the cursor is, in the app in front.
 *
 * The accessibility service does this, not a keyboard. It finds the field that
 * has input focus and replaces the selection with the new text. A field that
 * refuses a direct edit gets the text through the clipboard and a paste.
 *
 * Password fields are never written to.
 */
object TextInsert {

    enum class Result { DONE, NO_FIELD, PASSWORD, REFUSED }

    fun insert(svc: AccessibilityService, text: String): Result {
        val node = svc.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?: svc.rootInActiveWindow?.findFocus(AccessibilityNodeInfo.FOCUS_INPUT)
            ?: return Result.NO_FIELD
        if (node.isPassword) return Result.PASSWORD
        if (!node.isEditable) return Result.NO_FIELD

        // A field that shows its hint reports the hint as its text.
        val old = if (node.isShowingHintText) "" else node.text?.toString().orEmpty()
        val start = node.textSelectionStart.takeIf { it in 0..old.length } ?: old.length
        val end = node.textSelectionEnd.takeIf { it in start..old.length } ?: start

        // A space between the old text and the new words, when there is none.
        val lead = if (start > 0 && !old[start - 1].isWhitespace()) " " else ""
        val piece = lead + text
        val merged = old.substring(0, start) + piece + old.substring(end)

        val set = Bundle().apply {
            putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, merged)
        }
        if (node.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, set)) {
            val caret = start + piece.length
            node.performAction(
                AccessibilityNodeInfo.ACTION_SET_SELECTION,
                Bundle().apply {
                    putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_START_INT, caret)
                    putInt(AccessibilityNodeInfo.ACTION_ARGUMENT_SELECTION_END_INT, caret)
                }
            )
            return Result.DONE
        }

        // Web pages and some custom fields accept a paste but not a direct edit.
        val clipboard = svc.getSystemService(ClipboardManager::class.java) ?: return Result.REFUSED
        clipboard.setPrimaryClip(ClipData.newPlainText("AssistKey voice typing", piece))
        return if (node.performAction(AccessibilityNodeInfo.ACTION_PASTE)) Result.DONE else Result.REFUSED
    }
}
