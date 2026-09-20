# AssistKey

Remaps the four hardware keys on a **Viwoods AiPaper Reader** — the AI key, both
volume keys, and the power button — to arbitrary actions, including multi-tap,
press-and-hold and key combinations.

Built against firmware 1.5.6 (Android 16, SDK 36). No dependencies beyond the
Android framework; the APK is about 2 MB and everything in it is in this repo.

## Why it is shaped like this

Android does not offer one way to intercept hardware keys. It offers several
partial ones, each with different reach, and the power button has none at all.
So the app is organised around **capture channels**: independent routes by which
a key press can be made to arrive at this app. You tick the ones you want.

| Channel | Captures | How | Needs |
|---|---|---|---|
| **Accessibility key filter** | AI key, Volume up, Volume down | `AccessibilityService.onKeyEvent` with `flagRequestFilterKeyEvents` | Service enabled in Settings |
| **Digital assistant** | Power — press and hold | Holds `ROLE_ASSISTANT`; the firmware fires `ACTION_ASSIST` at the role holder | Role granted; firmware long-press set to Assistant |
| **Camera app** | Power — double press | Becomes the default camera, so the double-press camera gesture lands here | Set as default camera app |
| **Wallet app** | Power — double press, wallet tile, lock-screen wallet button | Holds `ROLE_WALLET` and serves a (empty) `QuickAccessWalletService` | Role granted |
| **Viwoods native hooks** | AI key, Volume up, Volume down | Rewrites the firmware's own `Settings.System` key bindings | `WRITE_SETTINGS` (grantable in-app) |

### The power button is not like the others

`PhoneWindowManager.interceptKeyBeforeQueueing()` consumes `KEYCODE_POWER`
before the input dispatcher runs, so **no** accessibility service, on any
Android version, can see it. That leaves exactly three reachable slots:

- **Short press** — firmware only. The app rewrites
  `Settings.Global.power_button_short_press`, so it can become Home, or nothing,
  instead of sleep. No app code runs.
- **Double press** — arrives as a camera or wallet launch, depending on what the
  firmware's double-press target is set to. Both channels are offered.
- **Press and hold** — arrives as an assistant request.

No multi-tap beyond two, and Power can never be half of a combination.
`Power + Volume up` is reserved by the firmware for the power menu, which the
app deliberately leaves alone as an escape hatch.

### Everything else

The AI key and both volume keys go through the accessibility filter, which gives
the full vocabulary: 1–5 taps, press-and-hold, and any combination of those
three keys, each bindable separately.

The gesture engine (`engine/GestureEngine.kt`) is deliberately free of Android
types so the state machine is unit-testable on the JVM. Its one rule that
matters for feel: *a key with no bindings is never consumed, and a key whose
highest bound tap count is 1 fires on key-up without waiting out the multi-tap
window.* You only pay multi-tap latency on keys where you actually asked for a
double tap.

## Actions

Parity with the stock Viwoods key screen, plus everything it does not offer:

- **Viwoods AI** — AI crop, AI lookup, quick prompt, full assistant, history,
  repository, edit screenshot, Viwoods home
- **Navigation** — Back, Home, Recents, notification shade, quick settings,
  power menu, lock screen, screenshot, D-pad, play/pause
- **Page turning** — synthetic swipes and scrolls, for readers that only take
  touch
- **Sound and media** — volume up/down/mute/panel, play-pause, next, previous
- **Anything else** — open an app, open a specific `package/class` (reaches
  activities with no launcher icon), send an intent action, send a broadcast
- **Do nothing** — swallows the key, which is how a button gets disabled

## Installing

Grab `app-release.apk` from the release build and sideload it. Then, optionally:

```bash
adb shell pm grant dev.equwal.assistkey android.permission.WRITE_SECURE_SETTINGS
```

Without that grant everything still works except the firmware-level power
switches (short press, hold duration, the double-press gesture toggles). The app
detects the refusal and prints the exact `adb` command instead of failing
silently.

## Building

```bash
./gradlew assembleRelease
```

Release signing is read from `keystore.properties` at the repo root, which is
not committed:

```properties
storeFile=assistkey-release.jks
storePassword=…
keyAlias=assistkey
keyPassword=…
```

Without it the release build still runs and produces an unsigned APK, so a fresh
clone is never broken — it just cannot ship. **Back up the keystore**: losing it
means shipped installs can never be upgraded.

### Windows note

If Gradle dies with `Unable to establish loopback connection`, `java.io.tmpdir`
is resolving through an 8.3 short path (`C:\Users\ADMINI~1\…`), which breaks the
JDK's AF_UNIX-backed `Selector.open()`. Point `TMP` and `TEMP` at a short path
such as `C:\tmp` and it goes away.

## Uninstalling cleanly

Uninstalling restores every key to firmware default **except** the ones written
through the Viwoods channel — those live in system settings and outlive the app.
Reset them from *Firmware key hooks → Restore all three keys to factory* first.

## Layout

```
model/     keys, triggers, action specs, the recovered Viwoods component list
engine/    the gesture state machine and the accessibility service
channel/   the five capture channels and their entry points
native/    firmware settings: power gestures and the Viwoods key hooks
route/     turning an action spec into behaviour
store/     persistence, with the key-event hot path precomputed
ui/        the configuration screens, built in code
```
