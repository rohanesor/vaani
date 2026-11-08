# Phase 3C.2 production transport integration

## Status: PARTIAL

`OutboundMessageProcessor` no longer calls `Transport.send(Message)` directly. It asks `TransportManager` for the active transport and calls `sendMessage`, which creates Packet v2 bytes before handing them to the manager.

`TransportManager` prefers an actually connected Wi-Fi Direct packet transport and otherwise uses the existing Bluetooth compatibility transport. The application constructs both transports.

The migration is not yet production-complete: Bluetooth still has no raw byte/ACK session adapter, Wi-Fi ACK correlation is not wired, and a Wi-Fi send currently returns a non-acknowledged result. Therefore the Room queue cannot yet claim a complete Wi-Fi ACK → ACKNOWLEDGED path or automatic Wi-Fi-failure → Bluetooth retry.

No message is marked delivered without an ACK. No TTS/model/network changes were made.
