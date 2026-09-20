package dev.equwal.assistkey.model

/** Tap (possibly repeated) or a sustained hold. */
enum class GestureType { TAP, HOLD }

/**
 * One bindable gesture: a set of keys pressed together (one key = a plain
 * press, several = a chord) performed as N taps or as a hold.
 *
 * Serialised as "ai+vol_up:tap:2" - keys sorted by enum ordinal so the same
 * chord always produces the same id regardless of press order.
 */
data class Trigger(
    val keys: Set<HwKey>,
    val type: GestureType,
    val count: Int = 1
) {
    init {
        require(keys.isNotEmpty()) { "trigger needs at least one key" }
        require(count in 1..MAX_TAPS) { "count out of range: $count" }
        require(type == GestureType.TAP || count == 1) { "hold cannot repeat" }
    }

    val isChord: Boolean get() = keys.size > 1

    val id: String
        get() = keys.sortedBy { it.ordinal }.joinToString("+") { it.token } +
            ":" + type.name.lowercase() + ":" + count

    /** Human label, e.g. "AI key + Volume up - double tap". */
    fun label(): String {
        val k = keys.sortedBy { it.ordinal }.joinToString(" + ") { it.label }
        val g = when {
            type == GestureType.HOLD -> "hold"
            count == 1 -> "tap"
            count == 2 -> "double tap"
            count == 3 -> "triple tap"
            else -> "$count taps"
        }
        return "$k — $g"
    }

    companion object {
        const val MAX_TAPS = 5

        fun parse(id: String): Trigger? {
            val parts = id.split(":")
            if (parts.size != 3) return null
            val keys = parts[0].split("+").mapNotNull { HwKey.fromToken(it) }.toSet()
            if (keys.isEmpty()) return null
            val type = when (parts[1]) {
                "tap" -> GestureType.TAP
                "hold" -> GestureType.HOLD
                else -> return null
            }
            val count = parts[2].toIntOrNull() ?: return null
            if (count !in 1..MAX_TAPS) return null
            if (type == GestureType.HOLD && count != 1) return null
            return Trigger(keys, type, count)
        }

        /**
         * Every gesture the UI offers for a given key set: taps 1..MAX_TAPS
         * plus a hold.
         */
        fun allFor(keys: Set<HwKey>): List<Trigger> =
            (1..MAX_TAPS).map { Trigger(keys, GestureType.TAP, it) } +
                Trigger(keys, GestureType.HOLD)
    }
}
