package dev.equwal.assistkey.menu

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import dev.equwal.assistkey.model.ActionKind
import dev.equwal.assistkey.model.ActionSpec
import dev.equwal.assistkey.model.Trigger
import dev.equwal.assistkey.store.Store
import dev.equwal.assistkey.ui.ActionPickerActivity
import dev.equwal.assistkey.ui.Ui
import dev.equwal.assistkey.ui.Ui.button
import dev.equwal.assistkey.ui.Ui.header
import dev.equwal.assistkey.ui.Ui.note
import dev.equwal.assistkey.ui.Ui.row
import dev.equwal.assistkey.ui.Ui.title
import org.json.JSONObject

/** Builds the menu that one gesture opens: add actions, order them, save. */
class MenuEditActivity : Activity() {

    private lateinit var trigger: Trigger
    private val items = ArrayList<ActionSpec>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        trigger = Trigger.parse(intent.getStringExtra(EXTRA_TRIGGER).orEmpty()) ?: run { finish(); return }
        val saved = savedInstanceState?.getString(STATE)
        val current = Store.bindings(this).raw(trigger)
        items.addAll(Menu.decode(saved ?: if (current.kind == ActionKind.MENU) current.payload else "[]"))
        build()
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putString(STATE, Menu.encode(items))
    }

    private fun build() {
        val col = Ui.page(this)
        col.title("Menu for " + trigger.label())
        col.note(
            "One gesture opens a menu, and the menu holds as many actions as you " +
                "want. That gives you more actions than you have keys."
        )

        col.header(if (items.isEmpty()) "No actions yet" else "Actions, in menu order")
        items.forEachIndexed { i, spec ->
            col.row(spec.describe(), "Tap to move or remove") {
                val choices = listOf("Move up", "Move down", "Remove")
                Ui.pick(this, spec.describe(), choices) { which ->
                    when (which) {
                        0 -> if (i > 0) items.add(i - 1, items.removeAt(i))
                        1 -> if (i < items.size - 1) items.add(i + 1, items.removeAt(i))
                        2 -> items.removeAt(i)
                    }
                    build()
                }
            }
        }
        col.button("Add an action") {
            startActivityForResult(ActionPickerActivity.pickIntent(this, trigger), PICK)
        }

        col.header("Finish")
        col.button(if (items.isEmpty()) "Remove the menu" else "Save the menu") {
            Store.bind(this, trigger, if (items.isEmpty()) ActionSpec.PASS else Menu.spec(items))
            setResult(RESULT_OK)
            finish()
        }
        col.button("Try it") { MenuActivity.open(this, Menu.encode(items)) }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != PICK || resultCode != RESULT_OK) return
        val json = data?.getStringExtra(ActionPickerActivity.RESULT_SPEC) ?: return
        runCatching { ActionSpec.fromJson(JSONObject(json)) }.getOrNull()?.let(items::add)
        build()
    }

    companion object {
        private const val EXTRA_TRIGGER = "trigger"
        private const val STATE = "items"
        private const val PICK = 1

        fun intent(c: Context, t: Trigger): Intent =
            Intent(c, MenuEditActivity::class.java).putExtra(EXTRA_TRIGGER, t.id)
    }
}
