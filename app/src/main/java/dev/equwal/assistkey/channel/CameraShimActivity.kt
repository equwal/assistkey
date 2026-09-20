package dev.equwal.assistkey.channel

import android.app.Activity
import android.os.Bundle

/**
 * Stands in for the camera app so that a double press of Power lands here.
 *
 * Only useful when the firmware double-press target is Camera and this app is
 * the default camera app - which also means no real camera is reachable that
 * way while the channel is on. That is the trade the checkbox represents.
 */
class CameraShimActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        ChannelEntry.handle(this, Channel.CAMERA)
    }
}
