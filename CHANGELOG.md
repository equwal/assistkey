# Changelog

## 0.0.7-alpha - 2026-09-21

Version code 7.

- **Fix: Extra dim toggle.** On now goes to the lowest level (1). Off gives the
  light back to the brightness set in the system. Before, on used the level
  that was last in use, or a middle level. Regression tests added.
- **Emergency SOS switch.** Android starts Emergency SOS on five quick Power
  presses, which collides with a 5-tap binding. The Power screen now has a
  switch for it (`emergency_gesture_enabled`). The app never turns it off by
  itself. The switch shows only where the app can write the setting.
- The three light actions are now named Extra dim toggle, Extra dim darker and
  Extra dim brighter. A binding made by 0.0.6 shows the new name with no change.

## 0.0.6-alpha - 2026-09-21

Version code 6.

- **Voice typing prefers speech recognition on the device.** When the user has
  not chosen a speech app, an on-device one (Whisper, FUTO Voice Input, Vosk,
  Sayboard, Sherpa) wins over one that may use a server. The Voice typing screen
  says which kind each app is.
- **A route to local Whisper.** If no on-device speech app is installed, the
  screen offers Whisper from F-Droid (`org.woheller69.whisper`): offline after
  the model download, many languages, automatic language detection.
- Language is "Detect the language" by default. A fixed language tag stays
  possible.
- **Menu of actions.** A gesture can open a menu that holds any number of
  actions, so there can be more actions than keys. The menu lives in its own
  binding, so export, import and delete treat it like any other binding.
- **Export and import.** Settings as one JSON file: save, share, open, paste.
  The licence, saved firmware values and device facts never enter the file, and
  a file cannot write them.
- **Extra-dim from a key.** Three actions under Light: darker, brighter, on and
  off. `full` build only.
- First unit tests, 20 in all: the speech app choice rule, the settings file
  round trip (500 random cases) and its allow-list, the light level steps, the
  menu round trip. Run them with `./gradlew testPlayReleaseUnitTest`.

## 0.0.5-alpha - 2026-09-21

Version code 5.

- **Recent apps as cards.** The list is now a row of cards, like the Android
  switcher: swipe sideways, tap a card to open it, swipe a card up to close it.
  It is drawn for e-ink: outlines only, and the row jumps from card to card with
  no animation. With shell access a card is a real task and can be closed
  (`am stack remove`). Where the shell is root, a card shows the picture the
  system keeps of the task. Otherwise a card shows the app icon.

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
