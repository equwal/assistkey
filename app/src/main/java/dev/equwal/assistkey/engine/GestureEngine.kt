package dev.equwal.assistkey.engine

import dev.equwal.assistkey.model.GestureType
import dev.equwal.assistkey.model.HwKey
import dev.equwal.assistkey.model.Trigger

/**
 * Turns a stream of key down/up events into tap-count / hold / chord gestures.
 *
 * Deliberately free of Android types: everything that touches the framework
 * (timers, firing an action, emulating the native key) goes through [Host], so
 * the whole state machine is unit-testable on the JVM.
 *
 * The one rule that matters for feel: a key with no bindings at all is never
 * consumed, and a key whose highest bound tap count is 1 fires on key-up
 * without waiting out the multi-tap window. We only pay the multi-tap latency
 * on keys where the user actually asked for a double/triple tap.
 */
class GestureEngine(
    private val host: Host,
    var cfg: Config = Config()
) {

    data class Config(
        /** How long to wait for another tap before committing the count. */
        val multiTapMs: Long = 280,
        /** How long a key must stay down to count as a hold. */
        val holdMs: Long = 450,
        /** Two keys going down within this window form a chord. */
        val chordMs: Long = 140
    )

    interface Host {
        /** Highest bound tap count for this key set, or 0 if no tap is bound. */
        fun maxTaps(keys: Set<HwKey>): Int

        /** True when a hold is bound for this key set. */
        fun hasHold(keys: Set<HwKey>): Boolean

        /** True when this exact gesture has a binding. */
        fun isBound(trigger: Trigger): Boolean

        /** Any key set that starts with this key and could still become a chord. */
        fun chordPartners(key: HwKey): Set<HwKey>

        fun fire(trigger: Trigger)

        /** Reproduce what the system would have done, since we ate the event. */
        fun passThrough(keys: Set<HwKey>, longPress: Boolean)

        fun schedule(token: String, delayMs: Long, action: () -> Unit)
        fun cancel(token: String)
    }

    private companion object {
        const val T_HOLD = "hold"
        const val T_TAP = "tap"
    }

    private val down = LinkedHashSet<HwKey>()
    private var seqKeys: Set<HwKey> = emptySet()
    private var tapCount = 0
    private var holdFired = false
    private var consuming = false
    private var firstDownAt = 0L

    /** @return true to swallow the event. */
    fun onDown(key: HwKey, now: Long, repeatCount: Int): Boolean {
        if (repeatCount > 0) return consuming   // our own timer drives holds

        down.add(key)

        val chordUpgrade = consuming &&
            key !in seqKeys &&
            (now - firstDownAt) <= cfg.chordMs &&
            interesting(seqKeys + key)

        when {
            chordUpgrade -> {
                host.cancel(T_HOLD); host.cancel(T_TAP)
                seqKeys = seqKeys + key
                tapCount = 0
                holdFired = false
            }
            !consuming || key !in seqKeys -> {
                host.cancel(T_HOLD); host.cancel(T_TAP)
                seqKeys = setOf(key)
                tapCount = 0
                holdFired = false
                firstDownAt = now
            }
            else -> {
                // Another tap of a sequence already in progress.
                host.cancel(T_TAP)
            }
        }

        // Only grab the key if this set, or a chord it could grow into, is bound.
        if (!interesting(seqKeys) && !couldBecomeChord(seqKeys, now)) {
            reset()
            return false
        }

        consuming = true
        if (host.hasHold(seqKeys)) {
            host.schedule(T_HOLD, cfg.holdMs) { onHoldElapsed() }
        }
        return true
    }

    /** @return true to swallow the event. */
    fun onUp(key: HwKey, now: Long): Boolean {
        down.remove(key)
        if (!consuming) return false

        host.cancel(T_HOLD)

        if (holdFired) {
            if (down.isEmpty()) reset()
            return true
        }

        // For a chord, wait until every key of the chord is released.
        if (down.any { it in seqKeys }) return true

        tapCount++
        val max = host.maxTaps(seqKeys)

        if (max == 0) {
            // Nothing bound to taps - only a hold was. Give the key back.
            val keys = seqKeys
            reset()
            host.passThrough(keys, longPress = false)
            return true
        }

        if (tapCount >= max) {
            commit()
        } else {
            host.schedule(T_TAP, cfg.multiTapMs) { commit() }
        }
        return true
    }

    fun onCancel() {
        host.cancel(T_HOLD); host.cancel(T_TAP)
        down.clear()
        reset()
    }

    private fun onHoldElapsed() {
        val t = Trigger(seqKeys, GestureType.HOLD)
        holdFired = true
        tapCount = 0
        if (host.isBound(t)) host.fire(t)
    }

    private fun commit() {
        host.cancel(T_TAP)
        val keys = seqKeys
        val n = tapCount.coerceIn(1, Trigger.MAX_TAPS)
        reset()
        if (keys.isEmpty()) return

        val t = Trigger(keys, GestureType.TAP, n)
        if (host.isBound(t)) {
            host.fire(t)
        } else if (n == 1) {
            host.passThrough(keys, longPress = false)
        }
        // A 2+ tap with no binding is intentionally dropped rather than
        // replayed - replaying would fire the native action N times.
    }

    private fun interesting(keys: Set<HwKey>): Boolean =
        host.maxTaps(keys) > 0 || host.hasHold(keys)

    /**
     * A lone key with no bindings still has to be held briefly if it is the
     * first half of a bound chord, otherwise the chord could never be entered.
     */
    private fun couldBecomeChord(keys: Set<HwKey>, now: Long): Boolean {
        if (keys.size != 1) return false
        val partners = host.chordPartners(keys.first())
        return partners.isNotEmpty() && (now - firstDownAt) <= cfg.chordMs
    }

    private fun reset() {
        seqKeys = emptySet()
        tapCount = 0
        holdFired = false
        consuming = false
    }
}
