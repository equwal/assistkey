package dev.equwal.assistkey.store

import dev.equwal.assistkey.model.ActionKind
import dev.equwal.assistkey.model.ActionSpec
import dev.equwal.assistkey.model.GestureType
import dev.equwal.assistkey.model.HwKey
import dev.equwal.assistkey.model.Trigger

/**
 * An immutable snapshot of every binding, with the lookups the key-event hot
 * path needs precomputed. Rebuilt on save rather than queried per keystroke.
 */
class Bindings(private val map: Map<Trigger, ActionSpec>) {

    private val active: Map<Trigger, ActionSpec> =
        map.filterValues { it.kind != ActionKind.PASS_THROUGH }

    /** What the gesture engine is told, for one answer to "can it see Power?". */
    private class View(triggers: Collection<Trigger>) {
        val taps: Map<Set<HwKey>, Int> =
            triggers.filter { it.type == GestureType.TAP }
                .groupBy { it.keys }
                .mapValues { (_, v) -> v.maxOf { it.count } }

        val holds: Set<Set<HwKey>> =
            triggers.filter { it.type == GestureType.HOLD }.map { it.keys }.toSet()

        /** For each key, the other keys it forms a bound chord with. */
        val partners: Map<HwKey, Set<HwKey>> = buildMap {
            triggers.filter { it.isChord }.forEach { t ->
                t.keys.forEach { k -> merge(k, t.keys - k) { a, b -> a + b } }
            }
        }
    }

    /**
     * Two views, because whether the engine may know about Power depends on
     * whether Power presses can reach it. Without shell access they cannot, and
     * an engine that believed in a Power chord would hold every volume press
     * back waiting for a partner that never arrives. With it, Power is a key
     * like any other.
     */
    private val withPower = View(active.keys)
    private val withoutPower = View(active.keys.filter { HwKey.POWER !in it.keys })

    private fun view(power: Boolean) = if (power) withPower else withoutPower

    fun maxTaps(keys: Set<HwKey>, power: Boolean = false): Int = view(power).taps[keys] ?: 0
    fun hasHold(keys: Set<HwKey>, power: Boolean = false): Boolean = keys in view(power).holds
    fun chordPartners(key: HwKey, power: Boolean = false): Set<HwKey> =
        view(power).partners[key] ?: emptySet()

    operator fun get(trigger: Trigger): ActionSpec? = active[trigger]
    fun isBound(trigger: Trigger): Boolean = trigger in active

    /** The action for "hold Power, then press [key]", if there is one. */
    fun powerCombo(key: HwKey): ActionSpec? = active[Trigger.powerThen(key)]

    val hasPowerCombos: Boolean =
        HwKey.interceptable.any { active.containsKey(Trigger.powerThen(it)) }

    /** Including PASS_THROUGH entries, for the UI. */
    fun raw(trigger: Trigger): ActionSpec = map[trigger] ?: ActionSpec.PASS
    fun all(): Map<Trigger, ActionSpec> = map

    fun with(trigger: Trigger, spec: ActionSpec): Bindings =
        Bindings(map.toMutableMap().apply {
            if (spec.kind == ActionKind.PASS_THROUGH) remove(trigger) else put(trigger, spec)
        })

    companion object {
        val EMPTY = Bindings(emptyMap())
    }
}
