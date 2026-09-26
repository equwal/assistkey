# Keep all names. The code names its own activities and services in strings
# (for example Channel and ViwoodsBridge), and stored settings hold names too.
-dontobfuscate

# app/build.gradle.kts leaves out com.google.android.datatransport, the telemetry
# of Play Billing, on purpose. So its classes are missing from the play and full builds.
-dontwarn com.google.android.datatransport.**

# Shell.kt calls the private method Shizuku.newProcess by reflection.
# Keep it, so that R8 does not remove it.
-keepclassmembers class rikka.shizuku.Shizuku {
    *** newProcess(java.lang.String[], java.lang.String[], java.lang.String);
}
