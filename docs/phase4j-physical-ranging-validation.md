# Phase 4J — physical peer-ranging capability validation

## Test boundary

This is a device-only validation milestone. No production Android code, APK, PacketV2, Bluetooth transport, Wi-Fi Direct transport, GPS algorithm, STT, translation, or TTS code was modified.

## ADB gate

Required devices:

| Device | Required identifier | Required status | Observed status |
|---|---|---|---|
| Device A | RMX3782 / `MJPVXCSG9HYL65YL` | connected through ADB | **NOT TESTED — absent** |
| Device B | vivo V2205 / `10BCAC2HM5000CR` | connected through ADB | **NOT TESTED — absent** |

Observed command result:

```text
adb devices -l
List of devices attached
```

The Phase 4J instruction requires stopping when either phone is unavailable. Both were unavailable, so no capability command, framework query, radio measurement, Internet-off test, or app regression test was run.

## Results

| Area | Device A | Device B | Classification |
|---|---|---|---|
| Model/manufacturer/API/ABI baseline | NOT TESTED | NOT TESTED | NOT TESTED |
| Wi-Fi RTT framework capability | NOT TESTED | NOT TESTED | UNKNOWN |
| Wi-Fi Aware framework capability | NOT TESTED | NOT TESTED | UNKNOWN |
| Phone-to-phone Wi-Fi Aware RTT | NOT TESTED | NOT TESTED | NOT MEASURED |
| UWB feature | NOT TESTED | NOT TESTED | UNKNOWN |
| UWB ranging | NOT TESTED | NOT TESTED | NOT POSSIBLE TO ASSESS |
| Bluetooth Channel Sounding API/controller support | NOT TESTED | NOT TESTED | UNKNOWN |
| Bluetooth RSSI samples | NOT TESTED | NOT TESTED | NOT MEASURED |
| GPS close-range baseline | NOT TESTED | NOT TESTED | NOT MEASURED |
| Heading/cardinal direction | NOT TESTED | NOT TESTED | NOT MEASURED |
| Movement toward/away | NOT TESTED | NOT TESTED | NOT MEASURED |
| Offline behavior | NOT TESTED | NOT TESTED | NOT MEASURED |
| Bluetooth text / PacketV2 / ACK regression | NOT TESTED | NOT TESTED | NOT MEASURED |
| Wi-Fi Direct text / PacketV2 / ACK regression | NOT TESTED | NOT TESTED | NOT MEASURED |
| STT, location packet, Locate, Emergency Mode regression | NOT TESTED | NOT TESTED | NOT MEASURED |

## Device capability matrix

| Capability | Device A | Device B |
|---|---|---|
| GPS | UNKNOWN in this session |
| Wi-Fi RTT | UNKNOWN |
| Wi-Fi Aware | UNKNOWN |
| UWB | UNKNOWN |
| Bluetooth Channel Sounding | UNKNOWN |
| Bluetooth RSSI | UNKNOWN |

```text
PHONE-TO-PHONE RTT POSSIBLE: UNKNOWN
PRECISION RANGING POSSIBLE: UNKNOWN
```

## Current recommendation

No physical evidence was collected in this milestone, so the prior evidence-bounded recommendation remains unchanged: do not claim a precision ranging method. If/when the devices reconnect, first query framework features and only attempt peer RTT/UWB when both peers explicitly report the required capability. RSSI remains qualitative only and must not be converted to metres.

## Known limitation

A laptop cannot substitute for phone Wi-Fi/Bluetooth/UWB radios. The required physical test cannot be continued until both specified ADB devices are connected.
