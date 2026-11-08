# Phase L1 — Message-triggered GPS-free relative proximity

## Architecture

The L1 layer is an additional observer of the existing communication path:

```text
Vokie message / ACK event
→ LocationMeasurementCollector
→ latest per-peer RelativeProximityMeasurement
→ Locate UI
```

It does not modify Whisper, CT2, TTS, PacketV2 framing, CRC, fragmentation, ACK protocol, or Wi-Fi Direct TCP. `TransportManager.sendMessage()` records `MESSAGE_SENT` and `ACK_RECEIVED`; `VokieApplication` records inbound `MESSAGE_RECEIVED` after the existing inbound coordinator accepts the decoded message. ACK RTT is measured locally from the send-message boundary to ACK resolution.

## Available and unavailable measurements

The existing Wi-Fi Direct implementation exposes P2P group state and live TCP readiness, but no standard Android peer RSSI API is used here. The Bluetooth RSSI provider only accepts discovery RSSI and is not a Wi-Fi Direct peer measurement; it is not treated as a Wi-Fi Direct signal value. Therefore L1 records:

- peer/device identifier;
- trigger timestamp and event;
- transport and Wi-Fi Direct state;
- ACK RTT when the event is an acknowledged send;
- delivery result and retry count when available.

No GPS coordinates, map, Google location service, cloud API, RSSI-to-metres conversion, bearing, or directional estimate is used.

## First proximity model

A direct Wi-Fi Direct link with an acknowledged PacketV2 message produces `NEAR` with `LOW` confidence for a receive snapshot and `MEDIUM` confidence for an ACK snapshot when RTT is available. Without an acknowledged direct-link event the result is `UNKNOWN`/`LOW`. `VERY_NEAR` is intentionally not emitted: the current measurements prove local reachability, not a physical distance. RTT is supporting timing evidence only and is not a ranging measurement.

Measurements are held in memory as the latest value per peer and are stale after 120 seconds. The model is prepared for multiple peer IDs without implementing multi-device positioning.

## Locate UI

Locate now presents only:

- `Proximity estimate unavailable` until a communication measurement exists;
- `NEARBY`/`UNKNOWN`, direct-link state, confidence, trigger, and optional ACK RTT;
- a stale/unavailable state after the freshness limit;
- an explicit `GPS-free • no coordinates • no distance estimate` notice.

No latitude/longitude, map, directional arrow, or distance in metres is shown by L1. Existing coordinate-sharing backend code remains outside the L1 presentation and is not used to generate this estimate.

## Physical validation

Devices:

```text
Phone A: V2205  / 10BCAC2HM5000CR
Phone B: RMX3782 / MJPVXCSG9HYL65YL
```

Both used the explicitly deployed debug APK. Before communication, both applications had no `VOKIE_PROXIMITY` measurement records, confirming no fabricated initial estimate.

### A → B

Existing Wi-Fi Direct debug message path sent `VOKIE_L1_A_TO_B` from A to B.

```text
Wi-Fi Direct ready: A and B
A connection:       506 ms
A MESSAGE_SENT:     peer=unknown (debug message initially omitted receiver ID)
A ACK_RECEIVED:     RTT=35 ms, delivered=true, zone=NEAR, confidence=MEDIUM
B MESSAGE_RECEIVED: peer=<Phone A app device UUID>, delivered=true, zone=NEAR, confidence=LOW
Packet ACK:         correlated
```

The validation then corrected the debug message to carry its known peer address for future snapshots; PacketV2 itself was unchanged.

### Reverse B → A

Phone B (`MJPVXCSG9HYL65YL`) connected to Phone A (`10BCAC2HM5000CR`) over Wi-Fi Direct and sent `VOKIE_L1_REVERSE`. The previously known Phone A P2P address was discovered in this fresh session.

```text
B MESSAGE_SENT:      peer=f6:63:fc:b9:d2:be, transport=WIFI_DIRECT
B ACK_RECEIVED:      RTT=102 ms, delivered=true, zone=NEAR, confidence=MEDIUM
A MESSAGE_RECEIVED:  peer=<Phone B app device UUID>, transport=WIFI_DIRECT
A delivered:         true, zone=NEAR, confidence=LOW
Packet ACK:           correlated
```

No payload corruption, crash, or ANR occurred. The classifier remained unchanged.

### No-communication/staleness

After switching Phone A to Locate and waiting more than 120 seconds after the last measurement, the UI displayed:

```text
Proximity estimate unavailable
Send or receive a Vokie message to create a local measurement.
GPS-free • no coordinates • no distance estimate
```

The old estimate was not presented as current. This stale-state check passed.

### Physical separation comparison

The phones were not physically moved to multiple measured positions during this run. Therefore separation-dependent variation is **NOT VALIDATED**. The implementation must not be interpreted as a distance classifier; it may continue to report `NEAR` at any reachable direct-link position.

## Status

```text
Implementation:       PASS
Physical A→B trigger: PASS
Physical B→A trigger: PASS
Stale UI behavior:    PASS
Physical separation:  NOT VALIDATED
Phase L1 overall:      NOT PASS
```

## Phase L2 — GPS-free proximity measurement study

The collector and unchanged L1 classifier were exercised with the two connected devices. Available measurements remain limited to Wi-Fi Direct state, TCP readiness, PacketV2 delivery/ACK result, timestamps, peer identifiers, and ACK RTT. No peer-specific Wi-Fi Direct RSSI or other radio-strength value is exposed or used by this implementation.

Observed same-session baseline values from physical exchanges:

| Exchange | Link | ACK RTT | Delivery | Classifier |
|---|---|---:|---|---|
| A → B | Wi-Fi Direct / TCP | 35 ms | acknowledged | NEAR / MEDIUM at ACK; NEAR / LOW at receive |
| B → A | Wi-Fi Direct / TCP | 102 ms | acknowledged | NEAR / MEDIUM at ACK; NEAR / LOW at receive |

These observations were made without controlled separation labels. They are not evidence that RTT measures distance.

A subsequent L2 repeated-exchange attempt at the current position failed before message transmission because the Wi-Fi Direct client remained `CONNECTING` and the debug harness stopped with its existing connection timeout. No new RTT, retry, or delivery sample was recorded from that attempt. No phones were physically moved through close/several-metre/farther positions, so the required multi-separation experiment is not complete.

The current classifier was not changed. The available evidence is insufficient to determine whether physical separation produces a useful signal. A controlled experiment with an operator physically placing the phones at labeled positions and re-establishing the P2P session at each position is still required. This phase does not start direction finding, ranging, or multi-device positioning.

## Status

```text
Measurement collection:       PASS (implemented; physical baseline samples available)
Physical separation study:    NOT PASS (not performed)
Useful separation signal:     INCONCLUSIVE
Current classifier changed:   NO
Phase L2 overall:             NOT PASS
```
