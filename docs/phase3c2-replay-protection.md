# Phase 3C.2 replay protection

## Status: PARTIAL

Room now defines `received_packets` with the composite identity:

```text
sourceDeviceId + messageId + sequenceNumber
```

Migration 2→3 creates the table, with received and expiry timestamps plus an expiration deletion query. The DAO is not yet connected to the authoritative inbound pipeline, so process-death replay rejection is not complete.

The in-memory `ReplayGuard` remains useful for tests, but no cryptographic authentication or encryption is claimed. A future inbound coordinator must atomically insert this identity before message persistence/application delivery and reject an insert conflict.
