# Store listing copy

Paste into Play Console > Store presence > Main store listing.

## App name (30 max)

```
AssistKey: E-Ink Key Remap
```

26 characters. Keep "AssistKey" as the brand and put no device maker's name in
the title. A maker's name at the front of a title ("Viwoods ...") reads as an
official app: Play can suspend it as impersonation, and the maker can remove it
with one trademark complaint. It also tells every Boox owner the app is not for
them, and Boox is the larger market by a wide margin. Maker names belong in the
long description, as plain compatibility facts, with the disclaimer below.

## Short description (80 max)

```
Remap keys, page turn and voice type on E-Ink readers and any Android.
```

## Full description (4000 max)

```
AssistKey remaps your device's hardware keys: volume keys, the power button, page-turn buttons, camera and assistant keys, and on the Viwoods AiPaper the AI key.

Turn pages with the volume keys. Hold the power button for Home, double press it for Recents. Put three different actions on one key with a tap, a double tap and a hold.

Setup is three taps: tap the button in a drawing of your device, choose tap, double tap or hold, choose what it does. AssistKey asks only for what that needs.

Built for e-ink readers, and it works on any Android 12+ phone or tablet.

WHAT EACH KEY CAN DO

AI key and volume keys
• 1 to 5 taps, each bound separately
• Press and hold
• Combinations: hold one key and press another
• A key with only a single-tap binding fires instantly - you only wait out a double-tap window on keys where you asked for a double tap

Power button
• Hold Power, then press another key: three extra combinations
• Press and hold: run any action (AssistKey becomes your digital assistant app)
• Double press: run any action (AssistKey becomes your default camera app)
• Short press: choose the firmware behaviour

ACTIONS

• Viwoods AI: crop, lookup, quick prompt, full assistant, history, repository, edit screenshot
• Navigation: Back, Home, Recents, notifications, quick settings, power menu, lock screen, screenshot
• Page turning: swipes and scrolls, for reading apps that only accept touch
• Sound and media: volume up, down, mute, play/pause, next, previous
• Open any app, or any screen inside an app
• Send an intent or a broadcast, for automation apps
• A menu of actions: one gesture opens a menu with as many actions as you want, so you have more actions than keys
• Ready-made menus for navigation, system, sound and media, and page turning
• Do nothing - disable a key you keep pressing by accident
• Export and import your setup as a file, to move it to another device or share it

VOICE TYPING
Bind a key to Voice typing: press, speak, and the words appear where the cursor is - in any app, with the keyboard you already have. AssistKey has no speech engine and no internet access; a speech recognition app on your device does the listening.

MORE FOR E-INK
• A plain text home screen: a clock, a few apps, type to find the rest
• Recent apps as swipe cards, drawn for e-ink with no animation

BUILT FOR E-INK

Black on white, large text, no animation. No ads. No account.

PRIVATE BY CONSTRUCTION

AssistKey has no internet permission. It cannot send anything anywhere, and you can verify that on the app's permissions page. It collects nothing and stores nothing except your own key bindings, on your device.

ACCESSIBILITY SERVICE

AssistKey uses Android's AccessibilityService API, and only to remap hardware keys. The service receives key presses so it can recognise taps, holds and combinations, and performs the action you chose - Back, Home, a swipe - on your behalf. It looks at the window in front only to find a scrollable area when you use the Scroll action. It does not record what you type or what is on your screen. The app explains this and asks for your agreement before sending you to the accessibility switch.

GOOD TO KNOW

• Made for the Viwoods AiPaper Reader. The volume and power features work on most Android 12+ devices; the AI key and Viwoods actions are specific to Viwoods hardware.
• Android does not let any app see the power button directly, so power gestures are limited to short press, double press and hold.
• Hiding the navigation bar, switching system gestures off, and giving AssistKey sole control of the AI key are one-time steps done from a computer over USB. Android does not allow any app to change them. The app shows the exact commands.
• If a volume key does not respond, the device's own key settings may be holding on to it. The built-in key tester shows exactly which keys AssistKey can see, and the Firmware key hooks screen shows the fix.

COMPATIBILITY
Works on Android 12 and later. Built and tested on the Viwoods AiPaper Reader; key remapping, page turning, voice typing and the home screen work on Boox, Bigme, Meebook and ordinary phones and tablets too. Search terms people use for this: button mapper, key remapper, remap buttons, volume key page turn, double tap, long press, key combination.

AssistKey is not affiliated with, or endorsed by, Viwoods, Onyx Boox, Bigme or Meebook.

PRICE

Free to install and try. A one-time purchase unlocks it permanently - no subscription. Your licence follows your Google account to every device you own.
```

## Category and tags

| Field | Value |
|---|---|
| Category | Tools |
| Tags | Tools, Productivity |
| Contact email | truex@equwal.com |
| Website | (optional) |

## Graphics

| Asset | File | Spec |
|---|---|---|
| App icon | `graphics/icon-512.png` | 512 x 512, 32-bit PNG |
| Feature graphic | `graphics/feature-1024x500.png` | 1024 x 500 |
| Phone screenshots | `graphics/screenshot-*.png` | at least 2; taken on the reader |

The reader's screen is greyscale, so are the screenshots. That is the product,
not a defect - do not colourise them.

## Release notes for 0.0.1-alpha

```
First alpha. Remaps the AI key, volume keys and power button, with multi-tap, hold and key combinations. Everything is free while the beta runs.
```
