package dev.equwal.assistkey.channel

import android.app.role.RoleManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import dev.equwal.assistkey.engine.KeyFilterService
import dev.equwal.assistkey.route.ServiceHolder

/**
 * Turning channels on and off, and telling the truth about whether the system
 * is actually routing anything to them.
 *
 * "Enabled" is our own switch. "Satisfied" is whether the system agrees - the
 * role is held, the service is running, the permission is granted. The UI
 * shows both because an enabled-but-unsatisfied channel is the normal state
 * right after the user ticks the box, and it needs a visible next step.
 */
object Channels {

    const val ROLE_WALLET = "android.app.role.WALLET"
    const val ACTION_VIEW_WALLET = "android.service.quickaccesswallet.action.VIEW_WALLET"
    const val ACTION_STILL_IMAGE_CAMERA = "android.media.action.STILL_IMAGE_CAMERA"

    private const val PREFS = "assistkey_channels"

    private fun prefs(c: Context) =
        c.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isEnabled(c: Context, ch: Channel): Boolean =
        prefs(c).getBoolean(ch.key, false)

    fun setEnabled(c: Context, ch: Channel, on: Boolean) {
        prefs(c).edit().putBoolean(ch.key, on).apply()
        applyComponents(c, ch, on)
    }

    fun enabled(c: Context): List<Channel> = Channel.entries.filter { isEnabled(c, it) }

    /**
     * A disabled channel has its components disabled too, so the app stops
     * being offered as an assistant / camera / wallet candidate at all. Called
     * on every launch as well, in case a reinstall reset the component states.
     */
    fun syncComponents(c: Context) {
        Channel.entries.forEach { applyComponents(c, it, isEnabled(c, it)) }
    }

    private fun applyComponents(c: Context, ch: Channel, on: Boolean) {
        val pm = c.packageManager
        val want = if (on) {
            android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED
        } else {
            android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED
        }
        ch.components.forEach { cls ->
            runCatching {
                val cn = ComponentName(c.packageName, cls)
                if (pm.getComponentEnabledSetting(cn) != want) {
                    pm.setComponentEnabledSetting(
                        cn, want, android.content.pm.PackageManager.DONT_KILL_APP
                    )
                }
            }
        }
    }

    // ---- what the system thinks -------------------------------------------

    fun isSatisfied(c: Context, ch: Channel): Boolean = when (ch) {
        Channel.ACCESSIBILITY -> isAccessibilityOn(c)
        Channel.ASSISTANT -> isRoleHeld(c, RoleManager.ROLE_ASSISTANT)
        Channel.WALLET -> isRoleHeld(c, ROLE_WALLET)
        Channel.CAMERA -> isDefaultCamera(c)
        Channel.VIWOODS -> Settings.System.canWrite(c)
    }

    /** One line for the UI, describing exactly what is missing. */
    fun status(c: Context, ch: Channel): String {
        if (!isEnabled(c, ch)) return "Off"
        if (isSatisfied(c, ch)) return "Active"
        return when (ch) {
            Channel.ACCESSIBILITY -> "Service not enabled in Settings"
            Channel.ASSISTANT -> "Not the default digital assistant"
            Channel.WALLET ->
                if (isRoleAvailable(c, ROLE_WALLET)) "Not the default wallet app"
                else "No wallet role on this firmware"
            Channel.CAMERA -> "Not the default camera app"
            Channel.VIWOODS -> "Needs permission to modify system settings"
        }
    }

    /**
     * The running service is the authority when it is there - it lives in this
     * process and sets the holder itself. The settings string is only a
     * fallback for the window between "enabled" and "bound", and is read
     * defensively because restricted settings can make it unreadable.
     */
    fun isAccessibilityOn(c: Context): Boolean {
        if (ServiceHolder.isRunning) return true
        val want = ComponentName(c.packageName, KeyFilterService::class.java.name)
        val list = runCatching {
            Settings.Secure.getString(
                c.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            )
        }.getOrNull() ?: return false
        return list.split(":").any {
            ComponentName.unflattenFromString(it.trim()) == want
        }
    }

    /**
     * Android silently reverts accessibility for apps installed outside an app
     * store until the user clears the restriction in App info. There is no API
     * to read that state, so the app cannot detect it - it can only say so.
     */
    fun appInfoIntent(c: Context): Intent = Intent(
        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
        Uri.parse("package:" + c.packageName)
    ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun roleManager(c: Context): RoleManager? =
        c.getSystemService(RoleManager::class.java)

    fun isRoleAvailable(c: Context, role: String): Boolean =
        runCatching { roleManager(c)?.isRoleAvailable(role) == true }.getOrDefault(false)

    fun isRoleHeld(c: Context, role: String): Boolean =
        runCatching { roleManager(c)?.isRoleHeld(role) == true }.getOrDefault(false)

    private fun isDefaultCamera(c: Context): Boolean {
        val i = Intent(ACTION_STILL_IMAGE_CAMERA)
        val r = c.packageManager.resolveActivity(i, 0) ?: return false
        return r.activityInfo?.packageName == c.packageName
    }

    // ---- how the user grants it -------------------------------------------

    /**
     * Where to send the user to satisfy a channel. Role requests get a real
     * system dialog; everything else falls back to the closest Settings screen,
     * because no firmware exposes a direct intent for default camera.
     */
    fun claimIntent(c: Context, ch: Channel): Intent = when (ch) {
        Channel.ACCESSIBILITY -> Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)

        Channel.ASSISTANT -> roleRequest(c, RoleManager.ROLE_ASSISTANT)
            ?: Intent(Settings.ACTION_VOICE_INPUT_SETTINGS)

        Channel.WALLET -> roleRequest(c, ROLE_WALLET)
            ?: defaultApps()

        Channel.CAMERA -> defaultApps()

        Channel.VIWOODS -> Intent(
            Settings.ACTION_MANAGE_WRITE_SETTINGS,
            Uri.parse("package:" + c.packageName)
        )
    }.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    private fun roleRequest(c: Context, role: String): Intent? {
        if (!isRoleAvailable(c, role)) return null
        return runCatching { roleManager(c)?.createRequestRoleIntent(role) }.getOrNull()
    }

    private fun defaultApps(): Intent =
        Intent(Settings.ACTION_MANAGE_DEFAULT_APPS_SETTINGS)

    /**
     * Not every firmware has the default-apps screen wired up; fall back to the
     * top-level Settings rather than crashing on an unresolvable intent.
     */
    fun safeStart(c: Context, intent: Intent): Boolean {
        if (intent.resolveActivity(c.packageManager) != null) {
            c.startActivity(intent); return true
        }
        val fallback = Intent(Settings.ACTION_SETTINGS).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (fallback.resolveActivity(c.packageManager) != null) {
            c.startActivity(fallback); return true
        }
        return false
    }

    /** SDK_INT is read in a couple of UI spots; keep the lookup in one place. */
    val sdk: Int get() = Build.VERSION.SDK_INT
}
