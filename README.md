# AssistKey

Remaps the hardware keys of an Android device - volume keys, the Power button,
and whatever else the device has - to arbitrary actions, with multi-tap,
press-and-hold and key combinations. Also: navigation in any mix of button bar,
gestures and the Power key; a frontlight level below the system's floor; a
plain text home screen and recent-apps list made for e-ink.

It runs on any Android 12+ device. The **Viwoods AiPaper Reader** is the first
device profile, because that is where it was built; see *Device profiles*.

Sold on Google Play as a free download with a one-time licence. Publishing,
products, pricing and how the beta is run and ended are in
[play/CHECKLIST.md](play/CHECKLIST.md). The app holds no `INTERNET` permission.

## Two builds

One app, one id, one signature, two flavours:

| Flavour | For | Shizuku |
|---|---|---|
| `play` | Google Play. `./gradlew bundlePlayRelease` | No code, no permission |
| `full` | Direct install. `./gradlew assembleFullRelease` | Yes |

Shizuku is on Google Play and so are apps that use it, so Play does not forbid
it. The `play` flavour leaves it out anyway: an app that holds an accessibility
service and a shell has a harder review, and the store build must be the safe
one. `shell/Shell.kt` exists once per flavour with the same surface; the `play`
one always answers "not available", and every feature falls back by itself.
`Shell.SUPPORTED` hides the rows that would lead nowhere.

## Shell access

*`full` flavour only.*

Android keeps several things from every installed app: the Power key, the
navigation bar, system gestures, a maker's own key settings, the backlight
node. The shell user can reach them. [Shizuku](https://shizuku.rikka.app/)
(MIT API, free app) gives the app that user from the device itself, through
wireless debugging - no computer, no root. *Setup > Shell access* walks through
it. Everything that depends on it degrades honestly without it.

| With shell access | Without |
|---|---|
| Power: any taps, hold, true combinations, read from `/dev/input` | Hold (assistant role) and double press (default camera) only |
| Bar and gestures switched in-app | The adb commands are shown |
| Recent apps: the real task list, apps can be closed | Rebuilt from the usage log |
| Extra-dim light (needs root-level shell) | Not available |

Implementation notes that cost time:

- Shizuku's bound *user service* cannot be used: its starter dies inside
  `LoadedApk.makeApplication` on Android 16 before our class loads. `Shell`
  uses Shizuku's remote-process call, and the key watcher is a pipe from
  `getevent -q <node>` on the nodes that declare the key.
- While Power is managed, `power_button_short_press`, `power_button_long_press`
  and the double-tap camera gesture are set to nothing; the firmware values are
  saved first and restored whenever shell access, the licence, the bindings or
  the service stop justifying it. Restoring needs no shell: the app grants
  itself `WRITE_SECURE_SETTINGS` through the shell on first contact. The press
  that wakes the screen is never a gesture; Power + Volume up stays the power
  menu.

## Device profiles

`device/Device.kt`. A profile adds only what a maker did differently: extra
keys, its own key settings, a gesture switch, a brightness floor. Unknown
devices get the generic profile plus any supported key the key filter has
actually seen (a page-turn button appears the first time it is pressed).

New profiles come from **device reports** (*Advanced > Device report*): model,
firmware, input devices and the keys they declare, navigation overlays, a
whitelist of key and navigation settings, the light's range. The user sees
every line and sends it themselves by email or share sheet. There is no
automatic telemetry, because there is no `INTERNET` permission; adding one is a
deliberate product decision, not a code change.

## Extra-dim light

On the Viwoods reader the frontlight is a backlight LED whose driver accepts
1-255 (2047 real steps), but the framework snaps anything under 5 to zero, by
every official route including `cmd display set-brightness`. Writing
`/sys/class/leds/lcd-backlight/brightness` directly gets under the floor and
sticks until the system slider is moved. The node belongs to `system`, so it
needs Shizuku running as root or a shell that may `su` (userdebug firmware).

## Voice typing

`voice/`. The action *Typing > Voice typing* turns a key into a dictation key.
The app has no speech engine and no network access. It calls the Android
`SpeechRecognizer` interface, so a speech recognition app on the device does
the listening: the system one, an offline Whisper app, or any other app that
offers a `RecognitionService`. `TextInsert` then puts the words into the field
that has input focus, through the accessibility service: `ACTION_SET_TEXT` at
the selection, with a clipboard paste as the fallback. It never writes into a
password field.

Two facts cost time:

- Android gives the microphone to the app in front only, and an accessibility
  service does not count (`RECORD_AUDIO` app-op mode is `foreground`). The
  listening therefore runs in `DictationActivity`, which is see-through and
  cannot take focus or touches.
- The speech recognition app needs its own microphone permission too. If it has
  none, the framework answers `ERROR_INSUFFICIENT_PERMISSIONS` and blames the
  caller.

The Viwoods voice prompt is not reusable. It uploads audio to the Viwoods cloud
(`/api/v1/openAi/speechToTextGemini`) and offers no interface to other apps.

Debug builds carry `FakeRecognitionService`, which hears a fixed sentence, so
the whole chain can be tested on a bench with no voice.

## Home screen and recent apps

`home/`. The home screen is a clock, a few chosen apps and a search line; the
app list is hidden until you type, and a single match opens itself. It is
original code: the idea is shared with CLauncher/Olauncher, which are GPL-3.0
and therefore cannot be copied into this app. Disabled in the manifest until
switched on. Recent apps is a text list in place of the screenshot carousel.

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
| AI key (`KEY_F1`), stock or unset hook | `event5` "AI KEY" | Reaches the filter, **and the firmware opens its AI screen anyway**, consumed or not |
| AI key, hook set to anything else | `event5` | Firmware launches that component; the filter never sees the key |
| Power held, then another key | `event1` + key | The second key reaches the filter; no screenshot or power-menu chord fires |
| Volume up / down | `event1` / `event2` | Reaches the filter **only while its firmware hook is unset** |
| Power, held 700 ms | `event1` | Firmware fires `ACTION_ASSIST` at the assistant role holder — us |
| Power, twice within 300 ms | `event1` | `GestureLauncherService` fires `STILL_IMAGE_CAMERA`; never the wallet |

### Firmware hooks can hide a key

The firmware has its own per-key settings in `Settings.System`:
`CustomAiKey`, `CustomVolumeUpKey`, `CustomVolumeDownKey`. While a volume hook
holds **any** value — even its default token, `volume_up` — the firmware deals
with that key before the filter stage and no app ever sees it. Unset, the key
arrives normally. The device's own key-settings screen sets them.

The AI key is worse. With the stock hook the filter does see it, but the
firmware opens its AI screen on every press regardless, so a binding fires on
top of that screen. The only clean route is to point the hook at this app's
`AiKeyActivity`: every press then arrives as a launch, which is enough to count
taps (but not to see a release, so no hold and no volume combinations):

```bash
tools/ai-key hook      # or: unhook, status
```

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

That is the position **without shell access**. With it, none of this section
applies - see *Shell access*. Without it there is no multi-tap beyond two, and
Power cannot take part in an ordinary combination, but there is one way in: a held Power announces itself, because the firmware
fires the assistant at us, and a key pressed while it is still down reaches the
filter. So **hold Power, then press** the AI key, Volume up or Volume down is
three real combinations. While any is bound, the plain hold action waits one
second to see whether a key follows.
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

## Navigation

*Navigation* on the main screen mixes three ways of getting around: the
three-button bar, swipe gestures, and Power key combinations (defaults: Power
then Volume up = Back, the AI key = Home, Volume down = Recents). Five named
setups, or tick any mix.

The combinations are the app's own. The bar and the gestures are the system's,
and Android lets no app switch them, so the screen shows the current state and
the commands, and `tools/nav-mode` runs them:

```bash
tools/nav-mode buttons   nogestures    # bar only, no gestures at all
tools/nav-mode nobuttons gestures      # gestures only
tools/nav-mode nobuttons nogestures    # neither: Power combinations only
tools/nav-mode status
```

| Piece | Switch |
|---|---|
| Button bar | overlay `com.android.internal.systemui.navbar.threebutton` / `.gestural` (gestural removes the bar outright on this firmware) |
| Swipe up for Home (Viwoods' own, both modes) | `Settings.System disable_gesture_bottom` |
| Edge swipe for Back (gestural mode only) | `Settings.Secure back_gesture_inset_scale_left/right` = 0 |

Order matters: changing the overlay makes SystemUI forget
`disable_gesture_bottom`, so the overlay goes first.

Back, Home and Recents on a Power combination keep working when the app is
locked. A reader with no bar and no gestures is navigated entirely by them, and
an expired trial must not turn it into a brick.

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
./gradlew assembleFullRelease assemblePlayRelease bundlePlayRelease
```

`bundlePlayRelease` makes the `.aab` for Play. `assembleFullRelease` makes the
APK with shell access for direct install. `assemblePlayRelease` makes the store
build as an APK, for testing it on a device. The version comes from `gradle.properties`; `versionCode` must rise with
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
channel/   the key filter switch and the Power side doors (assistant, camera, wallet)
device/    device profiles
display/   the extra-dim light
home/      the home screen and the recent-apps list
shell/     shell access through Shizuku, and the managed Power button
voice/     voice typing: recognizer choice, the listening screen, text insert
license/   licence tiers and Google Play Billing
native/    firmware settings: power gestures, and the read-only key hooks
route/     turning an action spec into behaviour
store/     persistence, with the key-event hot path precomputed
ui/        the configuration screens, built in code

tools/     adb helpers: nav-mode (bar and gestures), ai-key (the AI key hook)
tools/e2e/ tests that run on a device, through the kernel input nodes
play/      everything for the Play Console: checklist, listing, policy, graphics
src/debug/ a debug-only hook that renders each screen to a PNG, because the
           e-ink panel defeats `adb shell screencap`
```
