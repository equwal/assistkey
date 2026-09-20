package dev.equwal.assistkey.channel

import android.app.Activity
import android.os.Bundle

/**
 * The assistant role holder. Long-pressing Power fires ACTION_ASSIST at
 * whichever app holds the role, which is how a gesture the input dispatcher
 * never sees becomes an ordinary activity launch we can act on.
 *
 * Invisible, no-history, no task affinity: nothing is ever drawn and nothing
 * is left in Recents.
 */
class AssistActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ChannelEntry.handle(this, Channel.ASSISTANT)
    }
}
