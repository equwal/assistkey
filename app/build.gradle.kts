plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "dev.equwal.assistkey"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.equwal.assistkey"
        // 30 is the floor for GLOBAL_ACTION_TAKE_SCREENSHOT.
        minSdk = 30
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    buildTypes {
        release {
            // No shrinking: the app is tiny and R8 would only complicate
            // keeping the accessibility service and assist activity reachable.
            isMinifyEnabled = false
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }
}

// Deliberately no dependencies - framework APIs only, so the APK stays a few KB
// and there is nothing to audit but our own code.
dependencies { }
