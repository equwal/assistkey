import java.util.Properties

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

/**
 * Release signing comes from keystore.properties, which is deliberately not in
 * the repository. Without it the release build still runs and produces an
 * unsigned APK, so a fresh clone is never broken - it just cannot ship.
 */
val keystoreProps = Properties().apply {
    val f = rootProject.file("keystore.properties")
    if (f.exists()) f.inputStream().use { load(it) }
}
val hasSigning = keystoreProps.getProperty("storeFile") != null

android {
    namespace = "dev.equwal.assistkey"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.equwal.assistkey"
        // QuickAccessWalletService, which the wallet channel needs, is API 31.
        minSdk = 31
        targetSdk = 36
        versionCode = 1
        versionName = "1.0"
    }

    signingConfigs {
        if (hasSigning) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    buildTypes {
        release {
            // No shrinking: the app is tiny, and R8 would only complicate
            // keeping the accessibility service and the three impersonation
            // entry points reachable.
            isMinifyEnabled = false
            if (hasSigning) signingConfig = signingConfigs.getByName("release")
        }
        debug {
            applicationIdSuffix = ".debug"
            versionNameSuffix = "-debug"
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    kotlinOptions {
        jvmTarget = "17"
    }

    lint {
        // The whole app is built out of things a normal app has no business
        // doing; the interesting warnings are drowned out by the expected ones.
        abortOnError = false
    }
}

// Framework APIs only, so the APK stays a couple of hundred KB and there is
// nothing to audit but our own code.
dependencies { }
