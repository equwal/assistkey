package dev.equwal.assistkey.ui

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import dev.equwal.assistkey.BuildConfig
import dev.equwal.assistkey.channel.Channels
import dev.equwal.assistkey.device.Device
import dev.equwal.assistkey.engine.KeyFilterService
import dev.equwal.assistkey.model.Trigger
import dev.equwal.assistkey.route.ServiceHolder
import dev.equwal.assistkey.store.SettingsFile
import dev.equwal.assistkey.store.Store
import dev.equwal.assistkey.ui.Ui.button
import dev.equwal.assistkey.ui.Ui.header
import dev.equwal.assistkey.ui.Ui.note
import dev.equwal.assistkey.ui.Ui.title
import org.json.JSONObject

/**
 * Export and import of the settings, as one JSON file.
 *
 * What is in the file, and what can never be, is decided by [SettingsFile].
 * This screen moves the text: to a file, to another app, and back in.
 */
class BackupActivity : Activity() {

    private companion object {
        const val SAVE = 1
        const val OPEN = 2
        const val FILE_NAME = "assistkey-settings.json"
    }

    override fun onResume() {
        super.onResume()
        build()
    }

    private fun build() {
        val col = Ui.page(this)
        col.title("Export and import")
        col.note(
            "Your key bindings, timing, navigation setup, home screen and voice " +
                "typing choices, as one file. Use it to move to another device, or to " +
                "share a setup with other people."
        )

        col.header("Export")
        col.button("Save to a file") {
            start(
                Intent(Intent.ACTION_CREATE_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE)
                    .setType("application/json").putExtra(Intent.EXTRA_TITLE, FILE_NAME),
                SAVE
            )
        }
        col.button("Share") {
            start(
                Intent.createChooser(
                    Intent(Intent.ACTION_SEND).setType("text/plain")
                        .putExtra(Intent.EXTRA_SUBJECT, "AssistKey settings")
                        .putExtra(Intent.EXTRA_TEXT, export()),
                    "Share settings"
                ),
                0
            )
        }
        col.note("The file does not contain your licence, and nothing about you or your apps beyond the apps you bound to keys or put on the home screen.")

        col.header("Import")
        col.button("Open a file") {
            start(Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"), OPEN)
        }
        col.button("Paste from the clipboard") {
            val clip = getSystemService(android.content.ClipboardManager::class.java)
                ?.primaryClip?.getItemAt(0)?.coerceToText(this)?.toString()
            if (clip.isNullOrBlank()) toast("The clipboard is empty") else offer(clip)
        }
        col.note(
            "An import replaces the settings it contains and leaves the others as " +
                "they are. A binding can open an app or send an intent, so import " +
                "files only from people you trust."
        )
    }

    private fun start(i: Intent, code: Int) {
        runCatching { if (code == 0) startActivity(i) else startActivityForResult(i, code) }
            .onFailure { toast("No app on this device can do that") }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        val uri: Uri = data?.data ?: return
        if (resultCode != RESULT_OK) return
        when (requestCode) {
            SAVE -> runCatching {
                contentResolver.openOutputStream(uri)?.use { it.write(export().toByteArray()) }
            }.onSuccess { toast("Saved") }.onFailure { toast("Could not write the file") }

            OPEN -> runCatching {
                contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.UTF_8) }
            }.getOrNull()?.let(::offer) ?: toast("Could not read the file")
        }
    }

    // ---- export ---------------------------------------------------------------------------------

    private fun export(): String {
        val settings = SettingsFile.ALLOWED.keys.associateWith { file ->
            getSharedPreferences(file, Context.MODE_PRIVATE).all
        }
        return SettingsFile.encode(settings, BuildConfig.VERSION_NAME, Device.name)
    }

    // ---- import ---------------------------------------------------------------------------------

    /** Reads the text, says what is in it, and applies it only after a yes. */
    private fun offer(text: String) {
        val settings = try {
            SettingsFile.decode(text)
        } catch (e: SettingsFile.BadFile) {
            return toast(e.message ?: "This is not a settings file.")
        }
        if (settings.isEmpty()) return toast("That file has no settings in it")

        val bindings = (settings["assistkey"]?.get("bindings") as? String)
            ?.let { runCatching { JSONObject(it) }.getOrNull() }
        val count = bindings?.keys()?.asSequence()?.count { Trigger.parse(it) != null } ?: 0
        AlertDialog.Builder(this)
            .setTitle("Import these settings?")
            .setMessage(
                count.toString() + " key bindings, and settings for: " +
                    settings.keys.joinToString(", ") { it.removePrefix("assistkey_").replace("assistkey", "keys") } +
                    ".\n\nThey replace what you have now."
            )
            .setPositiveButton("Import") { _, _ -> apply(settings) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun apply(settings: Map<String, Map<String, Any>>) {
        settings.forEach { (file, values) ->
            val e = getSharedPreferences(file, Context.MODE_PRIVATE).edit()
            values.forEach { (key, value) ->
                when (value) {
                    is String -> e.putString(key, if (key == "bindings") cleanBindings(value) else value)
                    is Boolean -> e.putBoolean(key, value)
                    is Int -> e.putInt(key, value)
                    is Long -> e.putLong(key, value)
                    is Float -> e.putFloat(key, value)
                    is Set<*> -> e.putStringSet(key, value.map { it.toString() }.toSet())
                }
            }
            e.apply()
        }
        // Everything that holds a copy of the old settings has to look again.
        Store.invalidate()
        Channels.syncComponents(this)
        (ServiceHolder.service as? KeyFilterService)?.let {
            it.reloadTiming()
            it.syncPower()
        }
        toast("Imported")
        build()
    }

    /** Keeps only the bindings whose trigger this version understands. */
    private fun cleanBindings(json: String): String {
        val source = runCatching { JSONObject(json) }.getOrNull() ?: return "{}"
        val out = JSONObject()
        source.keys().forEach { id ->
            val spec = source.optJSONObject(id)
            if (Trigger.parse(id) != null && spec != null) out.put(id, spec)
        }
        return out.toString()
    }

    private fun toast(message: String) = Toast.makeText(this, message, Toast.LENGTH_LONG).show()
}
