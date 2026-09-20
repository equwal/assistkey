package dev.equwal.assistkey.channel

import android.service.quickaccesswallet.GetWalletCardsCallback
import android.service.quickaccesswallet.GetWalletCardsRequest
import android.service.quickaccesswallet.GetWalletCardsResponse
import android.service.quickaccesswallet.QuickAccessWalletService
import android.service.quickaccesswallet.SelectWalletCardRequest

/**
 * Present only because the wallet role will not be offered to an app that
 * cannot serve cards. It serves none - the useful half of the channel is
 * [WalletActivity], which is where the role holder actually gets launched.
 */
class WalletService : QuickAccessWalletService() {

    override fun onWalletCardsRequested(
        request: GetWalletCardsRequest,
        callback: GetWalletCardsCallback
    ) {
        callback.onSuccess(GetWalletCardsResponse(emptyList(), 0))
    }

    override fun onWalletCardSelected(request: SelectWalletCardRequest) { /* no cards */ }

    override fun onWalletDismissed() { /* nothing to tear down */ }
}
