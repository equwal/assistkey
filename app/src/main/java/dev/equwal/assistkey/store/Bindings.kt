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

    private val tapsByKeys: Map<Set<HwKey>, Int> =
        active.keys.filter { it.type == GestureType.TAP }
            .groupBy { it.keys }
            .mapValues { (_, v) -> v.maxOf { it.count } }

    private val holdKeys: Set<Set<HwKey>> =
        active.keys.filter { it.type == GestureType.HOLD }.map { it.keys }.toSet()

    /** For each key, the other keys it forms a bound chord with. */
    private val partners: Map<HwKey, Set<HwKey>> = buildMap {
        active.keys.filter { it.isChord }.forEach { t ->
            t.keys.forEach { k -> merge(k, t.keys - k) { a, b -> a + b } }
        }
    }

    fun maxTaps(keys: Set<HwKey>): Int = tapsByKeys[keys] ?: 0
    fun hasHold(keys: Set<HwKey>): Boolean = keys in holdKeys
    fun chordPartners(key: HwKey): Set<HwKey> = partners[key] ?: emptySet()

    operator fun get(trigger: Trigger): ActionSpec? = active[trigger]
    fun isBound(trigger: Trigger): Boolean = trigger in active

    /** Including PASS_THROUGH entries, for the UI. */
    fun raw(trigger: Trigger): ActionSpec = map[trigger] ?: ActionSpec.PASS
    fun all(): Map<Trigger, ActionSpec> = map

    /** Key sets the engine should watch at all. */
    fun watchedKeys(): Set<HwKey> =
        (tapsByKeys.keys + holdKeys).flatten().toSet()

    fun with(trigger: Trigger, spec: ActionSpec): Bindings =
        Bindings(map.toMutableMap().apply {
            if (spec.kind == ActionKind.PASS_THROUGH) remove(trigger) else put(trigger, spec)
        })

    companion object {
        val EMPTY = Bindings(emptyMap())
    }
}
