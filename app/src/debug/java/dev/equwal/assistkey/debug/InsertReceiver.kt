package dev.equwal.assistkey.debug

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import dev.equwal.assistkey.route.ServiceHolder
import dev.equwal.assistkey.voice.TextInsert

/**
 * Debug builds only. Lets a test put text at the cursor with no speech, because
 * a test bench cannot talk.
 */
class InsertReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val svc = ServiceHolder.service
        val result = if (svc == null) "NO_SERVICE" else TextInsert.insert(svc, i.getStringExtra("text").orEmpty()).name
        Log.i("AssistKey", "insert test: " + result)
    }
}
