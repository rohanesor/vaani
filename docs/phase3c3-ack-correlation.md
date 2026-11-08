# Phase 3C.3 ACK correlation

## Status: BLOCKED / PARTIAL

Packet v2 ACK framing exists and Bluetooth's pre-existing ACK tracker remains active. The new `TransportManager` does not yet own one pending-ACK registry keyed by `messageId + sequenceNumber` for both transports.

Wi-Fi transmission is therefore not marked `ACKNOWLEDGED` merely on a socket write; it returns a non-acknowledged result and remains eligible for retry. Same-transport Wi-Fi ACK routing and manager-owned timeout cleanup remain required.

ACK means remote receipt/persistence only. It never means PLAYED or HEARD.
