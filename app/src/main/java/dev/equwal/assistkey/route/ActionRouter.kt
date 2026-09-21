package dev.equwal.assistkey.route

import android.accessibilityservice.GestureDescription
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Path
import android.media.AudioManager
import android.util.Log
import android.view.KeyEvent
import android.view.accessibility.AccessibilityNodeInfo
import dev.equwal.assistkey.model.ActionKind
import dev.equwal.assistkey.model.ActionSpec
import dev.equwal.assistkey.model.GlobalAction

/** Executes an [ActionSpec]. The single place any binding turns into behaviour. */
object ActionRouter {

    private const val TAG = "AssistKey"

    fun run(ctx: Context, spec: ActionSpec): Boolean {
        return try {
            when (spec.kind) {
                ActionKind.NONE -> true
                ActionKind.PASS_THROUGH -> false
                ActionKind.GLOBAL -> global(spec.payload)
                ActionKind.VOLUME -> volume(ctx, spec.payload)
                ActionKind.MEDIA -> media(ctx, spec.payload)
                ActionKind.LAUNCH_APP -> launchApp(ctx, spec.payload)
                ActionKind.LAUNCH_COMPONENT -> launchComponent(ctx, spec.payload)
                ActionKind.LAUNCH_ACTION -> launchAction(ctx, spec.payload)
                ActionKind.BROADCAST -> broadcast(ctx, spec.payload)
                ActionKind.SWIPE -> swipe(spec.payload)
                ActionKind.SCROLL -> scroll(spec.payload)
                ActionKind.MENU -> dev.equwal.assistkey.menu.MenuActivity.open(ctx, spec.payload)
                ActionKind.DIM -> {
                    dev.equwal.assistkey.display.ExtraDim.act(ctx.applicationContext, spec.payload)
                    true
                }
                ActionKind.VOICE -> ServiceHolder.service
                    ?.let { dev.equwal.assistkey.voice.Dictation.toggle(it) } ?: false
            }
        } catch (e: Exception) {
            Log.w(TAG, "action " + spec.kind + "/" + spec.payload + " failed", e)
            false
        }
    }

    private fun global(name: String): Boolean {
        val svc = ServiceHolder.service ?: return false
        val action = GlobalAction.fromName(name) ?: return false
        if (!action.available) return false
        if (action == GlobalAction.HOME_CLOSE_IME) return homeClosingKeyboard(svc)
        return svc.performGlobalAction(action.id)
    }

    /**
     * Home alone leaves a keyboard floating over the launcher on some firmware,
     * so an open keyboard is dismissed first. Seeing the keyboard's window needs
     * flagRetrieveInteractiveWindows; if the list is unavailable this is Home.
     */
    private fun homeClosingKeyboard(svc: android.accessibilityservice.AccessibilityService): Boolean {
        val keyboardUp = runCatching {
            svc.windows.any { it.type == android.view.accessibility.AccessibilityWindowInfo.TYPE_INPUT_METHOD }
        }.getOrDefault(false)
        if (!keyboardUp) return svc.performGlobalAction(GlobalAction.HOME.id)
        svc.performGlobalAction(GlobalAction.BACK.id)
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(
            { svc.performGlobalAction(GlobalAction.HOME.id) }, 180L
        )
        return true
    }

    private fun volume(ctx: Context, dir: String): Boolean {
        val am = ctx.getSystemService(AudioManager::class.java) ?: return false
        val adjust = when (dir) {
            "raise" -> AudioManager.ADJUST_RAISE
            "lower" -> AudioManager.ADJUST_LOWER
            "mute" -> AudioManager.ADJUST_TOGGLE_MUTE
            "panel" -> AudioManager.ADJUST_SAME
            else -> return false
        }
        am.adjustSuggestedStreamVolume(
            adjust,
            AudioManager.USE_DEFAULT_STREAM_TYPE,
            AudioManager.FLAG_SHOW_UI
        )
        return true
    }

    private fun media(ctx: Context, what: String): Boolean {
        val code = when (what) {
            "play_pause" -> KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE
            "next" -> KeyEvent.KEYCODE_MEDIA_NEXT
            "previous" -> KeyEvent.KEYCODE_MEDIA_PREVIOUS
            "stop" -> KeyEvent.KEYCODE_MEDIA_STOP
            else -> return false
        }
        val am = ctx.getSystemService(AudioManager::class.java) ?: return false
        val now = System.currentTimeMillis()
        am.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, code, 0))
        am.dispatchMediaKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, code, 0))
        return true
    }

    private fun launchApp(ctx: Context, pkg: String): Boolean {
        val i = ctx.packageManager.getLaunchIntentForPackage(pkg) ?: return false
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
        return true
    }

    /**
     * An explicit component needs no intent-filter on the target, which is how
     * the internal Viwoods AI activities get reached.
     */
    private fun launchComponent(ctx: Context, flat: String): Boolean {
        val cn = ComponentName.unflattenFromString(flat) ?: return false
        val i = Intent()
        i.component = cn
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        ctx.startActivity(i)
        return true
    }

    private fun launchAction(ctx: Context, action: String): Boolean {
        val i = Intent(action).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        if (i.resolveActivity(ctx.packageManager) == null) return false
        ctx.startActivity(i)
        return true
    }

    private fun broadcast(ctx: Context, action: String): Boolean {
        ctx.sendBroadcast(Intent(action))
        return true
    }

    /**
     * Synthetic swipe - the page-turn action for reader apps that only respond
     * to touch. Uses display bounds so it scales to any screen.
     */
    private fun swipe(dir: String): Boolean {
        val svc = ServiceHolder.service ?: return false
        val dm = svc.resources.displayMetrics
        val w = dm.widthPixels.toFloat()
        val h = dm.heightPixels.toFloat()
        val cx = w / 2f
        val cy = h / 2f
        val dx = w * 0.35f
        val dy = h * 0.35f

        var sx = cx; var sy = cy; var ex = cx; var ey = cy
        when (dir) {
            "left" -> { sx = cx + dx; ex = cx - dx }
            "right" -> { sx = cx - dx; ex = cx + dx }
            "up" -> { sy = cy + dy; ey = cy - dy }
            "down" -> { sy = cy - dy; ey = cy + dy }
            else -> return false
        }

        val path = Path()
        path.moveTo(sx, sy)
        path.lineTo(ex, ey)
        val stroke = GestureDescription.StrokeDescription(path, 0L, 120L)
        return svc.dispatchGesture(
            GestureDescription.Builder().addStroke(stroke).build(), null, null
        )
    }

    private fun scroll(dir: String): Boolean {
        val svc = ServiceHolder.service ?: return false
        val action = if (dir == "backward") {
            AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD
        } else {
            AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
        }
        val root = svc.rootInActiveWindow ?: return false
        val target = findScrollable(root) ?: return false
        return target.performAction(action)
    }

    private fun findScrollable(node: AccessibilityNodeInfo?): AccessibilityNodeInfo? {
        if (node == null) return null
        if (node.isScrollable) return node
        for (i in 0 until node.childCount) {
            val hit = findScrollable(node.getChild(i))
            if (hit != null) return hit
        }
        return null
    }

    /** True when this action cannot run without the accessibility service. */
    fun requiresAccessibility(spec: ActionSpec): Boolean = when (spec.kind) {
        ActionKind.GLOBAL, ActionKind.SWIPE, ActionKind.SCROLL, ActionKind.VOICE -> true
        else -> false
    }
}
