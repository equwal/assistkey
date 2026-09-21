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

    /**
     * What the gesture engine may know about. Anything involving Power is kept
     * from it: Power never reaches the key filter, so an engine that believed
     * in a Power chord would hold every volume press back waiting for a
     * partner that cannot arrive. Power triggers are served by the channels
     * and by [powerCombo] instead.
     */
    private val engine: Set<Trigger> = active.keys.filter { HwKey.POWER !in it.keys }.toSet()

    private val tapsByKeys: Map<Set<HwKey>, Int> =
        engine.filter { it.type == GestureType.TAP }
            .groupBy { it.keys }
            .mapValues { (_, v) -> v.maxOf { it.count } }

    private val holdKeys: Set<Set<HwKey>> =
        engine.filter { it.type == GestureType.HOLD }.map { it.keys }.toSet()

    /** For each key, the other keys it forms a bound chord with. */
    private val partners: Map<HwKey, Set<HwKey>> = buildMap {
        engine.filter { it.isChord }.forEach { t ->
            t.keys.forEach { k -> merge(k, t.keys - k) { a, b -> a + b } }
        }
    }

    fun maxTaps(keys: Set<HwKey>): Int = tapsByKeys[keys] ?: 0
    fun hasHold(keys: Set<HwKey>): Boolean = keys in holdKeys
    fun chordPartners(key: HwKey): Set<HwKey> = partners[key] ?: emptySet()

    operator fun get(trigger: Trigger): ActionSpec? = active[trigger]

    /** The action for "hold Power, then press [key]", if there is one. */
    fun powerCombo(key: HwKey): ActionSpec? = active[Trigger.powerThen(key)]

    val hasPowerCombos: Boolean =
        HwKey.interceptable.any { active.containsKey(Trigger.powerThen(it)) }
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
