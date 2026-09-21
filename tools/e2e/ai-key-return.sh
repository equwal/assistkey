#!/usr/bin/env bash
# Test on a Viwoods reader: inside the AI screen or the crop screen, the AI key
# goes back to the app that was in use before.
#
# Needs: adb root, release build installed, key remapping on. Presses go through
# the kernel input node, because `adb shell input keyevent` skips the input
# filter stage.
set -u
export MSYS_NO_PATHCONV=1
NODE=${AI_NODE:-/dev/input/event5}
E="sendevent $NODE"; AI="$E 1 59 1; $E 0 0 0; sleep 0.08; $E 1 59 0; $E 0 0 0"
top() {
  local t="" i
  for i in 1 2 3 4 5; do
    t=$(adb shell "dumpsys activity activities" | grep -m1 topResumedActivity | grep -oE '[A-Za-z0-9_.]+/[A-Za-z0-9_.$]+' | head -1)
    [ -n "$t" ] && break; sleep 1
  done; echo "${t%%/*}"; }
fail=0
check() { if [ "$2" = "$3" ]; then echo "PASS  $1: $2"; else echo "FAIL  $1: got '$2', want '$3'"; fail=1; fi; }

adb shell input keyevent KEYCODE_WAKEUP; sleep 2
adb shell "am start -a android.settings.SETTINGS" >/dev/null 2>&1; sleep 3
check "start in a normal app" "$(top)" "com.android.settings"

adb shell "am start -n com.viwoods.viwoodsai/com.wisky.wiskyai.WebViewAiActivity" >/dev/null 2>&1; sleep 3
check "AI screen is in front" "$(top)" "com.viwoods.viwoodsai"
adb shell "su 0 sh -c '$AI'"; sleep 3
check "AI key in the AI screen returns to the app" "$(top)" "com.android.settings"

adb shell "su 0 am start -n com.viwoods.launcher/com.viwoods.libfloating.activity.ScreenCaptureActivity" >/dev/null 2>&1; sleep 4
check "crop screen is in front" "$(top)" "com.viwoods.launcher"
adb shell "su 0 sh -c '$AI'"; sleep 3
check "AI key in the crop screen returns to the app" "$(top)" "com.android.settings"

adb shell input keyevent KEYCODE_HOME
exit $fail
