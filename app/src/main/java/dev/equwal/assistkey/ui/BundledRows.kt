package dev.equwal.assistkey.ui

import android.app.Activity
import android.widget.LinearLayout
import dev.equwal.assistkey.bundle.Bundled
import dev.equwal.assistkey.ui.Ui.header
import dev.equwal.assistkey.ui.Ui.row

/** The extensions that belong on one screen: installed, or one tap to get. */
object BundledRows {

    fun add(a: Activity, col: LinearLayout, group: String) {
        val items = Bundled.items.filter { it.group == group }
        if (items.isEmpty()) return
        col.header(if (group == Bundled.UPDATE) "Updates" else "Extensions")
        items.forEach { item ->
            val installed = runCatching { a.packageManager.getPackageInfo(item.pkg, 0) }.isSuccess
            col.row(
                item.title,
                item.hint + " · " + item.version + " · " + item.licence,
                state = if (installed) "Installed" else if (Bundled.SUPPORTED) "Install" else "Get it"
            ) {
                if (!installed) Bundled.install(a, item)
                else a.packageManager.getLaunchIntentForPackage(item.pkg)?.let(a::startActivity)
            }
        }
    }
}
