package dev.equwal.assistkey.store

import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.os.Environment
import android.os.Handler
import android.os.HandlerThread
import android.provider.MediaStore
import android.util.Log
import dev.equwal.assistkey.BuildConfig
import dev.equwal.assistkey.device.Device

/**
 * A copy of the settings outside the app, kept up to date by itself.
 *
 * An update keeps the settings: they are in the data of the app, and Android
 * keeps that data when a new version goes over the old one. An uninstall
 * removes the data. This copy is in the shared Documents folder, which an
 * uninstall does not touch, so the user can import it after a new install.
 *
 * The copy holds what an export holds, and nothing else. See [SettingsFile].
 * It needs no permission: an app may write its own file to Documents.
 */
object AutoBackup {

    const val FOLDER = "Rebind"
    const val FILE_NAME = "rebind-settings.json"

    /** For the user: where the copy is. */
    val PLACE = Environment.DIRECTORY_DOCUMENTS + "/" + FOLDER + "/" + FILE_NAME

    private const val TAG = "AssistKey"
    private const val DELAY_MS = 3000L

    private val thread by lazy { HandlerThread("rebind-backup").apply { start() } }
    private val worker by lazy { Handler(thread.looper) }

    // SharedPreferences holds its listeners weakly. These references keep them alive.
    private val listeners = ArrayList<SharedPreferences.OnSharedPreferenceChangeListener>()

    /** The settings as one JSON text, the same text that Export writes. */
    fun export(c: Context): String {
        val settings = SettingsFile.ALLOWED.keys.associateWith { file ->
            c.getSharedPreferences(file, Context.MODE_PRIVATE).all
        }
        return SettingsFile.encode(settings, BuildConfig.VERSION_NAME, Device.name)
    }

    /**
     * Starts to watch the settings. Each change writes the copy again, a few
     * seconds later, so that a run of changes makes one write. Safe to call
     * more than once.
     */
    @Synchronized
    fun watch(context: Context) {
        if (listeners.isNotEmpty()) return
        val app = context.applicationContext
        SettingsFile.ALLOWED.keys.forEach { file ->
            val l = SharedPreferences.OnSharedPreferenceChangeListener { _, _ -> schedule(app) }
            listeners += l
            app.getSharedPreferences(file, Context.MODE_PRIVATE).registerOnSharedPreferenceChangeListener(l)
        }
        // One write at the start, so that a user who only updates has a copy too.
        schedule(app)
    }

    private val token = Any()

    private fun schedule(app: Context) {
        worker.removeCallbacksAndMessages(token)
        worker.postDelayed({ write(app) }, token, DELAY_MS)
    }

    /** Writes the copy now. False when the device refused. The app works without the copy. */
    fun write(c: Context): Boolean = runCatching {
        val resolver = c.contentResolver
        val files = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val path = Environment.DIRECTORY_DOCUMENTS + "/" + FOLDER + "/"
        // The file of this install, if there is one. After a new install the old
        // file belongs to nobody, and Android gives the new file a name of its own.
        val mine = resolver.query(
            files, arrayOf(MediaStore.MediaColumns._ID),
            MediaStore.MediaColumns.RELATIVE_PATH + "=? AND " + MediaStore.MediaColumns.DISPLAY_NAME + "=?",
            arrayOf(path, FILE_NAME), null
        )?.use { if (it.moveToFirst()) android.content.ContentUris.withAppendedId(files, it.getLong(0)) else null }
        val target = mine ?: resolver.insert(
            files,
            ContentValues().apply {
                put(MediaStore.MediaColumns.DISPLAY_NAME, FILE_NAME)
                put(MediaStore.MediaColumns.MIME_TYPE, "application/json")
                put(MediaStore.MediaColumns.RELATIVE_PATH, path)
            }
        ) ?: return false
        resolver.openOutputStream(target, "wt")?.use { it.write(export(c).toByteArray()) } ?: return false
        true
    }.onFailure { Log.w(TAG, "settings copy not written", it) }.getOrDefault(false)
}
