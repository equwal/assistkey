package dev.equwal.assistkey.debug

import android.app.Activity
import android.app.Application
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.File

/**
 * Store screenshots, for a device that cannot take them.
 *
 * A second after any screen comes to the front, its view tree is drawn into a
 * bitmap and written to the app's external files directory:
 *
 *   adb pull /sdcard/Android/data/dev.equwal.assistkey.debug/files/shots
 */
class ShotApp : Application() {

    override fun onCreate() {
        super.onCreate()
        val main = Handler(Looper.getMainLooper())
        registerActivityLifecycleCallbacks(object : ActivityLifecycleCallbacks {
            override fun onActivityResumed(a: Activity) {
                main.postDelayed({ if (!a.isFinishing) shoot(a) }, 1200L)
            }
            override fun onActivityCreated(a: Activity, b: Bundle?) = Unit
            override fun onActivityStarted(a: Activity) = Unit
            override fun onActivityPaused(a: Activity) = Unit
            override fun onActivityStopped(a: Activity) = Unit
            override fun onActivitySaveInstanceState(a: Activity, b: Bundle) = Unit
            override fun onActivityDestroyed(a: Activity) = Unit
        })
    }

    private fun shoot(a: Activity) {
        val v = a.window.decorView
        if (v.width == 0 || v.height == 0) return
        val bmp = Bitmap.createBitmap(v.width, v.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bmp)
        canvas.drawColor(Color.WHITE)
        v.draw(canvas)
        val dir = File(getExternalFilesDir(null), "shots").apply { mkdirs() }
        val out = File(dir, a.javaClass.simpleName + ".png")
        out.outputStream().use { bmp.compress(Bitmap.CompressFormat.PNG, 100, it) }
        Log.i("AssistKey", "shot " + out.absolutePath)
    }
}
