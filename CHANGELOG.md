# Changelog

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
