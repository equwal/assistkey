# AssistKey

Remaps the four hardware keys on a **Viwoods AiPaper Reader** — the AI key, both
volume keys, and the power button — to arbitrary actions, including multi-tap,
press-and-hold and key combinations.

Built against firmware 1.5.6 (Android 16, SDK 36). One dependency, Google's
Play Billing Library, and only because there is no other way to sell on Play.
The app holds no `INTERNET` permission.

Sold on Google Play as a free download with a one-time licence. Publishing,
products, pricing and how the beta is run and ended are in
[play/CHECKLIST.md](play/CHECKLIST.md).

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
| **Wallet app** | Wallet tile, lock-screen wallet button; double-press Power on firmware that targets the wallet | Holds `ROLE_WALLET` and serves an (empty) `QuickAccessWalletService` | Role granted |

### What was measured, and how

`adb shell input keyevent` is useless for this: injected events skip the input
filter stage, so they say nothing about what a real press does. Everything
below was measured as root with `sendevent` on the kernel input nodes, which
enters the pipeline exactly where the hardware does.

| Press | Node | Result |
|---|---|---|
| AI key (`KEY_F1`) | `event5` "AI KEY" | Reaches the accessibility filter, whatever `CustomAiKey` holds |
| Volume up / down | `event1` / `event2` | Reaches the filter **only while its firmware hook is unset** |
| Power, held 700 ms | `event1` | Firmware fires `ACTION_ASSIST` at the assistant role holder — us |
| Power, twice within 300 ms | `event1` | `GestureLauncherService` fires `STILL_IMAGE_CAMERA`; never the wallet |

### Firmware hooks can hide a key

The firmware has its own per-key settings in `Settings.System`:
`CustomAiKey`, `CustomVolumeUpKey`, `CustomVolumeDownKey`. While a volume hook
holds **any** value — even its default token, `volume_up` — the firmware deals
with that key before the filter stage and no app ever sees it. Unset, the key
arrives normally. The device's own key-settings screen sets them.

An ordinary app cannot write these. `SettingsProvider` rejects any
`Settings.System` name outside its public list unless the caller is a
privileged system app, with or without `WRITE_SECURE_SETTINGS`:
*"You cannot keep your settings in the secure settings."* So
*Advanced → Firmware key hooks* is read-only: it shows each hook, says when one
is hiding a key, and gives the fix:

```bash
adb shell settings delete system CustomVolumeUpKey
adb shell settings delete system CustomVolumeDownKey
```

### The power button is not like the others

`PhoneWindowManager.interceptKeyBeforeQueueing()` consumes `KEYCODE_POWER`
before the input dispatcher runs, so **no** accessibility service, on any
Android version, can see it. That leaves exactly three reachable slots:

- **Short press** — firmware only. The app rewrites
  `Settings.Global.power_button_short_press`, so it can become Home, or nothing,
  instead of sleep. No app code runs.
- **Double press** — arrives as a camera launch. With more than one camera app
  installed Android shows a chooser the first time; pick AssistKey and
  *Always*.
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

From Google Play, or sideload `AssistKey-<version>.apk` from the GitHub
release. Both are the same build with the same signature.

On a Viwoods reader Google Play is switched off out of the box. The app works
without it; buying a licence does not.

### The accessibility switch will not stay on until you do this

Android blocks **restricted settings** for anything installed outside an app
store, and accessibility is one of them. The symptom is silent and confusing:
the switch appears to turn on, then reverts a moment later with no message.

*App info → three-dot menu → Allow restricted settings*, then enable the
service. The app links straight to App info from the accessibility channel.

Over adb the equivalent is:

```bash
adb shell appops set dev.equwal.assistkey ACCESS_RESTRICTED_SETTINGS allow
```

Note that `adb shell am force-stop` on this app disables its accessibility
service again, so re-enable it after any force-stop or reinstall.

### Optional: the firmware power switches

```bash
adb shell pm grant dev.equwal.assistkey android.permission.WRITE_SECURE_SETTINGS
```

Without that grant everything still works except the firmware-level power
switches (short press, hold duration, the double-press gesture toggles). The app
detects the refusal and prints the exact `adb` command instead of failing
silently.

Note that this permission only unlocks *writes*. Since Android 12 those keys
cannot be **read** by a non-system app at all — `Settings.Global.getInt` throws
`SecurityException` — so the app shows their current values as "unknown". That
is a platform restriction, not a bug.

### Key tester

*Advanced → Key tester* lists every key event that reaches the filter. It is the
only reliable way to find out what a given device allows, because a key consumed
upstream by the window manager never reaches any app and simply never appears.
Note that events injected with `adb shell input keyevent` **bypass**
accessibility input filters entirely, so only real presses tell you anything.

## Licensing

Four tiers, tried in order (`license/License.kt`):

| Tier | When | Effect |
|---|---|---|
| Licensed | Play reports `assistkey_pro` or `assistkey_pro_tester` owned | Everything works; cached, so it survives being offline |
| Beta | The beta is open | Everything works, free; the install marks itself as a tester |
| Trial | Beta closed, under 7 days since first launch | Everything works |
| Locked | Otherwise | Key filter and entry points go inert; bindings are kept |

"Is the beta open" is answered by Google Play, not by a server. A third
product, `assistkey_beta_open`, is never sold and exists only as a flag:
deactivate it in Play Console and the beta ends everywhere. Only "paid product
visible **and** flag missing", in one response, reads as closed — so a failed
or empty answer can never lock anyone out. Installs that cannot reach Play fall
back to `assistkey.betaExpires` in `gradle.properties`.

Testers are then offered `assistkey_pro_tester`, the same licence for less. A
tester on a new device types the tester code instead; only its SHA-256 ships.

The billing library's telemetry runtime (`datatransport`) is excluded from the
build, because it would merge `INTERNET` into the manifest. The library wraps
that runtime's start-up in a catch-all and logs *"Skipping logging since
initialization failed"*; that was confirmed in bytecode and on the device.
**Re-check it before bumping the billing version.**

## Building

```bash
./gradlew assembleRelease bundleRelease
```

`assembleRelease` makes the APK for testers, `bundleRelease` the `.aab` for
Play. The version comes from `gradle.properties`; `versionCode` must rise with
every upload.

Release signing is read from `keystore.properties` at the repo root, which is
not committed:

```properties
storeFile=assistkey-release.jks
storePassword=…
keyAlias=assistkey
keyPassword=…
testerCode=…
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

Uninstalling restores every key to its firmware behaviour. The app writes
nothing that outlives it, unless you granted `WRITE_SECURE_SETTINGS` and changed
the firmware power switches, which are ordinary system settings.

## Layout

```
model/     keys, triggers, action specs, the recovered Viwoods component list
engine/    the gesture state machine and the accessibility service
channel/   the four capture channels and their entry points
license/   licence tiers and Google Play Billing
native/    firmware settings: power gestures, and the read-only key hooks
route/     turning an action spec into behaviour
store/     persistence, with the key-event hot path precomputed
ui/        the configuration screens, built in code

play/      everything for the Play Console: checklist, listing, policy, graphics
src/debug/ a debug-only hook that renders each screen to a PNG, because the
           e-ink panel defeats `adb shell screencap`
```
