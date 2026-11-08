# Room state machine

## Status: PARTIAL

Existing Room persistence retains retry count, TTL, transport, sequence, priority, checksum, and last error. The required target is `QUEUED → SENDING → ACKNOWLEDGED`, with bounded retry and `EXPIRED`/`FAILED` terminal states.

`nextRetryAt`, persistent retry scheduling, TTL expiration worker, and manager-owned ACK state transitions are not implemented.
