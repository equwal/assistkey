package dev.equwal.assistkey.native

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import dev.equwal.assistkey.model.HwKey
import dev.equwal.assistkey.model.Presets

/**
 * The firmware's own key remapper, which turns out to be nothing more than a
 * handful of Settings.System strings that the Viwoods launcher reads. The names
 * and the token vocabulary below were read out of setting_se08 1.3.7 on
 * firmware 1.5.6.
 *
 * Writing them needs WRITE_SETTINGS, which the user can grant from an ordinary
 * settings screen - no adb - so this is the one channel that works on a
 * completely untouched device. The cost is one action per key: no multi-tap,
 * no holds, no chords.
 *
 * The two key families are not symmetric. The volume keys hold a token, with
 * the component kept alongside in *AppShortcut; the AI key holds the flat
 * component itself and has no shortcut slot.
 */
object ViwoodsBridge {

    private const val AI_KEY = "CustomAiKey"

    private val VOLUME_SLOT = mapOf(
        HwKey.VOL_UP to Slot("CustomVolumeUpKey", "VolumeUpAppShortcut"),
        HwKey.VOL_DOWN to Slot("CustomVolumeDownKey", "VolumeDownAppShortcut")
    )

    data class Slot(val key: String, val shortcut: String)

    const val TOKEN_APP = "shortcut_app"

    /** Every value the stock launcher understands for a volume key. */
    val volumeTokens = listOf(
        "volume_up" to "Volume up",
        "volume_down" to "Volume down",
        "screenshot" to "Screenshot",
        TOKEN_APP to "Open an app"
    )

    fun keys(): List<HwKey> = listOf(HwKey.AI, HwKey.VOL_UP, HwKey.VOL_DOWN)

    fun canWrite(c: Context): Boolean = Settings.System.canWrite(c)

    // ---- reads -------------------------------------------------------------

    fun read(c: Context, key: HwKey): String? = when (key) {
        HwKey.AI -> Settings.System.getString(c.contentResolver, AI_KEY)
        else -> VOLUME_SLOT[key]?.let {
            Settings.System.getString(c.contentResolver, it.key)
        }
    }

    fun readShortcut(c: Context, key: HwKey): String? =
        VOLUME_SLOT[key]?.let { Settings.System.getString(c.contentResolver, it.shortcut) }

    /** Human description of whatever the firmware currently has bound. */
    fun describe(c: Context, key: HwKey): String {
        val v = read(c, key)
        if (v.isNullOrBlank()) return "Not set (firmware default)"
        if (key == HwKey.AI) return shortName(c, v)

        val named = volumeTokens.firstOrNull { it.first == v } ?: return shortName(c, v)
        if (v != TOKEN_APP) return named.second
        val target = readShortcut(c, key)
        return if (target.isNullOrBlank()) "Open an app (none chosen)"
        else "Open " + shortName(c, target)
    }

    // ---- writes ------------------------------------------------------------

    /**
     * Returns false when WRITE_SETTINGS has not been granted, so the caller can
     * send the user to the grant screen rather than silently doing nothing.
     */
    fun bindComponent(c: Context, key: HwKey, flatComponent: String): Boolean {
        if (!canWrite(c)) return false
        return runCatching {
            if (key == HwKey.AI) {
                Settings.System.putString(c.contentResolver, AI_KEY, flatComponent)
            } else {
                val slot = VOLUME_SLOT[key] ?: return false
                Settings.System.putString(c.contentResolver, slot.shortcut, flatComponent)
                Settings.System.putString(c.contentResolver, slot.key, TOKEN_APP)
            }
            true
        }.getOrDefault(false)
    }

    fun bindToken(c: Context, key: HwKey, token: String): Boolean {
        val slot = VOLUME_SLOT[key] ?: return false
        if (!canWrite(c)) return false
        return runCatching {
            Settings.System.putString(c.contentResolver, slot.key, token); true
        }.getOrDefault(false)
    }

    /**
     * Points a firmware key at our own assist entry, which is how the Viwoods
     * channel gets the full gesture engine on a device where the accessibility
     * service is unavailable or unwanted.
     */
    fun bindToUs(c: Context, key: HwKey): Boolean =
        bindComponent(c, key, c.packageName + "/dev.equwal.assistkey.channel.AssistActivity")

    /**
     * Hands a key back to the firmware: the AI key to its factory target, the
     * volume keys to plain volume.
     */
    fun restore(c: Context, key: HwKey): Boolean = when (key) {
        HwKey.AI -> bindComponent(c, key, Presets.STOCK_AI_KEY)
        HwKey.VOL_UP -> bindToken(c, key, "volume_up")
        HwKey.VOL_DOWN -> bindToken(c, key, "volume_down")
        HwKey.POWER -> false
    }

    private fun shortName(c: Context, flat: String): String {
        val cn = ComponentName.unflattenFromString(flat) ?: return flat
        Presets.viwoods.firstOrNull { it.component == flat }?.let { return it.label }
        return runCatching {
            c.packageManager.getActivityInfo(cn, 0).loadLabel(c.packageManager).toString()
        }.getOrElse { cn.shortClassName.substringAfterLast('.') }
    }
}
