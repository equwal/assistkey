# Changelog

## 0.0.4-alpha - 2026-09-20

Version code 4.

- **Voice typing.** A new action. Press the key, speak, and the words go where
  the cursor is. The app has no speech engine: it uses the Android
  SpeechRecognizer interface, so any speech recognition app on the device does
  the listening. The accessibility service puts the text into the focused
  field. Password fields are never written to.
- **Two builds.** `play` for Google Play has no Shizuku code and no Shizuku
  permission. `full` for direct install keeps shell access. Same app id and
  signature, so one can replace the other.
- **Viwoods: AI key goes back.** Inside the AI screen or the crop screen, the AI
  key returns to the app that was in use before. Switch on the AI key screen.
  Test: `tools/e2e/ai-key-return.sh`.
- **Fix: Power always wakes and unlocks.** While the app manages the Power
  button, a press on a sleeping or locked device is never a gesture. The app
  also sends the wake and the keyguard dismissal through the shell, so waking
  does not depend on the firmware. Regression test: `tools/e2e/power-wake.sh`.

Known limits:

- Voice typing needs a speech recognition app that has its own microphone
  permission. The Viwoods voice prompt cannot be used: it sends audio to the
  Viwoods cloud and has no interface for other apps.
- The listening screen pauses the app behind it for a moment. An app that
  clears its text field on resume loses what was typed before.

## 0.0.3-alpha - 2026-09-20

Version code 3.

- **Shell access** through Shizuku, set up on the device over wireless
  debugging. No computer, no root.
- **Power button, properly.** With shell access: tap, double tap, any number
  of taps, hold, and true combinations. Defaults: tap Back, hold Home (closing
  the keyboard first), double tap Recents, triple tap lock. The firmware is
  told to do nothing on Power meanwhile, which is what stops a single tap from
  swallowing a double tap, and gets the button back whenever shell access goes.
- **Navigation applies itself.** Bar, gestures and Power key in any mix, applied
  in-app through the shell; adb commands only as the fallback.
- **One interface.** Assistant, camera and wallet are no longer "channels"
  beside the key filter; they are the Power button's side doors for when there
  is no shell access, and live on the Power screen.
- **Any Android device.** Viwoods is now one device profile. More key types;
  a key appears once the device has produced it.
- **Extra-dim light**, below the system's brightness floor.
- **Home screen** and **recent apps list**, text-only, made for e-ink.
- **Device report**: what a tester can send to get a device supported. Shown in
  full, sent only by the user.
- New action: Home, closing the keyboard first.

Known limits:

- Shizuku started over wireless debugging stops at restart; Power reverts to
  the system until it is started again.
- Extra-dim needs a root-level shell.
- Purchases are still untested.

## 0.0.2-alpha — 2026-09-20

Version code 2.

- **Navigation screen.** Button bar, swipe gestures and Power key combinations
  in any mix, with five named setups. The system half cannot be switched by an
  app, so the screen reports the live state and gives the commands;
  `tools/nav-mode` runs them.
- **Power key combinations.** Hold Power, then press the AI key, Volume up or
  Volume down. Defaults to Home, Back and Recents. These three keep working
  when the app is locked, so a reader with no bar and no gestures always has a
  way out.
- **AI key hook.** Found that the firmware opens its AI screen on every AI key
  press whether or not an app consumed it. `tools/ai-key hook` points the
  firmware at a new entry point instead; taps then work cleanly. The app says
  which state it is in and what that costs.
- Fixed: "ai key" in labels.

Known limits:

- The notification-shade action does nothing on this firmware.
- With the AI key hooked, that key has taps only: no hold, no volume
  combinations.

## 0.0.1-alpha — 2026-09-20

First alpha. Version code 1.

- Remaps the AI key and both volume keys through an accessibility key filter:
  1–5 taps, press-and-hold, and key combinations, each bound separately.
- Remaps Power: hold (as the digital assistant), double press (as the default
  camera app), and the firmware short-press behaviour.
- Actions: Viwoods AI screens, navigation, page-turn swipes and scrolls,
  volume and media, open an app or component, send an intent or broadcast.
- Licensing through Google Play Billing: licensed, beta, 7-day trial, locked.
  The beta is opened and closed from Play Console; testers get a lower price.
- In-app accessibility disclosure with explicit consent.
- Key tester, and a read-only view of the firmware's own key hooks.
- No `INTERNET` permission.

Known limits:

- Purchases are untested: Play Billing cannot be exercised until the app exists
  in Play Console. Everything up to the purchase sheet is tested on the device.
- A volume key whose firmware hook is set is invisible to every app, and only
  adb can unset it.
- The reader ships with Google Play disabled; buying needs it switched on.
