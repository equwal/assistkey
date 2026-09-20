package dev.equwal.assistkey.channel

import android.app.Activity
import android.os.Bundle

/**
 * The wallet role holder. Reached by a double press of Power when the firmware
 * target is Wallet, and also by the lock-screen wallet button and the
 * quick-settings tile - three routes into the same binding.
 */
class WalletActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ChannelEntry.handle(this, Channel.WALLET)
    }
}
