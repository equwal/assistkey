package dev.equwal.assistkey.home

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Process
import dev.equwal.assistkey.shell.Shell

/** The apps on this device, as the home screen and the recents list need them. */
object Apps {

    data class App(val label: String, val pkg: String, val activity: String) {
        val component: ComponentName get() = ComponentName(pkg, activity)
    }

    private const val PREFS = "assistkey_home"
    private const val K_FAVS = "favourites"      // ordered, newline separated packages
    private const val K_HIDDEN = "hidden"
    private const val K_CLOCK = "clock"
    private const val K_AUTO = "auto_open"

    private fun prefs(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    /** Everything with a launcher icon, alphabetical, ourselves included. */
    fun all(c: Context): List<App> {
        val pm = c.packageManager
        val main = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        return pm.queryIntentActivities(main, 0)
            .map { App(it.loadLabel(pm).toString(), it.activityInfo.packageName, it.activityInfo.name) }
            .distinctBy { it.pkg + "/" + it.activity }
            .sortedBy { it.label.lowercase() }
    }

    fun visible(c: Context): List<App> = hidden(c).let { h -> all(c).filter { it.pkg !in h } }

    fun launch(c: Context, app: App): Boolean = runCatching {
        c.startActivity(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
                .setComponent(app.component)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_RESET_TASK_IF_NEEDED)
        )
        true
    }.getOrDefault(false)

    // ---- preferences -------------------------------------------------------------------------

    fun favourites(c: Context): List<String> =
        prefs(c).getString(K_FAVS, "").orEmpty().lines().filter { it.isNotBlank() }

    fun setFavourites(c: Context, pkgs: List<String>) {
        prefs(c).edit().putString(K_FAVS, pkgs.distinct().joinToString("\n")).apply()
    }

    fun toggleFavourite(c: Context, pkg: String) {
        val f = favourites(c)
        setFavourites(c, if (pkg in f) f - pkg else f + pkg)
    }

    fun hidden(c: Context): Set<String> = prefs(c).getStringSet(K_HIDDEN, emptySet()).orEmpty()

    fun toggleHidden(c: Context, pkg: String) {
        val h = hidden(c)
        prefs(c).edit().putStringSet(K_HIDDEN, if (pkg in h) h - pkg else h + pkg).apply()
    }

    fun showClock(c: Context): Boolean = prefs(c).getBoolean(K_CLOCK, true)
    fun setShowClock(c: Context, on: Boolean) = prefs(c).edit().putBoolean(K_CLOCK, on).apply()

    /** Open the app by itself once typing has narrowed the list to one. */
    fun autoOpen(c: Context): Boolean = prefs(c).getBoolean(K_AUTO, true)
    fun setAutoOpen(c: Context, on: Boolean) = prefs(c).edit().putBoolean(K_AUTO, on).apply()

    // ---- recents -----------------------------------------------------------------------------

    fun hasUsageAccess(c: Context): Boolean {
        val ops = c.getSystemService(android.app.AppOpsManager::class.java) ?: return false
        return ops.unsafeCheckOpNoThrow(
            android.app.AppOpsManager.OPSTR_GET_USAGE_STATS, Process.myUid(), c.packageName
        ) == android.app.AppOpsManager.MODE_ALLOWED
    }

    /** Most recent first, from the usage log. Needs the user's usage-access grant. */
    fun recentFromUsage(c: Context, limit: Int = 12): List<App> {
        val usm = c.getSystemService(UsageStatsManager::class.java) ?: return emptyList()
        val now = System.currentTimeMillis()
        val events = usm.queryEvents(now - 3L * 24 * 60 * 60 * 1000, now)
        val last = LinkedHashMap<String, Long>()
        val e = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(e)
            if (e.eventType == UsageEvents.Event.ACTIVITY_RESUMED) last[e.packageName] = e.timeStamp
        }
        return rank(c, last.entries.sortedByDescending { it.value }.map { it.key }, limit)
    }

    /** Most recent first, from the system's real task list. Needs shell access. */
    fun recentFromShell(c: Context, limit: Int = 12, done: (List<App>?) -> Unit) {
        if (!Shell.ready) return done(null)
        Shell.run("dumpsys activity recents") { r ->
            if (!r.ok) return@run done(null)
            val task = Regex("""Recent #\d+: Task\{\S+ #\d+ type=(\w+) A=\d+:(\S+)""")
            val pkgs = task.findAll(r.output)
                .filter { it.groupValues[1] == "standard" }
                .map { it.groupValues[2].trimEnd('}') }
                .toList()
            done(rank(c, pkgs, limit))
        }
    }

    private fun rank(c: Context, pkgs: List<String>, limit: Int): List<App> {
        val byPkg = all(c).associateBy { it.pkg }
        val home = homePackages(c)
        return pkgs.distinct()
            .filter { it != c.packageName && it !in home }
            .mapNotNull { byPkg[it] }
            .take(limit)
    }

    private fun homePackages(c: Context): Set<String> =
        c.packageManager.queryIntentActivities(
            Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME), PackageManager.MATCH_DEFAULT_ONLY
        ).map { it.activityInfo.packageName }.toSet()
}
