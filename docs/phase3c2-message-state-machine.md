# Phase 3C.2 message state machine

## Target states

```text
QUEUED -> SENDING -> ACKNOWLEDGED
SENDING -> RETRYING -> SENDING
QUEUED/RETRYING/SENDING -> EXPIRED
RETRYING -> FAILED
```

Room remains authoritative. Existing retry count/error persistence is retained and message protocol metadata now includes sequence number and TTL. A Room `received_packets` table was added in schema migration 2→3 for replay identity storage.

`nextRetryAt`, a local expiration worker, and complete explicit ACKNOWLEDGED transitions through the new byte-oriented manager remain outstanding. At-least-once delivery is the safe assumption.
