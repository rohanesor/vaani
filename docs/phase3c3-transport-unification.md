# Phase 3C.3 transport unification

## Status: PARTIAL

Both Wi-Fi Direct and Bluetooth now expose raw Packet v2 bytes through the `PacketTransport` boundary. `TransportManager` merges incoming byte streams and chooses Wi-Fi Direct only when its lifecycle is `CONNECTED`, otherwise Bluetooth.

`OutboundMessageProcessor` now calls `TransportManager.sendMessage` rather than selecting Bluetooth directly. The manager creates Packet v2 fragments before sending.

The Bluetooth adapter still wraps the legacy socket implementation, and manager-level ACK correlation/fallback retry is not complete. This phase is therefore not code-complete under the requested gate.
