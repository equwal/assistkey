package dev.equwal.assistkey.store

import android.content.Context
import android.content.SharedPreferences
import dev.equwal.assistkey.engine.GestureEngine
import dev.equwal.assistkey.model.ActionSpec
import dev.equwal.assistkey.model.Trigger
import org.json.JSONObject

/**
 * Persistence. Bindings live as one JSON blob so adding an action kind never
 * needs a migration, and the parsed result is cached because the key-event
 * path must not touch disk.
 */
object Store {

    private const val PREFS = "assistkey"
    private const val K_BINDINGS = "bindings"
    private const val K_MULTITAP = "multitap_ms"
    private const val K_HOLD = "hold_ms"
    private const val K_CHORD = "chord_ms"

    @Volatile private var cache: Bindings? = null

    private fun prefs(c: Context): SharedPreferences =
        c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun bindings(c: Context): Bindings = cache ?: load(c).also { cache = it }

    private fun load(c: Context): Bindings {
        val raw = prefs(c).getString(K_BINDINGS, null) ?: return Bindings.EMPTY
        return runCatching {
            val o = JSONObject(raw)
            val m = HashMap<Trigger, ActionSpec>()
            o.keys().forEach { id ->
                val t = Trigger.parse(id) ?: return@forEach
                m[t] = ActionSpec.fromJson(o.getJSONObject(id))
            }
            Bindings(m)
        }.getOrDefault(Bindings.EMPTY)
    }

    fun save(c: Context, b: Bindings) {
        val o = JSONObject()
        b.all().forEach { (t, spec) -> o.put(t.id, spec.toJson()) }
        prefs(c).edit().putString(K_BINDINGS, o.toString()).apply()
        cache = b
    }

    fun bind(c: Context, trigger: Trigger, spec: ActionSpec) {
        save(c, bindings(c).with(trigger, spec))
    }

    fun timing(c: Context): GestureEngine.Config {
        val p = prefs(c)
        val d = GestureEngine.Config()
        return GestureEngine.Config(
            multiTapMs = p.getLong(K_MULTITAP, d.multiTapMs),
            holdMs = p.getLong(K_HOLD, d.holdMs),
            chordMs = p.getLong(K_CHORD, d.chordMs)
        )
    }

    fun setTiming(c: Context, cfg: GestureEngine.Config) {
        prefs(c).edit()
            .putLong(K_MULTITAP, cfg.multiTapMs)
            .putLong(K_HOLD, cfg.holdMs)
            .putLong(K_CHORD, cfg.chordMs)
            .apply()
    }

    /** Forces the next read to hit disk. */
    fun invalidate() { cache = null }
}
