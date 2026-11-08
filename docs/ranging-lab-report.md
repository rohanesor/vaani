# Vokie L4-R0 Universal Ranging Capability & Measurement Lab

## Scope

This is a capability/measurement inventory only. No distance algorithm, direction estimator, GPS, coordinates, maps, cloud service, RSSI-to-metres conversion, or RTT-to-metres conversion was implemented. The L1 classifier is unchanged.

## Devices

| Device | ADB serial | Inventory |
|---|---|---|
| Phone A / V2205 | `10BCAC2HM5000CR` | **PASS** — physical debug inventory collected |
| Phone B / RMX3782 | `MJPVXCSG9HYL65YL` | **PASS** — physical debug inventory collected |

## Phone B capability inventory

Collected by `RangingCapabilityManager` on Android 15:

```json
{
  "sdk": 35,
  "manufacturer": "realme",
  "model": "RMX3782",
  "abi": "arm64-v8a",
  "chipset": "mt6835",
  "wifi_supported": true,
  "wifi_enabled": true,
  "wifi_rtt_hardware": false,
  "wifi_rtt_manager": false,
  "wifi_aware_hardware": false,
  "wifi_aware_manager": false,
  "wifi_direct_supported": true,
  "bluetooth_supported": true,
  "bluetooth_enabled": false,
  "ble_supported": true,
  "ble_scan_permission": true,
  "ble_advertise_permission": true,
  "sensors": {
    "accelerometer": true,
    "gyroscope": true,
    "magnetometer": true,
    "rotation_vector": true,
    "game_rotation_vector": true,
    "gravity": true,
    "linear_acceleration": true
  }
}
```

## Measurement status

### BLE

Both phones report BLE support and the required scan/advertise permissions. Phone B advertised a diagnostic service and Phone A completed two 30-sample scans at the same physical position. The scanner observed Phone B as `FC:2A:46:06:40:FE`.

```text
Run 1: n=30, RSSI=-46..-43 dBm, mean=-45.5, median=-46, stddev=1.118 dB
Run 2: n=30, RSSI=-46..-43 dBm, mean=-45.3, median=-46, stddev=1.269 dB
```

The raw sample stream and summaries are in `model-lab/ranging/ble_measurements.json`. RSSI was not converted to metres.

BLE support and permissions do not prove that a peer advertisement/scanner experiment has been completed. No RSSI value is fabricated.

### Wi-Fi RTT

```text
Phone B hardware/API: UNSUPPORTED / UNAVAILABLE
Phone A: NOT TESTED
Peer ranging: NOT TESTED
```

Phone B exposes neither the `android.hardware.wifi.rtt` feature nor a `WifiRttManager` service. No RTT request was made. Wi-Fi Direct remains independently supported.

### Wi-Fi Aware

Phone B exposes neither Wi-Fi Aware hardware nor a `WifiAwareManager` service. Status: unavailable on the tested device.

### Wi-Fi Direct communication metrics

The existing L1 baseline remains the only physical communication sample set:

```text
A → B ACK RTT: 35 ms, acknowledged, NEAR
B → A ACK RTT: 102 ms, acknowledged, NEAR
```

These are communication metrics, not physical-distance measurements. A new L2-style exchange was not performed because Phone A disconnected during the lab deployment.

### Sensors

Both phones report accelerometer, gyroscope, magnetometer, rotation vector, game rotation vector, gravity, and linear acceleration. No synchronized raw sensor stream was collected; sensor status is `SUPPORTED`, not `MEASURED`.

## Physical separation experiment

```text
Completed: NO
Positions tested: one unlabeled same-position setup only
BLE samples by position: 60 total (two 30-sample runs)
RTT samples by position: 0
```

Both phones were physically available for the BLE test, but the operator-labeled P0/P1/P2/P3/P4 separation sequence was not performed. The two same-position runs were nearly identical, with a 0.2 dB difference in mean RSSI; this demonstrates repeatability for that setup, not separation sensitivity. No physical-distance conclusion is supported.

## Recommended next research direction

Reconnect Phone A and run a dedicated BLE advertiser/scanner experiment with at least 30 raw RSSI observations at operator-labeled positions. Separately determine whether either device can expose peer-specific RTT through a supported Wi-Fi infrastructure/responder path. Keep ACK RTT and delivery metrics as communication metadata only. Do not design a ranging classifier until position-labeled variance is measured.

## Status

```text
L4-R0 STATUS: NOT PASS
Capability detection: PASS for both phones
Two-phone capability inventory: PASS
BLE measurement: PASS, 60 raw samples at one unlabeled position
Wi-Fi RTT: UNSUPPORTED/UNAVAILABLE on both phones
Wi-Fi Direct metrics: prior baseline only
Sensor availability: PASS for both; raw synchronized data NOT MEASURED
Physical separation experiment: NOT COMPLETED
Useful separation signal: INCONCLUSIVE
Distance claim: NONE
Direction claim: NONE
```