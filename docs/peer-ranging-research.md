# Peer-ranging research lab

**Scope:** laptop-only research and capability audit. No Android production, PacketV2, transport, APK, or location code was modified. “Verified” below has a precise meaning:

- **Documentation verified**: documented by the cited platform/specification source.
- **Device capability verified**: observed on a named device through a framework capability check or system evidence.
- **Physical ranging verified**: a supported two-phone ranging session produced a measurement.

Only the first category is established for the platform features in this report. At the time of this report no ADB device was attached, so the required runtime feature checks could not be run. Neither current phone has a physical RTT/UWB/Channel-Sounding measurement claim.

## 1. Executive summary

The observed close physical separation with a GPS display near 14 m is normal smartphone location uncertainty, not evidence that the Haversine calculation is wrong. GPS can retain useful coarse bearing while its horizontal error dominates short ranges.

**Best current universal fallback:** GPS geographic bearing plus explicit uncertainty/freshness, with Bluetooth RSSI only as a qualitative stronger/weaker/stable/unreliable indicator.

**Best precision option:** UWB, where *both* peers expose Android UWB and successfully establish a foreground ranging session.

**Best potential option for these Android versions:** Wi-Fi Aware peer RTT, but only if both phones report both Wi-Fi Aware and Wi-Fi RTT support. This is currently **not available/verified on the named devices**.

**Bluetooth Channel Sounding:** Bluetooth Core 6.0 specifies it, but it is not a practical current-device option for Android 14/15 targets absent a documented Android public API and verified controller support. **Reject for the current prototype; future optional research only.**

## 2. Current 14 m GPS problem and GPS limitations

A phone GNSS/location fix is an estimate with an accuracy radius, affected by sky view, multipath, network-assisted location, device antenna, power policy, and stale fixes. At a 1–3 m physical separation, a displayed 14 m range is therefore plausible, particularly when the combined local and remote accuracy is tens of metres. Direction can still be useful because bearing from two noisy geographic estimates may be qualitatively correct, but it is not compass/survey-grade at very short range. Android Wi-Fi RTT documentation itself describes RTT as typically 1–2 m accurate, in contrast to ordinary location positioning [1].

## 3. Bluetooth RSSI

Bluetooth RSSI is available from some discovery advertisements, but Android has no standard continuous connected-RFCOMM RSSI API. RSSI can support **stronger**, **weaker**, **stable**, and **unstable** only. It must not be converted to metres: orientation, hand/body blocking, walls, multipath, antenna placement, transmit-power differences, channel hopping, and phone model make any universal RSSI-to-distance formula invalid. Bluetooth SIG describes RSSI as received signal strength, not a distance measurement [7].

**Current-device status:** Bluetooth communication was physically observed earlier; connected RSSI ranging was not physically available or validated. **RSSI ranging: NOT AVAILABLE as a calibrated distance capability.**

## 4. Wi-Fi RTT

Android introduced Wi-Fi RTT in Android 9 / API 28. `WifiRttManager` can range nearby RTT-capable 802.11mc access points **and Wi-Fi Aware peers**; it is not ordinary Wi-Fi Direct phone-to-phone ranging [1][2]. RTT needs compatible hardware on the initiating device and responder. Check `PackageManager.FEATURE_WIFI_RTT` and `WifiRttManager.isAvailable()` at runtime. The result includes distance and distance standard deviation; Android documents typical accuracy as 1–2 m in suitable conditions [1].

- **Direct phone-to-phone:** only through the Wi-Fi Aware peer path, not merely because both phones formed a Wi-Fi Direct group.
- **AP required:** no for Wi-Fi Aware peer RTT; yes for the AP use case.
- **Internet/infrastructure:** no.
- **Wi-Fi Direct required:** no.
- **Minimum platform:** API 28 for RTT; peer setup additionally needs Aware.
- **Permissions:** Android documentation requires `ACCESS_FINE_LOCATION`, `ACCESS_WIFI_STATE`, and `CHANGE_WIFI_STATE`; Android 13+ introduces `NEARBY_WIFI_DEVICES` for nearby Wi-Fi operations, with exact manifest/runtime behavior depending on target SDK and whether location derivation is asserted [1][3][4].
- **Background:** do not assume continuous background ranging. Treat it as a foreground, explicit emergency/Locate operation; platform availability may change with Wi-Fi state and system policy.

**Laptop result:** documentation verified only. A laptop cannot emulate the two Android Wi-Fi chipsets or validate a ranging session.

## 5. Wi-Fi Aware

Wi-Fi Aware (NAN) is Android’s infrastructure-free discovery and local data-path mechanism, introduced in API 26. Both phones must support it and `WifiAwareManager.isAvailable()` must be true [3]. It uses publish/subscribe discovery, produces a `PeerHandle`, and can create an encrypted local data path. It does not require Internet or an access point. It is not Wi-Fi Direct and should remain independent of the existing Wi-Fi Direct communication transport.

Documented conceptual peer-ranging sequence:

```text
verify FEATURE_WIFI_AWARE + FEATURE_WIFI_RTT
→ WifiAwareManager.attach()
→ publisher starts publish / peer starts subscribe
→ service discovery callback yields PeerHandle
→ construct RangingRequest.addWifiAwarePeer(peerHandle)
→ WifiRttManager.startRanging()
→ ranging callback returns distance + standard deviation/status
```

This sequence is conditional: attach, discovery, and ranging can all fail when hardware/resource availability changes. Wi-Fi enabled, supported chipsets, foreground nearby-device/location permissions, and support on **both peers** are required. Android 12 adds Wi-Fi Aware resource/data-path enhancements, not a guarantee that every Android 12+ phone has Aware hardware [3].

**Current-device result:** NOT TESTABLE while ADB is disconnected; no support is inferred from Android 14/15 alone.

## 6. Bluetooth Channel Sounding

Bluetooth Channel Sounding is a Bluetooth Core Specification 6.0 feature intended to permit more accurate distance estimation from multi-channel phase measurements, rather than RSSI alone [8]. It requires controller/radio support and compatible behavior at **both** endpoints. It is distinct from Bluetooth LE communication and classic RFCOMM.

A Bluetooth specification feature is not proof that Android exposes a stable app API or that retail Android 14/15 phones ship enabled controllers. No official Android public-SDK documentation establishing usable Channel Sounding support for these two devices was found in this laptop audit. Consequently:

- **Specification:** documented by Bluetooth SIG.
- **Android public API on current targets:** not established.
- **RMX3782 / vivo V2205 hardware:** not established.
- **Physical measurement:** not tested.

**Decision:** reject as a current implementation target; re-evaluate only with a documented Android API, Android version, controller feature check, and two supported phones.

## 7. UWB

Android supports UWB ranging from Android 12 / API 31 on devices with `android.hardware.uwb`. Both phone peers need UWB. The Android UWB API returns ranging data and relies on an out-of-band channel (for example BLE scanning/GATT) to discover a peer and exchange secure ranging parameters; it does not need Internet [5]. The initiating app must be foreground (or run an allowed foreground service), so it is practical for an explicit Locate/Emergency operation, not a silent perpetual scanner [5]. UWB is a precise ranging technology; actual accuracy must be obtained from the session output and device/vendor conditions rather than promised as a universal number.

- **Required hardware:** UWB on both peers.
- **Required peer capability:** interoperable supported UWB peer / FiRa compatibility for IoT peers.
- **Permissions/API:** Android UWB documentation and the platform/Jetpack UWB APIs; runtime capability check is mandatory [5][6].
- **Offline:** yes after out-of-band setup.

**Current devices:** no attached-device `hasSystemFeature("android.hardware.uwb")` result and no trustworthy manufacturer feature proof were obtained. **UNAVAILABLE ON CURRENT DEVICES is not asserted; status is UNKNOWN / NOT TESTABLE FROM LAPTOP.**

## 8. Existing projects and reference implementations

| Project | URL | Technology / method | Android & hardware | Offline | License / relevance |
|---|---|---|---|---|---|
| AOSP WiFiRttScan sample | https://android.googlesource.com/platform/development/+/master/samples/WiFiRttScan/ | `WifiRttManager`, RTT AP ranging | API 28+, compatible Wi-Fi RTT radio/AP | Yes for ranging; AP deployment is infrastructure | Apache-2.0; API reference, not phone-peer proof [9] |
| Android Connectivity Samples UWB | https://github.com/android/connectivity-samples/tree/main/UwbRanging | UWB session and ranging parameter exchange | Android UWB hardware required | Yes | Apache-2.0; relevant future peer architecture [10] |
| Android Wi-Fi Aware docs/samples | https://developer.android.com/develop/connectivity/wifi/wifi-aware | publish/subscribe and local data path | API 26 + Aware hardware | Yes | Official documentation; discovery prerequisite for peer RTT [3] |
| Bluetooth SIG Core 6.0 | https://www.bluetooth.com/specifications/specs/core-specification-6-0/ | Channel Sounding specification | BT 6.0 controllers at both ends | Yes | Specification, not Android-phone implementation [8] |
| Bluetooth SIG RSSI primer | https://www.bluetooth.com/blog/proximity-and-rssi/ | qualitative received signal strength | BLE/classic radios | Yes | Explains why RSSI is environmental and not a precise range [7] |

These are references, not code to copy into iTantra. Last-updated status should be checked at adoption time from each upstream project/repository.

## 9. Android and technology capability matrix

| Technology | Direct phone-to-phone | Expected range quality | Hardware requirement | Android support | Offline | Current-device status | Complexity |
|---|---|---|---|---|---|---|---|
| GPS/location | Geographic only | coarse at short range; accuracy supplied per fix | GNSS/location provider | broad | yes | physical location callback previously observed on vivo | low |
| Bluetooth RSSI | qualitative proximity | no defensible metre value | Bluetooth + real samples | broad | yes | discovery samples only; connected RSSI not established | low |
| Wi-Fi RTT to AP | no | Android says typically 1–2 m | RTT phone + RTT AP | API 28+ | no Internet, but AP infrastructure | unknown | medium |
| Wi-Fi Aware peer RTT | yes | potentially RTT-grade; session result authoritative | Aware + RTT on both phones | Aware API 26, RTT API 28 | yes | unknown | high |
| Bluetooth Channel Sounding | theoretically yes | potentially precise, vendor/API dependent | BT Core 6.0 controller both ends | no current usable support established here | yes | unknown / reject current | very high |
| UWB | yes | precision session ranging | UWB on both phones | API 31+ | yes | unknown | high |

## 10. Device capability audit

### Device A — RMX3782 (reported RMX3782; Android 15; arm64-v8a)

A web model lookup identifies RMX3782 as a Realme Narzo 60x / C67-family identifier in third-party catalogues; this is insufficient to claim radio feature support [11]. Android version and ABI do **not** establish Wi-Fi RTT, Wi-Fi Aware, UWB, or Channel Sounding capability. Required audit commands, not run because no ADB device was attached:

```text
adb shell pm list features | grep -E 'wifi.rtt|wifi.aware|uwb|bluetooth_le'
adb shell dumpsys wifi | grep -iE 'rtt|aware|nan'
adb shell getprop | grep -iE 'wifi|bluetooth|uwb'
```

**Result:** Bluetooth LE/framework capability is likely but not device-capability verified in this audit. RTT/Aware/UWB/Channel Sounding: **UNKNOWN**.

### Device B — vivo V2205 (reported V2205; Android 14; arm64-v8a)

Same limitation: OS version does not demonstrate radio support. No attached ADB device or manufacturer primary specification confirming RTT/Aware/UWB/Channel Sounding was available during this research run.

**Result:** RTT/Aware/UWB/Channel Sounding: **UNKNOWN**. No chipset is inferred from CPU/OS.

## 11. Recommended capability-aware architecture (future only)

Keep communications independent. A future feature could expose a `PeerRangingProvider` result:

```text
source, distanceMeters?, accuracyMeters?, timestamp,
availability, confidence, peerCapability
```

Candidate providers: `UwbRangingProvider`, `WifiAwareRttProvider`, `BluetoothChannelSoundingProvider`, `BluetoothRssiTrendProvider`, `GpsGeographicProvider`. Select only after runtime capability negotiation and an actual session—not a fixed universal priority. A safe policy is: use a current successful precision ranging result for proximity category; use GPS for geographic bearing; use heading for relative direction; use RSSI only to qualify approach/retreat. If sources disagree, say uncertain/unreliable rather than synthesizing a precise distance.

## 12. Offline requirements and risks

All candidate radio ranging can be local/offline. Internet, Maps, Firebase, AWS, and cellular data are unnecessary. Permissions and foreground requirements remain platform requirements, not cloud dependencies. Risks include device fragmentation, scarce Wi-Fi Aware support, radio coexistence with Wi-Fi Direct/Bluetooth, permission denial, resource exhaustion, background limits, peer identity/security for out-of-band UWB parameters, and multipath/NLOS error.

## 13. Prototype and future hardware recommendation

- **Current prototype:** retain uncertainty-aware GPS bearing/distance category and optional real RSSI trend. Do not present GPS 14 m as tape-measured range.
- **Wi-Fi RTT:** **OPTIONAL**, gated on both `FEATURE_WIFI_AWARE` and `FEATURE_WIFI_RTT` plus a physical peer session. Do not use plain Wi-Fi Direct as a substitute.
- **Bluetooth Channel Sounding:** **REJECT** for current devices; future optional only after platform/hardware proof.
- **UWB:** **OPTIONAL / best precision option**, gated on both UWB features and physical validation.
- **Future hardware procurement:** choose two Android models explicitly documented by the vendor to support UWB and, separately, validate Android API availability/interoperability; do not buy based on Bluetooth version alone. If Wi-Fi Aware RTT is desired, test the exact pair before commitment.

## 14. Sources

1. Android Developers, “Wi-Fi location: ranging with RTT”: https://developer.android.com/develop/connectivity/wifi/wifi-rtt
2. Android API reference, `WifiRttManager`: https://developer.android.com/reference/android/net/wifi/rtt/WifiRttManager
3. Android Developers, “Wi-Fi Aware overview”: https://developer.android.com/develop/connectivity/wifi/wifi-aware
4. Android Developers, “Nearby Wi-Fi devices permission”: https://developer.android.com/develop/connectivity/wifi/wifi-permissions
5. Android Developers, “Ultra-wideband (UWB) communication”: https://developer.android.com/develop/connectivity/uwb
6. Android API reference, `UwbManager`: https://developer.android.com/reference/android/uwb/UwbManager
7. Bluetooth SIG, “Proximity and RSSI”: https://www.bluetooth.com/blog/proximity-and-rssi/
8. Bluetooth SIG, Bluetooth Core Specification 6.0: https://www.bluetooth.com/specifications/specs/core-specification-6-0/
9. AOSP WiFiRttScan sample: https://android.googlesource.com/platform/development/+/master/samples/WiFiRttScan/
10. Android Connectivity Samples, UWB Ranging: https://github.com/android/connectivity-samples/tree/main/UwbRanging
11. Model lookup only (not a radio-feature source): https://www.gsmarena.com/realme_narzo_60x-12542.php
