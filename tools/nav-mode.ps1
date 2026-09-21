# PowerShell twin of tools/nav-mode. See that file for what and why.
#   .\tools\nav-mode.ps1 buttons nogestures
#   .\tools\nav-mode.ps1 status
param([string]$Buttons = "", [string]$Gestures = "")

function Show-Status {
    $mode = (adb shell settings get secure navigation_mode).Trim()
    $gest = (adb shell settings get system disable_gesture_bottom).Trim()
    "button bar:       " + $(if ($mode -eq "2") { "hidden" } else { "showing" })
    "swipe-up gesture: " + $(if ($gest -eq "1") { "off" } else { "on" })
}

if ($Buttons -eq "status") { Show-Status; exit 0 }
$cat = switch ($Buttons) { "buttons" { "threebutton" } "nobuttons" { "gestural" } default { $null } }
$off = switch ($Gestures) { "gestures" { 0 } "nogestures" { 1 } default { $null } }
if ($null -eq $cat -or $null -eq $off) { "usage: nav-mode.ps1 buttons|nobuttons gestures|nogestures   (or: status)"; exit 2 }
$scale = if ($off -eq 1) { "0" } else { "0.6" }

adb shell cmd overlay enable-exclusive --category "com.android.internal.systemui.navbar.$cat"
Start-Sleep -Seconds 5
adb shell settings put system disable_gesture_bottom (1 - $off)
Start-Sleep -Seconds 1
adb shell settings put system disable_gesture_bottom $off
adb shell settings put secure back_gesture_inset_scale_left $scale
adb shell settings put secure back_gesture_inset_scale_right $scale
Start-Sleep -Seconds 1
Show-Status
