import java.security.MessageDigest
import java.time.LocalDate
import java.time.ZoneOffset
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

fun prop(name: String): String =
    (project.findProperty(name) as String?) ?: error("missing gradle property $name")

/** Midnight UTC at the start of the given ISO date, as epoch millis. */
fun epochMillis(isoDate: String): Long =
    LocalDate.parse(isoDate)
        .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()

fun sha256Hex(text: String): String =
    MessageDigest.getInstance("SHA-256")
        .digest(text.toByteArray(Charsets.UTF_8))
        .joinToString("") { b -> "%02x".format(b) }

// Only the digest of the tester code is compiled in. With no code configured
// the digest is of the empty string, which no entered code can match.
val testerCodeHash = sha256Hex(
    (keystoreProps.getProperty("testerCode") ?: "").trim().uppercase()
)

android {
    namespace = "dev.equwal.assistkey"
    compileSdk = 36

    defaultConfig {
        applicationId = "dev.equwal.assistkey"
        // QuickAccessWalletService, which the wallet channel needs, is API 31.
        minSdk = 31
        targetSdk = 36
        versionCode = prop("assistkey.versionCode").toInt()
        versionName = prop("assistkey.versionName")

        buildConfigField(
            "long", "BETA_EXPIRES_MS",
            epochMillis(prop("assistkey.betaExpires")).toString() + "L"
        )
        buildConfigField("String", "BETA_EXPIRES_DATE", "\"" + prop("assistkey.betaExpires") + "\"")
        buildConfigField("String", "TESTER_CODE_SHA256", "\"" + testerCodeHash + "\"")
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

    // Two builds of one app, same id and same signature.
    //   play: for Google Play. No Shizuku code, no Shizuku permission.
    //   full: for direct install. Adds shell access through Shizuku, which is
    //         what reads the Power button and switches the navigation bar.
    flavorDimensions += "store"
    productFlavors {
        create("play") { dimension = "store" }
        create("full") {
            dimension = "store"
            versionNameSuffix = "-full"
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

    buildFeatures {
        buildConfig = true
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

// One dependency, and only because there is no other way to sell on Play.
// Everything else is framework API.
//
// The billing library talks to the Play Store app over IPC and needs no network
// access of its own. What does want the network is its usage telemetry, which
// rides on Google's datatransport runtime and would merge INTERNET and
// ACCESS_NETWORK_STATE into the manifest. An accessibility service that sees
// key presses should not also hold INTERNET, so that runtime is left out.
//
// This is safe by construction, not by luck: the only class in the library that
// touches datatransport (zzdn in 8.3.0) initialises it inside a catch-all and
// falls back to "Skipping logging since initialization failed". Re-check that
// with javap before bumping the billing version, and re-check the merged
// permissions with `aapt2 dump badging` after.
dependencies {
    implementation("com.android.billingclient:billing:8.3.0") {
        exclude(group = "com.google.android.datatransport")
    }

    // Shell access, `full` flavour only. Shizuku (MIT) lets the app run a small service of its own
    // under the shell uid, which is what reads the Power key and switches the
    // settings Android keeps from ordinary apps. The user starts Shizuku through
    // wireless debugging; nothing here needs root or a computer.
    "fullImplementation"("dev.rikka.shizuku:api:13.1.5")
    "fullImplementation"("dev.rikka.shizuku:provider:13.1.5")

    // Tests only, never in the APK. JUnit 4 is what the Android Gradle plugin
    // runs with no more setup; there was no test framework before it.
    testImplementation("junit:junit:4.13.2")
    // org.json is part of Android, where unit tests get an empty stub of it.
    // This is the same library as a plain jar, so SettingsFile can be tested.
    testImplementation("org.json:json:20240303")
}
