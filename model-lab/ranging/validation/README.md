# L4-R4 validation data and log-readiness audit

## Objective
This directory is reserved for real, user-performed two-phone validation. The application must not infer ground truth from RSSI. Distances are metres only when entered by the experimenter; angles are degrees only when entered by the experimenter.

## Current data status
`distance_validation_measurements.json`, `direction_validation_measurements.json`, and `validation_summary.json` are placeholders. They contain no records.

**REAL MEASUREMENTS = 0**

The existing debug BLE lab is a separate diagnostic. It automatically collects at most 30 scan samples and writes one summary object to the app-private `ranging-lab/ble_measurements.json`; it does not write this validation directory, does not attach experiment/run labels, and overwrites rather than appends that diagnostic file.

## Measurement matrix

| Measurement | Source class | Captured? | Timestamp? | Peer ID? | Stored in JSON? | Used by distance estimator? | Used by direction estimator? | Phone A | Phone B | Notes |
|---|---|---:|---:|---:|---:|---:|---:|---:|---:|---|
| BLE RSSI | `BleMeasurementActivity`; `BluetoothTransport` discovery | YES in separate diagnostic / discovery | YES in diagnostic / discovery | MAC in diagnostic; peer ID in engine | Diagnostic summary only; validation recorder API can store raw value | YES if supplied as `LocalizationMeasurement` | YES if supplied as `DirectionObservation` | available | available | Connected classic Bluetooth has no standard RSSI polling here |
| Filtered BLE RSSI | `RelativePeerLocalizationEngine` | YES in memory when engine receives discovery RSSI | YES on measurement | YES | Sparse engine JSON only; not validation JSON | YES | YES | capability available | capability available | Moving median + EWMA |
| RSSI sample count | engine `RssiStatistics`; BLE lab summary | YES | latest timestamp only | YES in engine | Yes in sparse engine JSON only indirectly; BLE lab summary has count | YES as history input | YES as caller-provided observation count | available | available | BLE lab stops at 30 |
| RSSI timestamp | discovery/engine | YES | YES | YES | Sparse engine record | YES | YES | available | available | Unix epoch milliseconds |
| Peer MAC/address | `BluetoothTransport` / `ScanResult` | YES | discovery timestamp | address is peer identity in transport | BLE lab summary omits address; logcat includes it | indirectly | caller must copy it | scanner sees peer | advertiser address may be seen | `FC:2A:46:06:40:FE` was previously observed |
| Bluetooth state | `BluetoothTransport` | YES as state flow | state transition not persisted by validation recorder | NO | NO in validation files | NO | NO | available | available | Logs use `VOKIE][BT` |
| BLE scan state | `BleMeasurementActivity` | YES as UI/log events | log timestamp | NO | NO | NO | NO | scanner | not applicable | `SCAN_STARTED`, `SAMPLE`, `COMPLETE` |
| BLE advertising state | `BleMeasurementActivity` | YES as UI/log events | log timestamp | NO | NO | NO | NO | not applicable | advertiser | `ADVERTISE_STARTED` |
| Wi-Fi Direct state | `WifiDirectTransport` | YES as state flow | state transitions not in validation JSON | NO | NO | NO | NO | available | available | `VOKIE][BT` messages use `WIFI_*` |
| Wi-Fi Direct connection state | `WifiDirectTransport` / `TransportManager` | YES in runtime | packet/log timestamps | peer address only in peer list | NO | no | no | available | available | Connection proves reachability, not distance |
| ACK RTT | `TransportManager` / old `LocationMeasurementCollector` | YES for PacketV2 sends | elapsed realtime-derived duration; event timestamp absent in JSON | connected peer fallback | NO validation storage | not used as distance | no | available when messaging | available when messaging | Communication metric only |
| message sent timestamp | `TransportManager` / repository | YES in logs and old collector | yes | connected peer | NO validation storage | no | no | available | available | `TX_CREATE`, message pipeline |
| message received timestamp | `InboundPacketCoordinator` / application | YES in logs/collector | yes in log event | packet source ID | NO validation storage | no | no | available | available | `MESSAGE_RECEIVED` |
| retry count | `LocationMeasurementCollector` only when caller supplies it | nullable | measurement timestamp | peer ID | NO validation storage | no | no | available | available | Not supplied by validation recorder automatically |
| delivery status | old collector | YES nullable | measurement timestamp | peer ID | NO validation storage | no | no | available | available | `delivered` is communication status |
| accelerometer | `RelativePeerLocalizationEngine` SensorEventListener | YES in memory after peer starts | snapshot timestamp | attached to latest in-memory measurement | Not in validation JSON; sparse engine JSON omits snapshot | `MotionClassifier` can use it | observation caller can use it | available | available | No sensor logcat records |
| gyroscope | engine SensorEventListener | YES in memory after peer starts | snapshot timestamp | attached to latest snapshot | NO validation JSON | `MotionClassifier` can use it | observation caller can use it | available | available | No sensor logcat records |
| magnetometer | engine SensorEventListener | YES in memory after peer starts | snapshot timestamp | attached to latest snapshot | NO validation JSON | not currently used by distance calculation | observation caller can use it | available | available | No sensor logcat records |
| rotation vector | engine SensorEventListener | YES in memory after peer starts | snapshot timestamp | attached to latest snapshot | NO validation JSON | not currently used by distance calculation | `RotationVectorOrientation` utility can derive phone heading | available | available | This is phone orientation, not peer bearing |
| motion state | `MotionClassifier` | derived on demand, not recorded by runtime | inherits source timestamps | only if caller supplies history | Validation schema supports string; no runtime writer | distance UI can display it | direction observations support it | available if invoked | available if invoked | Not automatically fed into recorder |
| orientation | rotation vector utility / nullable recorder field | utility exists; not captured by BLE lab | sensor snapshot timestamp if supplied | only if caller copies | Validation schema supports list; runtime does not write it | no | input field only | available if invoked | available if invoked | No current UI control |
| distance estimator state | `RelativeDistanceEstimator` | YES only when explicitly called | latest input timestamp | input peer | NO validation JSON | output | no | available in code | available in code | Ranging Lab calls with one sample and no calibration => unavailable |
| estimated distance | estimator | nullable | output timestamp | input peer | schema supports it; no records | output | no | unavailable in current workflow | unavailable in current workflow | Never fabricate |
| distance confidence | estimator | nullable output | output timestamp | input peer | schema supports it; no records | output | no | unavailable in current workflow | unavailable in current workflow | Calibration-dependent |
| distance uncertainty | estimator | nullable interval bounds | output timestamp | input peer | schema supports metres fields; no records | output | no | unavailable in current workflow | unavailable in current workflow | Pair calibration required |
| direction sector | `RelativeDirectionEstimator` | always UNKNOWN currently | output timestamp default unless called | input peer | schema supports it; no records | no | output | unavailable | unavailable | Hypotheses are not selected |
| bearing | direction estimator | nullable | output timestamp | input peer | schema supports it; no records | no | output | unavailable | unavailable | No physical validation |
| direction confidence | estimator | LOW on UNKNOWN output | output timestamp | input peer | schema supports it; no records | no | output | unavailable | unavailable | No validated method |
| direction uncertainty | estimator | nullable | output timestamp | input peer | schema supports it; no records | no | output | unavailable | unavailable | No validated bearing |
| physical ground-truth distance | user/API field in `ValidationMeasurement` | API field only | record timestamp | API requires peer | not populated | calibration input only | no | user must enter | user must enter | No UI field exists |
| physical ground-truth angle | user/API field in `ValidationMeasurement` | API field only | record timestamp | API requires peer | not populated | no | calibration/evaluation input | user must enter | user must enter | No UI field exists |

**Available is not the same as actually recorded.** The recorder class is not referenced by any Activity or application runtime path (`PhysicalValidationRecorder` has no call site). Therefore the current implementation cannot capture a complete L4-R4 record from the UI.

## JSON schema audit

The validation files are JSON arrays for distance and direction, and an object placeholder for the summary. The recorder schema uses:

- `timestamp`: Unix epoch milliseconds (`Long`)
- `rawRssi`: nullable integer, dBm, constrained to -127..0
- `filteredRssi`: nullable numeric dBm
- `groundTruthDistanceMeters` / `estimatedDistanceMeters` / `uncertaintyMeters`: nullable numeric metres
- `groundTruthAngleDegrees` / `estimatedBearingDegrees` / `uncertaintyDegrees`: nullable numeric degrees
- sensor vectors: nullable JSON arrays of floats
- state, transport, confidence and sector: nullable strings
- `sampleCount`: integer >= 1

The separate BLE lab file is a single JSON object with summary fields (`timestamp`, `serviceUuid`, `sampleCount`, `minRssi`, `maxRssi`, `meanRssi`, `medianRssi`, `stddevRssi`) and is not an L4-R4 record.

## Runtime tags and useful messages

| Tag | What it logs | When | Useful? |
|---|---|---|---|
| `VOKIE_BLE_LAB` | advertising, scan start/failure, each raw RSSI/address, 30-sample summary | debug BLE Activity | YES for raw BLE diagnostic |
| `VOKIE][BT` | Bluetooth discovery, connection, PacketV2 decode, Wi-Fi Direct lifecycle, ACK correlation, TX/RX | transport activity | YES for transport correlation; not a structured ranging log |
| `VOKIE_PROXIMITY` | old communication measurement trigger, peer, transport, RTT, delivery, qualitative zone/confidence | message/ACK path | YES for communication events; ACK RTT is not distance |
| `VOKIE_RANGING` | capability inventory is logged by `RangingCapabilityManager` only through `Log.i` | Ranging Lab startup | YES for capabilities |
| `VOKIE_WIFI_SMOKE` | debug Wi-Fi smoke activity events | Wi-Fi smoke Activity | optional |
| `VOKIE_WHISPER_PACKET` | debug packet smoke events | packet smoke Activity | no for ranging |

There is no `VOKIE_VALIDATION`, distance, direction, sensor, or recorder log tag. The missing structured validation logging is a readiness blocker.

## Current UI workflow (exact)

1. Launch debug `com.vokie/.ranging.RangingLabActivity` to view capabilities and the latest application localization snapshot. It has no buttons or recorder controls.
2. Launch `com.vokie/.ranging.BleMeasurementActivity` with `--es role ADVERTISE` on the advertiser and without/with `--es role SCAN` on the scanner.
3. The BLE Activity automatically starts its role in `onCreate`; it filters service UUID `0000f4b0-0000-1000-8000-00805f9b34fb`.
4. There is no peer selector, experiment ID field, run ID field, ground-truth distance/angle field, start-record button, stop/finalize button, reset button, or export button.
5. After 30 scan results the BLE Activity automatically stops scanning and writes its summary. There is no append or validation recorder call.

Thus starting/stopping a complete L4-R4 recording and entering labels are currently impossible from the UI. The `PhysicalValidationRecorder` can only be invoked programmatically by a future debug workflow.

## Physical test procedure

### SETUP

- Phone A: V2205, scanner/measurement phone.
- Phone B: RMX3782, stationary advertiser.
- Start the advertiser first, then scanner. Confirm the intended peer address in `VOKIE_BLE_LAB`.
- Before every labelled batch, manually enter the experiment/run/position label through the eventual recorder workflow. Do not label an unpositioned batch.
- Wi-Fi RTT must be recorded as unavailable on these devices. ACK RTT, if collected, remains a communication metric.

### DISTANCE TEST

For each P0 = 0.5 m, P1 = 1 m, P2 = 2 m, P3 = 3 m, P4 = 5 m:

1. Physically place Phone A at the experimenter-set separation from stationary Phone B.
2. Keep orientation fixed and wait for stabilization.
3. Collect >=30, preferably 60, valid BLE samples.
4. Enter the manually measured `groundTruthDistanceMeters`; keep estimator output separate and nullable.
5. Save the run, then repeat the complete P0–P4 sequence with a second run.

### DIRECTION TEST

At constant approximate separation, physically place B at 0°, 45°, 90°, 135°, 180°, 225°, 270°, and 315° around A. Collect >=20, preferably 30–60 samples at each angle, enter `groundTruthAngleDegrees`, save, and repeat. Physical angle is not sensor heading.

### ROTATION TEST

Keep the peer fixed and rotate the measurement phone. Record sensor-derived orientation and RSSI; do not assume the requested angle occurred and do not equate maximum RSSI with peer direction.

### ARC MOVEMENT TEST

Move one phone around the other while continuously recording timestamp, RSSI, filtered RSSI, sensor vectors, motion state, physical angle and physical distance labels. Analyze RSSI/time and RSSI/angle after collection.

## Communication versus ranging

Communication logs establish packets, delivery, connection and ACK latency. They do not establish physical distance or direction. Wi-Fi Direct connected means reachable; BLE detected means radio-visible. Neither is a peer position. ACK RTT is not converted to metres.
