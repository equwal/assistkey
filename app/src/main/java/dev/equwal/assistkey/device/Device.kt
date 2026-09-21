package dev.equwal.assistkey.device

import android.content.Context
import android.os.Build
import dev.equwal.assistkey.model.HwKey

/**
 * What this particular device is, and what that changes.
 *
 * AssistKey runs on any Android 12+ device. Most of it is the same everywhere:
 * the key filter, the Power button, navigation, shell access. A profile only
 * adds what a maker did differently - extra keys, its own key settings, a
 * gesture switch nobody else has, a brightness floor. Viwoods is the first
 * profile because it is where the app was built; an unknown device is simply
 * the generic profile plus whatever keys the key tester has seen.
 *
 * New profiles come from the device reports beta testers send in.
 */
object Device {

    enum class Profile(val title: String) {
        VIWOODS("Viwoods AiPaper"),
        GENERIC("Android device")
    }

    val profile: Profile by lazy {
        val id = (Build.MANUFACTURER + " " + Build.BRAND + " " + Build.MODEL).lowercase()
        if ("viwoods" in id || "aipaper" in id) Profile.VIWOODS else Profile.GENERIC
    }

    val isViwoods: Boolean get() = profile == Profile.VIWOODS

    val name: String get() = Build.MANUFACTURER + " " + Build.MODEL

    /** Maker-specific key settings that can hide a key from every app. */
    val hasFirmwareKeyHooks: Boolean get() = isViwoods

    /** The maker's own AI screens, offered as actions. */
    val hasViwoodsActions: Boolean get() = isViwoods

    /** Settings.System switch for the maker's own swipe-up gesture, if it has one. */
    val bottomGestureSetting: String? get() = if (isViwoods) "disable_gesture_bottom" else null

    /**
     * The lowest backlight value the system will set by itself. Anything under
     * it gets snapped to zero by the framework, though the hardware goes lower.
     * Null where it has not been measured.
     */
    val brightnessFloor: Int? get() = if (isViwoods) 5 else null

    private const val PREFS = "assistkey_device"
    private const val K_AI_RETURNS = "ai_key_returns"

    /**
     * Viwoods only. Inside the AI screen or the crop screen, the AI key goes
     * back to the app that was in use before. On by default on that device.
     */
    fun aiKeyReturns(c: Context): Boolean =
        isViwoods && c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(K_AI_RETURNS, true)

    fun setAiKeyReturns(c: Context, on: Boolean) {
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(K_AI_RETURNS, on).apply()
    }

    /** True for the Viwoods AI assistant and for the Viwoods crop and screenshot-edit screens. */
    fun isAiScreen(pkg: String?, cls: String?): Boolean =
        pkg == "com.viwoods.viwoodsai" ||
            (pkg == "com.viwoods.launcher" && cls?.contains(".libfloating.") == true)
    private const val K_SEEN = "seen_keys"

    private val builtIn: List<HwKey>
        get() = if (isViwoods) listOf(HwKey.AI, HwKey.VOL_UP, HwKey.VOL_DOWN)
        else listOf(HwKey.VOL_UP, HwKey.VOL_DOWN)

    /**
     * The remappable keys to show: what the profile knows about, plus any other
     * supported key this device has actually produced. A page-turn button on an
     * e-reader shows up here the first time it is pressed.
     */
    fun keys(c: Context): List<HwKey> {
        val seen = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(K_SEEN, emptySet()).orEmpty()
        return (builtIn + HwKey.interceptable.filter { it.token in seen }).distinct()
    }

    private const val K_UNKNOWN = "unknown_keys"

    /**
     * Keys this app has no name for yet. Only recorded while the key tester is
     * open, so an ordinary keyboard being typed on never lands here; and only
     * as "this key exists", never as a sequence.
     */
    fun noteUnknown(c: Context, keyCode: Int, scanCode: Int, device: String?) {
        val p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val seen = p.getStringSet(K_UNKNOWN, emptySet()).orEmpty()
        val entry = "keycode " + keyCode + " scancode " + scanCode + " on " + (device ?: "?")
        if (entry !in seen && seen.size < 40) p.edit().putStringSet(K_UNKNOWN, seen + entry).apply()
    }

    fun unknownSeen(c: Context): List<String> =
        c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
            .getStringSet(K_UNKNOWN, emptySet()).orEmpty().sorted()

    /** Called by the key filter for every supported key it is handed. */
    fun noteSeen(c: Context, key: HwKey) {
        if (key in builtIn) return
        val p = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val seen = p.getStringSet(K_SEEN, emptySet()).orEmpty()
        if (key.token !in seen) p.edit().putStringSet(K_SEEN, seen + key.token).apply()
    }
}
