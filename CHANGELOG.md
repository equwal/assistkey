# Changelog

## 0.1.1-beta - 2026-09-21

Version code 17. The source code is now public under GPL-3.0-or-later.

- **F-Droid.** A new build, the `fdroid` flavour, is free and complete. It has
  no licence check, no Google Play Billing and no apps of other makers inside.
  Its permissions are those of the `full` build, less the permission to
  install apps. F-Droid builds each new `v*` tag by itself; see `fdroid/`.
- The `play` and `full` builds do not change.

## 0.1.0-beta - 2026-09-21

Version code 16. The first beta. The alpha versions end here.

- **Updates: Ink Update.** Rebind has no internet permission, so it cannot
  look for a new version. Ink Update, an open source app of its own, does
  that: F-Droid first, then Google Play (it leaves an app that Google Play
  installed to Google Play), then GitHub. It makes a notification and opens
  the page. Setup has an "Updates" row that installs or opens it. The build
  for direct install carries it.
- The build for direct install carries Ink Dim too. Hardware hacks lists it.
- The lists of carried apps are named "Extensions".

- **Settings are safe across versions and across an uninstall.** An update
  always kept the settings: they are in the data of the app. An uninstall
  removed them. Now Android asks at uninstall whether to keep the data
  (`hasFragileUserData`), and Rebind keeps an automatic copy of the settings in
  `Documents/Rebind/rebind-settings.json`, which an uninstall does not touch.
  Import opens that folder. `StoredFormatTest` holds the texts that every
  0.0.x version stored, so a new version cannot stop reading them unseen.

## 0.0.15-alpha - 2026-09-21

Version code 15.

- **The Recents button opens Ink Recents.** Android does not let an app take
  the place of the recent-apps screen of the system. Rebind now sees that
  screen open (the task manager of the Viwoods launcher, or the stock Android
  one), closes it, and opens Ink Recents. It works for the Recents button of
  the bar, for the swipe, and for the Recents action. Home and recents has the
  switch "Recents button opens Ink Recents". It is on when Ink Recents is
  installed. Tests: `SystemRecentsTest`.

## 0.0.14-alpha - 2026-09-21

Version code 14.

- **Permissions covers the extensions again.** New items, each only where it
  can apply: "Install apps from Rebind" (the build that carries apps), "Ink
  Recents: app usage data" (when Ink Recents is installed), and "Home screen"
  to choose inkOS or ThinkLauncher (when one is installed). "Set up what is
  missing" walks through them too. The main screen still counts only the
  grants of Rebind itself.
- **Fix: voice typing said "The microphone is not allowed" although it was
  allowed.** Android gives the microphone to an app only while that app is in
  use. The Viwoods firmware does not count a speech app as in use while it
  listens in the background for another app, so Whisper was refused (error
  9). Rebind now goes to the speech screen of the speech app, where that app
  is in use, takes the words from its result, and remembers the route.
  Regression test: `DictationRouteTest`.
- **Fix: "Let Rebind get the Power hold" opened nothing.** Android has no
  request dialog for the assistant role. The row now opens the settings page
  "Default digital assistant app".

## 0.0.13-alpha - 2026-09-21

Version code 13.

- **Home screens: inkOS and ThinkLauncher in place of CLauncher.** The build
  for direct install carries inkOS v0.6 and ThinkLauncher v3.0, both made for
  e-ink, both GPL-3.0, each the release file of its maker and not changed.
  ThinkLauncher has the internet permission of its own. Rebind still has
  none.

- **Change a button from the Done step.** "Change what it does" and "Change
  how you press it" go back to those steps with the button kept. Back on the
  Done step goes to the actions too. Tests: `Route.back`.
- **Show or hide the button bar** is an action (build for direct install).
  Android 16 has no auto-hide for the three-button bar, so a button does it.
  It uses the shell commands of the Navigation screen and keeps the gestures
  as they are. The allow step asks for shell access for this action and for
  the light actions.
- **AI voice prompt** is an action of its own (Viwoods). It is what the
  firmware does on a hold of the AI key: the Viwoods AI screen, told to start
  its voice prompt. It needs the Viwoods AI account, as the stock hold does.
  A component launch can now carry text extras (`pkg/class?key=value`).
  Tests: `ComponentPayloadTest`.
- **Ask when the user tries it.** The first run asks for every grant once.
  After that the app asks at the moment something is missing: after a menu is
  saved, after the Power switch on the Navigation screen, when a Power press
  arrives while button remapping is off, and when voice typing starts with no
  microphone grant. Before, those places showed a short message or nothing.
- **Fix: system Back always closed the button setup.** From Android 13 the
  system does not call `onBackPressed` for an app with this target version.
  The setup now registers the Back callback, and Back goes one step back.
  Checked on the reader: Done, actions, how to press, main screen.

## 0.0.12-alpha - 2026-09-21

Version code 12.

- **Apps of other makers.** The build for direct install carries two apps and
  can install them: CLauncher v5.3.0 (the release APK of its maker, not
  changed) and Ink Recents 0.1.1 (open source). Home and recents lists them. Android asks before
  each install. The APKs are in the build, because Rebind has no internet
  permission. The Google Play build carries nothing and has no install
  permission: Google Play does not allow that here. It opens the page of the
  maker.
- Whisper 3.7 (speech to text on the device, MIT, the F-Droid build) is the
  third app that the build for direct install carries. Voice typing lists it.
- **The home screen of Rebind is gone**, with its settings screens. CLauncher
  is the home screen now, as its maker released it. An old settings file that
  has home screen settings still imports. Those settings are skipped.
- **Recent apps is a program of its own: Ink Recents** (open source,
  github.com/equwal/ink-recents). Rebind no longer has that screen, and no
  longer asks for usage access or `KILL_BACKGROUND_PROCESSES`. The Recent apps
  action opens Ink Recents. Old bindings keep working. Where Ink Recents is
  not installed, the action opens the screen that offers it.
- Hardware hacks: the double tap of Power is one row. Its way in is the camera
  intent or the wallet intent, as the device decides. There is no wallet
  button.
- The setup of a Power combination no longer says the same thing twice.

- **Hardware hacks** is a tile on the main screen with a screen of its own:
  hold Power (assistant role), double tap Power (camera intent), the wallet
  button, the full Power button (shell access), extra-dim light, and the
  button settings of the device. Advanced no longer holds them.

## 0.0.11-alpha - 2026-09-21

Version code 11.

- **Fix: a Camera key in the drawing of a device that has none.** The Viwoods
  reader declares a camera key to Android and has no such button. Detection
  put the declared key in the drawing. The drawing now shows only the buttons
  that the device profile knows, and a button that was really pressed in
  normal use. The Detect screen still lists what the device declares, and
  marks a key that was never seen. Regression test: `DeviceKeysTest`.
  The double tap of Power uses the camera *intent* of Android. It needs no
  camera button.

## 0.0.10-alpha - 2026-09-21

Version code 10.

- **No Google Play, no lock.** On a device where the Google Play app is not
  installed or is turned off, the app is free and complete. There is no way
  to buy there, so there is nothing to lock. The licence screen says "Free on
  this device". A bought licence still comes first. Tests: `LicenseDecideTest`.
- The build for direct install has a tip link on the Licence screen
  (ko-fi.com/truex). A tip unlocks nothing. The Google Play build has no such
  link, because Google Play does not allow it.
- The reasons in the allow step are hints of three to seven words. The app
  says "double tap" for Power too.

## 0.0.9-alpha - 2026-09-21

Version code 9.

- **New name: Rebind.** The name says what the app does, and it has the words
  that buyers type in Play search. The package id stays `dev.equwal.assistkey`,
  so an update keeps all settings. The settings file keeps its `AssistKey`
  marker, so old exports still import.
- **Recent apps.** Swipe up closes the app. Swipe down closes all the others.
  One swipe sideways moves one card in one step, with no glide. When the screen
  opens, the card jumps up and sideways once to show the swipes. "Close all but
  this app" and "Close all" are two tall buttons with space between them.
  Closing works without shell access too: Android ends the background
  processes of the app (`KILL_BACKGROUND_PROCESSES`, a normal permission).
- **Hardware hacks** have their own box under Advanced: hold Power, double
  press Power, the wallet button, the full Power button, extra-dim light, and
  the button settings of the device.
- **Ask when it is needed, everywhere.** A binding made under Advanced now opens
  the same "allow" step as the guided setup, when something is missing.
- **The drawing is the main screen.** The device and its buttons are on the
  first screen. Each button shows what it does now. Tap a button to set it up.
  Tap two buttons to set up a combination.
- **Button order.** A device profile gives the order of its buttons from the
  top down. Viwoods AiPaper: Power, Volume up, Volume down, AI key.
- **On-screen button.** A new button that needs no hardware: a small round
  button that floats over every app. Tap it to do the action, drag it to move
  it. The key filter draws it, only while it has an action. Android's own
  accessibility button is not used, because Android shows that button to every
  user as soon as a service asks for it.
- **More ways to press.** Step two lists tap, double tap, triple tap, hold, 4
  taps and 5 taps, and an Advanced row with every gesture of the button.
- **More actions.** Step three adds Lock screen and Screenshot, and an Advanced
  row with every action.
- **Ask when it is needed.** A Power press that Android hides from apps is now
  offered where the build can have shell access. The app then asks for shell
  access, which opens the full Power button. After a double press of Power is
  set up, the app offers the wallet way for devices that use it.
- **Device detection.** At first start the app finds which buttons the device
  has, and what the device can do. It never asks the user to press a button.
  Advanced > Detect this device shows the result. The device report has it too.
- **Less text.** All "More about this" rows are gone. Notes are one short
  sentence. The accessibility disclosure is short. Google Play requires it, so
  it stays.
- Tests: selection rules of the drawing, the on-screen button route, shell
  access as a need, detection parser and round trip.

## 0.0.8-alpha - 2026-09-21

Version code 8.

- **Set up a button.** The main screen starts with one large button. The user
  taps a button in a drawing of the device, chooses tap, double tap or hold,
  and chooses what the button does. The app then works out what that needs
  (key filter, assistant role, camera role, microphone) and asks only for
  that, by what it does. The app never asks the user to press a key to find
  it. A button that is not in the drawing is chosen from a list of names.
- **New main screen.** A top bar with the licence state, "Your buttons" with
  the bindings in use, and five tiles: Navigation, Voice typing, Home and
  recents, Setup, Advanced. It fits on one screen with no scroll.
- **Advanced > Full control.** The screens from before (every key and gesture,
  the Power button, two-key combinations) are all still there. Guided setup
  and full control edit the same bindings.
- All screens use one look: title bar with a back arrow, state chips, one
  filled main button for each screen, less text, details behind "More".
- Tests: `RouteTest` (what a wish needs, which gestures a button can have) and
  `SummaryTest` (the one-line states on the main screen).

## 0.0.7-alpha - 2026-09-21

Version code 7.

- **Fix: Extra dim toggle.** On now goes to the lowest level (1). Off gives the
  light back to the brightness set in the system. Before, on used the level
  that was last in use, or a middle level. Regression tests added.
- **Emergency SOS switch.** Android starts Emergency SOS on five quick Power
  presses, which collides with a 5-tap binding. The Power screen now has a
  switch for it (`emergency_gesture_enabled`). The app never turns it off by
  itself. The switch shows only where the app can write the setting.
- **Permissions screen.** Every grant the app can use, with what it is for and
  whether it is granted. "Set up what is missing" opens the grant screens one
  after the other. It opens once by itself on the first run and can be run
  again at any time from Setup > Permissions.
- The accessibility disclosure is one shared text now, and it names voice
  typing and the app-in-front note, which the old text did not.
- Recent apps: each card says "Swipe up to close", and there is "Close all but
  <app>" beside "Close all".
- Store listing: title is now "AssistKey: E-Ink Key Remap", with no maker name
  in the title and a not-affiliated line in the description.
- Shell access and the extra-dim light are under Advanced now, and are not
  counted as missing permissions. They are for the few devices with Shizuku or
  root; the app is complete without them.
- **Ready-made menus.** The action list offers menus that can be bound in one
  tap: Brightness (`full` build), Navigation, System, Sound and media, Page
  turning, and Viwoods AI on Viwoods devices. Each is an ordinary menu after it
  is bound, and the menu editor can change it.
- **Brightness up and down.** Two more light actions, for the system
  brightness. A step is a quarter of the present value and at least 5.
- **Recent apps: easier to read, nicer to scroll.** One swipe moves one card, in
  the direction of the swipe. A row of icons under the cards shows every recent
  app at once and marks the one in the middle; a tap on an icon goes to its
  card. The heading says which app and "2 of 12".
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
