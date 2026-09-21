# App content declarations

Answers for Play Console > Policy and programs > App content. Each is true of
the build tagged `v0.0.4-alpha`; re-check them if the app changes.

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
(`aapt2 dump badging` on the release shows `WRITE_SECURE_SETTINGS`,
`PACKAGE_USAGE_STATS`, `RECORD_AUDIO`, `QUICK_ACCESS_WALLET` and
`com.android.vending.BILLING`, none of which is network access). These answers
are for the `play` build, which is the only one uploaded. The device report is
composed on the device and leaves it only if the user sends it, through their
own email or share target. Data that never
leaves the device is not "collected" in Play's sense. Purchase handling by
Google Play's own billing system does not have to be declared by the app.

## Accessibility API declaration

Play asks this of any app whose manifest declares an accessibility service.

**Is your app an accessibility tool (built to support people with
disabilities)?** No. The manifest does not set `isAccessibilityTool`.

**What core functionality uses the AccessibilityService API?**

```
AssistKey is a hardware key remapper for the Viwoods AiPaper Reader, an e-ink reading device. Remapping hardware keys is the app's only function, and the AccessibilityService API is the only public Android API that can do it.

The service is used for four things:

1. Key event filtering (flagRequestFilterKeyEvents / onKeyEvent). The service receives presses of the device's AI key and volume keys, recognises the gesture the user configured - single or multiple taps, press-and-hold, or a combination of keys - and consumes the press so that the user's chosen action runs instead of the default one.

2. Performing the action the user bound to that gesture: performGlobalAction (Back, Home, Recents, notifications, quick settings, lock screen, screenshot), dispatchGesture (a swipe, used to turn pages in reading apps that accept only touch input), and ACTION_SCROLL_FORWARD / ACTION_SCROLL_BACKWARD on the scrollable node of the active window.

3. Voice typing, if the user binds that action: the service finds the text field that has input focus and inserts the recognised words at the cursor (ACTION_SET_TEXT, or ACTION_PASTE as the fallback). It never writes into password fields.

4. Going back to the app that was in use, on Viwoods readers: the service reads the package name of the window in front from window-state events, keeps the latest one in memory only, and uses it to return from the maker's AI screen when the user presses the AI key there.

Window content is retrieved only in cases 2 and 3: to locate a scrollable node at the moment the user presses a key bound to the Scroll action, and to read the one focused text field at the moment recognised words are inserted into it. No window content, text, or key event is stored, logged or transmitted. The app does not request the INTERNET permission and cannot transmit anything.

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
| `RECORD_AUDIO` | Voice typing only. Requested at run time from the Voice typing screen. The speech recognition app the user chose records the audio; Android requires the calling app to hold the permission too. AssistKey receives text only, and stores and sends nothing. |
| `PACKAGE_USAGE_STATS` | Special access the user grants from a settings screen. Used only by the recent-apps list to order apps by last use, on the device; nothing is stored or sent. Not needed when shell access is on. |
| `QUICK_ACCESS_WALLET` | Required of any app offered as the wallet app. AssistKey serves an empty card list; it exists so a press of the wallet shortcut reaches the user's chosen action. |
| `com.android.vending.BILLING` | Play Billing. |

The assistant, camera and wallet entry points are disabled in the manifest and
are enabled one by one only when the user ticks the matching channel, so an
untouched install never appears as a candidate for any of those roles.
