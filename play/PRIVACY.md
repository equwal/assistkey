# AssistKey privacy policy

*Effective 20 September 2026*

AssistKey is published by equwal. Contact: truex@equwal.com

## The short version

AssistKey collects nothing, stores nothing about you, and sends nothing
anywhere. It does not hold Android's internet permission, so it is not able to.

## What the app handles

**Hardware key presses.** With its accessibility service switched on, AssistKey
is told when the AI key or a volume key is pressed or released. It uses this
only to recognise the taps, holds and combinations you have set up and to run
the action you chose. Key presses are handled in memory as they arrive and are
not recorded. The built-in key tester lists recent presses on screen while that
screen is open; the list is never saved and is discarded when you leave it.

**The window in front.** When you use the Scroll action, and only then, the
service asks Android which part of the current window can scroll, so that it
can scroll it. It does not read, record or transmit what the window contains.

**Your settings.** Your key bindings, timing preferences and which capture
channels are on are saved in the app's private storage on your device. They
leave the device only through Android's own backup, if you have it on.

**Voice typing.** If you bind the Voice typing action, AssistKey asks for the
microphone permission. A speech recognition app on your device, which you
choose, does the listening; how it handles audio is covered by that app's own
policy. AssistKey receives only the recognised text, and puts it into the text
field that has the cursor. To do that it reads the current text of that one
field at that moment. It keeps and sends none of it, and never writes into
password fields.

**Recent apps.** If you use the recent-apps list without shell access, it
reads the usage log Android keeps, with the usage access you grant, to put apps
in order of last use. This happens on the device each time the list opens;
nothing from it is stored or sent.

**Shell access.** Not in the Google Play build. In the build for direct
install, if you connect AssistKey to Shizuku, it runs commands on your
device as the shell user to read the Power button, switch the navigation bar
and gestures, and set the light. These act on your device only.

**Device report.** The report you can create under Advanced lists your device
model, firmware, input devices, navigation options and a short list of key and
navigation settings. It does not include your apps, accounts or identifiers.
It is shown to you in full and goes nowhere unless you send it yourself.

**Licence state.** The app saves, on your device, the date it was first
opened, whether it was used during the beta, and which AssistKey products
Google Play reports your account as owning.

## The accessibility service

AssistKey uses Android's AccessibilityService API for one purpose: remapping
hardware keys, and carrying out navigation actions such as Back, Home and
Recents on your behalf. It is not used to collect information, and the app asks
for your agreement, in the app, before directing you to switch the service on.
You can switch it off at any time in Android's accessibility settings.

## Purchases

Purchases are made through Google Play. AssistKey never sees your payment
details. It learns from Google Play only whether your account owns a licence.
Google's handling of the purchase is covered by Google's own privacy policy:
<https://policies.google.com/privacy>.

AssistKey includes Google's Play Billing Library in order to do this. The part
of that library which reports usage statistics to Google has been removed from
AssistKey, and the app has no network access with which it could do so.

## What is not in the app

No advertising. No analytics. No crash reporting. No accounts. No third-party
SDKs other than Google's Play Billing Library.

## Children

AssistKey is a utility for a general audience and is not directed at children.
It collects no personal information from anyone.

## Changes

If this policy changes, the new version will be published at the same address
with a new effective date.
