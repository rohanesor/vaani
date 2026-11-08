# Android two-device communication baseline — Phase 4A

## Devices and deployment

| Role | Device | ADB serial |
|---|---|---|
| Phone A | vivo V2205 | `10BCAC2HM5000CR` |
| Phone B | realme RMX3782, Android 15, arm64-v8a | `MJPVXCSG9HYL65YL` |

The same debug APK was explicitly installed with `adb -s` on both devices. Both were foregrounded, awake, unlocked, and had Wi-Fi enabled. This validation used Android Wi-Fi Direct; it does not depend on both phones joining a conventional LAN.

## Existing transport, discovery, and protocol

The repository already implements two physical transports:

- primary classic Bluetooth RFCOMM using `VokieProtocol.SERVICE_UUID`; and
- Android Wi-Fi Direct discovery/group formation followed by one TCP socket.

This validation used Wi-Fi Direct. `WifiP2pManager.discoverPeers` supplies P2P peer addresses/names. The initiator calls `WifiP2pManager.connect`; the group owner binds TCP `ServerSocket` port **39721** and the client connects to the Wi-Fi Direct group-owner IP with retries. TCP uses a 4-byte big-endian length prefix and opaque PacketV2 bytes. No NSD, HTTP, WebSocket, UDP, or ordinary-LAN peer discovery is implemented.

PacketV2 is binary, big-endian, version 2, CRC32 protected, and fragments payloads above 8192 bytes. A message packet carries: UUID message ID, sequence number, source-device UUID, timestamp, TTL, priority, source language code, fragment index/count, UTF-8 payload, message type ordinal, optional receiver ID, and ACK-required flag. ACK frames carry message ID, sequence number, receiver/source ID, and timestamp. CRC is integrity only, not encryption or authentication.

## Physical results

### A → B

Phone A discovered Phone B (`ca:69:06:d4:1a:25`, `realme narzo 60x 5G`) and connected as the P2P client. Phone B was group owner at `192.168.49.1:39721`.

```text
A TCP connected: 1007 ms
A sent:           VOKIE_TEST_A_TO_B
B received:       VOKIE_TEST_A_TO_B
A ACK:            correlated; send acknowledged=true
```

### B → A

A fresh session reversed the initiator roles. Phone B discovered Phone A (`f6:63:fc:b9:d2:be`, `vivo Y35`), became group owner at `192.168.49.1:39721`, and completed the TCP session in 1507 ms.

```text
B sent:           VOKIE_TEST_B_TO_A
A received:       VOKIE_TEST_B_TO_A
B ACK:            correlated; send acknowledged=true
```

No payload corruption, crash, or ANR was observed in either direction.

## Presence/context and reconnection

The available presence context is only Wi-Fi Direct discovery availability plus P2P device name/address and the live TCP connection state. It is not GPS, zone, distance, or location proof.

For the teardown observation, Phone A's Vokie process was force-stopped. Phone B logged Wi-Fi session invalidation/cleanup. The current Wi-Fi Direct transport tears down the TCP/P2P session and does **not** implement automatic Wi-Fi Direct peer reconnection; Bluetooth has a separate retry policy. This is documented behavior, not a reconnection PASS.

## Phase 4A result

**PASS** for existing two-device Wi-Fi Direct discovery, connection, PacketV2 framing, receiver delivery, and correlated ACK baseline. No speech, translation, TTS, CT2, or production conversation UI pipeline was invoked.
