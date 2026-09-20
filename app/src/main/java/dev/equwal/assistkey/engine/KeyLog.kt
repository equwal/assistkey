package dev.equwal.assistkey.engine

import android.view.KeyEvent

/**
 * A short record of what the key filter actually saw.
 *
 * Worth having permanently rather than as a debugging aid: whether a given key
 * reaches an input filter at all is a per-firmware question with no way to look
 * it up. Keys consumed upstream by the window manager - Power everywhere, and
 * on some builds an OEM key too - simply never appear here, and that absence is
 * the answer.
 *
 * Injected events (adb shell input keyevent) bypass accessibility input filters
 * entirely, so only a real press proves anything.
 */
object KeyLog {

    data class Entry(
        val at: Long,
        val keyCode: Int,
        val action: Int,
        val repeat: Int,
        val label: String,
        val consumed: Boolean
    ) {
        fun describe(): String {
            val act = when (action) {
                KeyEvent.ACTION_DOWN -> if (repeat > 0) "down x" + repeat else "down"
                KeyEvent.ACTION_UP -> "up"
                else -> "action " + action
            }
            return label + "  " + act + "  " + (if (consumed) "consumed" else "passed on")
        }
    }

    private const val CAPACITY = 60

    private val entries = ArrayDeque<Entry>(CAPACITY)

    /**
     * Only the tester screen turns this on, and only while it is in front. A
     * key filter is handed every key on the device, a Bluetooth keyboard's
     * included, and the app's promise is that it keeps none of them - so
     * outside that screen nothing is remembered, even in memory.
     */
    @Volatile
    var recording = false

    @Synchronized
    fun record(event: KeyEvent, label: String, consumed: Boolean) {
        if (!recording) return
        if (entries.size >= CAPACITY) entries.removeFirst()
        entries.addLast(
            Entry(
                at = System.currentTimeMillis(),
                keyCode = event.keyCode,
                action = event.action,
                repeat = event.repeatCount,
                label = label,
                consumed = consumed
            )
        )
    }

    /** Newest first, which is the order the tester screen wants. */
    @Synchronized
    fun recent(): List<Entry> = entries.reversed()

    @Synchronized
    fun clear() = entries.clear()

    @Synchronized
    fun count(): Int = entries.size
}
