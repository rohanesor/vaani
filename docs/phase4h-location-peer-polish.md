# Phase 4H — Location, proximity, and peer-discovery polish

## Scope and invariants

**Code verified:** location payload remains `LocationPacket` in the existing PacketV2/CRC/fragment/ACK envelope. No PacketV2, Bluetooth RFCOMM, Wi-Fi Direct framing, STT, translation, or TTS changes were made.

The product treats GPS geographic position, device heading, and Bluetooth RSSI as separate evidence. RSSI is never converted to metres.

## Implemented and unit verified

- `DistanceSmoother`: bounded five-sample median for **display only**. Bearing remains based on the current valid geographic fix.
- `DistanceTrendClassifier`: bounded window classification (`GETTING_CLOSER`, `GETTING_FARTHER`, `STABLE`, `UNRELIABLE`), with stale/invalid samples rejected.
- Distance buckets: very close (<5 m), nearby (<15 m), close (<50 m), near (<100 m), far (<=500 m), farther away.
- The Locate card displays a bucket rather than a precise range when combined local + peer GPS accuracy is at least the displayed distance. Otherwise it displays `≈ N m away`.
- The card shows combined accuracy in plain language and a sender freshness age / stale status.
- GPS trend works without RSSI. Current RSSI can only originate in real Bluetooth discovery callbacks; connected RFCOMM RSSI is not fabricated.
- Inbound location updates retain the latest sequence for the active sender; equal/older sequence updates are rejected and logged. Packet-level duplicate handling remains in `InboundPacketCoordinator`.
- Heading retains the existing circular smoothing implementation, including correct 359° → 1° handling.
- Bluetooth discovery retains inquiry candidates when SDP `ACTION_UUID` is absent. A connection still verifies the Vokie RFCOMM service UUID; arbitrary devices are not auto-connected.
- Wi-Fi state semantics remain: P2P group is not user-ready application transport; only a live TCP transport becomes connected.

## Tests

`DistancePresentationTest` verifies median behavior, uncertainty presentation, proximity buckets, noisy closer trend, stale input, and GPS-only proximity trend. Existing location/proximity tests verify cardinal bearing, heading-relative direction, heading wrap-around, invalid fixes, stale data, RSSI trends, GPS/RSSI agreement and contradiction.

## Physical device matrix

| Device | OS | Status |
|---|---:|---|
| RMX3782 | Android 15, arm64-v8a | APK install/launch verified; foreground location permission previously granted |
| vivo V2205 | Android 14, arm64-v8a | APK install/launch verified; foreground location permission previously granted |

## Physical evidence and limitations

**Measured previously:** a device GPS provider registration and real location callback were observed on vivo. A close-range observation reported roughly 14 m while the phones were physically close; this is correctly treated as GPS uncertainty, not a distance-algorithm defect.

**Not physically verified in this build:** clean Bluetooth location A→B/B→A, Wi-Fi Direct location A→B/B→A, movement trend, heading rotations, reconnect, offline transport, and RSSI availability. Wi-Fi Direct ACK/queue-completion validation also remains an earlier transport gate and must be physically passed before claiming Wi-Fi location delivery.

**RSSI:** NOT AVAILABLE for connected RFCOMM unless Android produces real discovery samples. This is an API capability limitation, not a synthetic telemetry gap.

**Battery lifecycle:** code verified: Locate starts location/heading on entry and stops both in `DisposableEffect` on exit. Physical battery measurement: NOT MEASURED.
