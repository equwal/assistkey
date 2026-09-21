package dev.equwal.assistkey.native

import android.content.ComponentName
import android.content.Context
import android.provider.Settings
import dev.equwal.assistkey.model.HwKey
import dev.equwal.assistkey.model.Presets

/**
 * The firmware's own key remapper: a handful of Settings.System strings. The
 * names and the token vocabulary were read out of setting_se08 1.3.7 on
 * firmware 1.5.6.
 *
 * This is read-only, and not by choice. An ordinary app cannot write these
 * keys. SettingsProvider refuses any Settings.System name outside its public
 * list unless the caller is a privileged system app - WRITE_SETTINGS does not
 * help and neither does an adb-granted WRITE_SECURE_SETTINGS; the call dies
 * with "You cannot keep your settings in the secure settings". Only the shell
 * can change them, so the most this app can do is show them and hand over the
 * command.
 *
 * They still matter, because of one interaction measured on the device:
 *
 *   While a volume key's hook holds ANY value - even its "default" token,
 *   volume_up or volume_down - the firmware handles that key before the input
 *   filter stage, and no app ever sees the press. Unset, the key reaches the
 *   accessibility filter normally.
 *
 * The AI key is not like that: it reaches the filter whatever its hook says,
 * and the hook only decides what happens when nothing consumes the press.
 *
 * The two families are not symmetric. A volume key holds a token, with a
 * component kept alongside in *AppShortcut; the AI key holds the flat component
 * itself.
 */
object ViwoodsBridge {

    private const val AI_KEY = "CustomAiKey"

    private val VOLUME_SLOT = mapOf(
        HwKey.VOL_UP to Slot("CustomVolumeUpKey", "VolumeUpAppShortcut"),
        HwKey.VOL_DOWN to Slot("CustomVolumeDownKey", "VolumeDownAppShortcut")
    )

    data class Slot(val key: String, val shortcut: String)

    private const val TOKEN_APP = "shortcut_app"

    /** Every value the stock settings screen writes for a volume key. */
    private val volumeTokens = mapOf(
        "volume_up" to "Volume up",
        "volume_down" to "Volume down",
        "screenshot" to "Screenshot",
        TOKEN_APP to "Open an app"
    )

    fun keys(): List<HwKey> = listOf(HwKey.AI, HwKey.VOL_UP, HwKey.VOL_DOWN)

    /** Defensive: an OEM key that a later firmware hides must not take the app down. */
    private fun get(c: Context, name: String): String? =
        runCatching { Settings.System.getString(c.contentResolver, name) }
            .getOrNull()?.takeIf { it.isNotBlank() && it != "null" }

    fun read(c: Context, key: HwKey): String? = when (key) {
        HwKey.AI -> get(c, AI_KEY)
        else -> VOLUME_SLOT[key]?.let { get(c, it.key) }
    }

    private fun readShortcut(c: Context, key: HwKey): String? =
        VOLUME_SLOT[key]?.let { get(c, it.shortcut) }

    /**
     * True when the firmware is keeping this key to itself, so that bindings
     * made for it in this app can never fire.
     */
    fun hidesFromFilter(c: Context, key: HwKey): Boolean =
        dev.equwal.assistkey.device.Device.hasFirmwareKeyHooks &&
            VOLUME_SLOT.containsKey(key) && read(c, key) != null

    private const val AI_ENTRY = "dev.equwal.assistkey.channel.AiKeyActivity"

    /**
     * What should open when an AI press is handed back to the firmware. Never
     * our own entry point: that would be a loop.
     */
    fun aiTarget(c: Context): String =
        read(c, HwKey.AI)?.takeUnless { aiHookedToUs(c) } ?: Presets.STOCK_AI_KEY

    /** True when the firmware sends the AI key straight to this app. */
    fun aiHookedToUs(c: Context): Boolean =
        dev.equwal.assistkey.device.Device.hasFirmwareKeyHooks && read(c, HwKey.AI)?.let { ComponentName.unflattenFromString(it) } ==
            ComponentName(c.packageName, AI_ENTRY)

    fun aiHookCommand(c: Context): String =
        "adb shell settings put system " + AI_KEY + " " + c.packageName + "/" + AI_ENTRY

    fun aiUnhookCommand(): String =
        "adb shell settings put system " + AI_KEY + " " + Presets.STOCK_AI_KEY

    /** The command that hands a volume key back to the input pipeline. */
    fun unsetCommand(key: HwKey): String? =
        VOLUME_SLOT[key]?.let { "adb shell settings delete system " + it.key }

    /** Human description of whatever the firmware currently has bound. */
    fun describe(c: Context, key: HwKey): String {
        val v = read(c, key) ?: return "Not set"
        if (key == HwKey.AI) return if (aiHookedToUs(c)) "AssistKey" else shortName(c, v)

        val label = volumeTokens[v] ?: return shortName(c, v)
        if (v != TOKEN_APP) return label
        val target = readShortcut(c, key)
        return if (target == null) "Open an app (none chosen)" else "Open " + shortName(c, target)
    }

    private fun shortName(c: Context, flat: String): String {
        val cn = ComponentName.unflattenFromString(flat) ?: return flat
        Presets.viwoods.firstOrNull { it.component == flat }?.let { return it.label }
        return runCatching {
            c.packageManager.getActivityInfo(cn, 0).loadLabel(c.packageManager).toString()
        }.getOrElse { cn.shortClassName.substringAfterLast('.') }
    }
}
