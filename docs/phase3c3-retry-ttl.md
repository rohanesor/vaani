# Retry and TTL

## Status: BLOCKED

Packet-level TTL validation is present. The Room queue still uses the prior in-memory retry delay. No persisted `nextRetryAt` or local expiration worker exists, so restart-safe retry and queue-level expiry are not claimed.
