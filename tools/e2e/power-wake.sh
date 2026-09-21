#!/usr/bin/env bash
# Regression test, on a device: in managed Power mode a Power press on a sleeping
# device wakes it, and is NOT read as a gesture.
#
# Bug it guards: with tap bound to Back, the press that woke the reader could
# also run Back, and a locked or charging reader had no sure way to wake.
#
# Needs: adb root, release build installed, key remapping on, shell access on,
# Power tap bound to Back and triple tap to Lock screen (Navigation > Power key). Presses go through the kernel input
# node, because `adb shell input keyevent` skips the input filter stage.
set -u
export MSYS_NO_PATHCONV=1
NODE=${POWER_NODE:-/dev/input/event1}
E="sendevent $NODE"; TAP="$E 1 116 1; $E 0 0 0; sleep 0.06; $E 1 116 0; $E 0 0 0"
wake()  { adb shell dumpsys power | grep -oE 'mWakefulness=[A-Za-z]+' | head -1 | cut -d= -f2; }
top()   { # dumpsys now and then answers before the activity has settled; ask again.
  local t="" i
  for i in 1 2 3 4 5; do
    t=$(adb shell "dumpsys activity activities" | grep -m1 topResumedActivity | grep -oE '[A-Za-z0-9_.]+/[A-Za-z0-9_.$]+' | head -1)
    [ -n "$t" ] && break; sleep 1
  done; echo "$t"; }
fail=0
check() { if [ "$2" = "$3" ]; then echo "PASS  $1: $2"; else echo "FAIL  $1: got '$2', want '$3'"; fail=1; fi; }

[ "$(adb shell settings get global power_button_short_press | tr -d '\r')" = "0" ] || { echo "SKIP  Power is not managed (power_button_short_press is not 0)"; exit 2; }

adb shell input keyevent KEYCODE_WAKEUP; sleep 2   # let any earlier tap window run out
# Two AssistKey screens deep, so that Back would visibly change the top activity.
# (Not the Settings app: on the Viwoods reader an injected SLEEP key is ignored
# while Settings > Display is in front.)
P=dev.equwal.assistkey
adb shell "am start -n $P/.ui.MainActivity" >/dev/null 2>&1; sleep 2
adb shell "su 0 am start -n $P/.ui.TimingActivity" >/dev/null 2>&1; sleep 2.5
before=$(top)

# Sleep with the triple tap the app binds to Lock screen. An injected SLEEP key
# is not reliable on this firmware.
QUICK="$E 1 116 1; $E 0 0 0; $E 1 116 0; $E 0 0 0;"
adb shell "su 0 sh -c '$QUICK sleep 0.05; $QUICK sleep 0.05; $QUICK'"; sleep 4
check "device sleeps" "$(wake | sed 's/Dozing/Asleep/')" "Asleep"

adb shell "su 0 sh -c '$TAP'"; sleep 3
check "one Power press wakes it" "$(wake)" "Awake"
check "the waking press is not a Back gesture" "$(top)" "$before"

adb shell "su 0 sh -c '$TAP'"; sleep 2.5
[ "$(top)" != "$before" ] && echo "PASS  the next press is a gesture again" || { echo "FAIL  a press while awake did nothing"; fail=1; }
adb shell input keyevent KEYCODE_HOME
exit $fail
