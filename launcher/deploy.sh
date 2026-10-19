#!/usr/bin/env bash
# =============================================================================
# deploy.sh — build, install and re-arm the launcher on the TV in one shot
#
# Reinstalls revoke runtime permissions on some MIUI firmwares (seen: weather
# and the Wi-Fi name silently dying after adb install -r), so every deploy
# re-grants them.
#
# Usage:    ./deploy.sh [tv-ip]        (e.g. ./deploy.sh 192.168.68.106)
# Env:      TV_IP   (default 192.168.68.106:5555)
#           ADB     (default ~/platform-tools/adb)
#           JAVA_HOME is passed through to gradlew
# =============================================================================
set -euo pipefail

TV_IP="${1:-${TV_IP:-192.168.68.106:5555}}"
ADB="${ADB:-$HOME/platform-tools/adb}"
APK=app/build/outputs/apk/release/app-release.apk

./gradlew assembleRelease
"$ADB" connect "$TV_IP"
"$ADB" -s "$TV_IP" install -r "$APK"
# Weather + Wi-Fi name; MIUI revokes these on reinstall
"$ADB" -s "$TV_IP" shell pm grant me.prince.launcher android.permission.ACCESS_COARSE_LOCATION
# Recent row
"$ADB" -s "$TV_IP" shell appops set me.prince.launcher GET_USAGE_STATS allow
# Faster cold start
"$ADB" -s "$TV_IP" shell cmd package compile -m speed -f me.prince.launcher

echo "Deployed to $TV_IP. Press Home on the TV if the launcher isn't in front."
