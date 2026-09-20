package dev.equwal.assistkey.model

/**
 * The stock Viwoods targets, recovered from the firmware manifests shipped on
 * this device (ViwoodsLauncher 1.0.35 and viwoodsAi 2.1.52). These are what the
 * OEM key settings screen binds to, so offering them here keeps parity with
 * stock.
 *
 * ScreenCaptureActivity is the factory value of Settings.System.CustomAiKey.
 */
object Presets {

    private const val LAUNCHER = "com.viwoods.launcher"
    private const val AI = "com.viwoods.viwoodsai"

    data class Preset(val label: String, val component: String) {
        fun toSpec() = ActionSpec(ActionKind.LAUNCH_COMPONENT, component, label)
    }

    val viwoods: List<Preset> = listOf(
        Preset("AI crop (screen capture)", LAUNCHER + "/com.viwoods.libfloating.activity.ScreenCaptureActivity"),
        Preset("Edit screenshot", LAUNCHER + "/com.viwoods.libfloating.activity.EditScreenshotActivity"),
        Preset("AI quick prompt (transparent)", AI + "/com.wisky.wiskyai.AiTransparentActivity"),
        Preset("AI lookup", AI + "/com.wisky.wiskyai.AIActivity"),
        Preset("AI assistant (full UI)", AI + "/com.wisky.wiskyai.WebViewAiActivity"),
        Preset("AI history", AI + "/com.wisky.wiskyai.HistroyActivity"),
        Preset("AI repository", AI + "/com.wisky.wiskyai.RepositoryActivity"),
        Preset("Viwoods home", LAUNCHER + "/com.viwoods.launcher.main.LauncherMainActivity")
    )

    /** The factory AI-key binding, restored when the user resets. */
    const val STOCK_AI_KEY =
        "com.viwoods.launcher/com.viwoods.libfloating.activity.ScreenCaptureActivity"
}
