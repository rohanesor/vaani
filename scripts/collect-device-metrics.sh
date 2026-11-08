#!/usr/bin/env bash
# Capture a timestamped, non-invasive Android performance snapshot for SIH validation.
# Usage: ADB=/path/to/adb scripts/collect-device-metrics.sh [label]
set -Eeuo pipefail
adb_bin=${ADB:-adb}
label=${1:-snapshot}
out="device-metrics/${label}-$(date -u +%Y%m%dT%H%M%SZ).txt"
mkdir -p "$(dirname "$out")"
"$adb_bin" get-state | grep -qx device || { echo 'No authorized Android device.' >&2; exit 1; }
{
  echo "label=$label"
  date -u +%FT%TZ
  "$adb_bin" shell getprop ro.product.model
  "$adb_bin" shell getprop ro.build.version.sdk
  echo '--- meminfo com.vokie ---'
  "$adb_bin" shell dumpsys meminfo com.vokie
  echo '--- process CPU snapshot ---'
  "$adb_bin" shell top -b -n 1 -o PID,CPU,RES,ARGS | grep com.vokie || true
  echo '--- iTantra STT/TTS/Bluetooth logs ---'
  "$adb_bin" logcat -d -v threadtime -s 'VOKIE][STT:D' 'VOKIE][TTS:D' 'VOKIE][BT:D' '*:S' || true
} | tee "$out"
echo "Saved $out"
