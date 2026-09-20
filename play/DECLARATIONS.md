# App content declarations

Answers for Play Console > Policy and programs > App content. Each is true of
the build tagged `v0.0.1-alpha`; re-check them if the app changes.

## Privacy policy

Required, because the app uses the accessibility API. Host
[PRIVACY.md](PRIVACY.md) at a public URL and paste that URL.

## Ads

**No**, the app does not contain ads.

## App access

**All functionality is available without special access.** No login.

## Content rating

Category: **Utility, Productivity, Communication, or other**. Answer **No** to
every question about violence, sexuality, language, controlled substances,
gambling, user interaction and sharing location. Expected result: Everyone /
PEGI 3.

## Target audience

**18 and over.** The app is not designed for children. Choosing adult ages only
keeps it out of the Families policy, which it has no reason to be under.

## News app / Health / Financial features / Government

**No** to all. (The "wallet" channel makes AssistKey selectable as the wallet
app so that a button press reaches it. It holds no cards and moves no money,
so it is not a financial feature.)

## Data safety

| Question | Answer |
|---|---|
| Does your app collect or share any of the required user data types? | **No** |
| Is all of the user data collected by your app encrypted in transit? | Not applicable - nothing is collected |
| Do you provide a way for users to request that their data is deleted? | Not applicable |

Why "No" is accurate: the app holds no `INTERNET` permission
(`aapt2 dump badging` on the release shows only `WRITE_SECURE_SETTINGS`,
`QUICK_ACCESS_WALLET` and `com.android.vending.BILLING`). Data that never
leaves the device is not "collected" in Play's sense. Purchase handling by
Google Play's own billing system does not have to be declared by the app.

## Accessibility API declaration

Play asks this of any app whose manifest declares an accessibility service.

**Is your app an accessibility tool (built to support people with
disabilities)?** No. The manifest does not set `isAccessibilityTool`.

**What core functionality uses the AccessibilityService API?**

```
AssistKey is a hardware key remapper for the Viwoods AiPaper Reader, an e-ink reading device. Remapping hardware keys is the app's only function, and the AccessibilityService API is the only public Android API that can do it.

The service is used for two things:

1. Key event filtering (flagRequestFilterKeyEvents / onKeyEvent). The service receives presses of the device's AI key and volume keys, recognises the gesture the user configured - single or multiple taps, press-and-hold, or a combination of keys - and consumes the press so that the user's chosen action runs instead of the default one.

2. Performing the action the user bound to that gesture: performGlobalAction (Back, Home, Recents, notifications, quick settings, lock screen, screenshot), dispatchGesture (a swipe, used to turn pages in reading apps that accept only touch input), and ACTION_SCROLL_FORWARD / ACTION_SCROLL_BACKWARD on the scrollable node of the active window.

Window content is retrieved only in case 2, only to locate a scrollable node, and only at the moment the user presses a key bound to the Scroll action. No window content, text, or key event is stored, logged or transmitted. The app does not request the INTERNET permission and cannot transmit anything.

Before the user is sent to the accessibility settings, the app shows a prominent in-app disclosure describing exactly this use, and proceeds only if the user taps Agree.
```

**Disclosure evidence.** If the form asks for a video: on the main screen tick
*Accessibility key filter*. The disclosure dialog appears before anything else,
and only *Agree* continues to Android's settings. Record that on any phone
pointed at the reader - the reader cannot capture its own screen.

## Permissions a reviewer may ask about

| Permission | Why |
|---|---|
| `BIND_ACCESSIBILITY_SERVICE` | Above. |
| `WRITE_SECURE_SETTINGS` | Cannot be granted to a Play install; it does nothing unless the owner grants it over adb. It then lets the app switch the firmware's power-button behaviour (short press, hold duration). Declared so that the grant is possible at all. |
| `QUICK_ACCESS_WALLET` | Required of any app offered as the wallet app. AssistKey serves an empty card list; it exists so a press of the wallet shortcut reaches the user's chosen action. |
| `com.android.vending.BILLING` | Play Billing. |

The assistant, camera and wallet entry points are disabled in the manifest and
are enabled one by one only when the user ticks the matching channel, so an
untouched install never appears as a candidate for any of those roles.
