# Phase 3C.1 state machine

## Transport states

Each byte transport reports `IDLE`, `DISCOVERING`, `CONNECTING`, `CONNECTED`, `DISCONNECTING`, or `FAILED`. Wi-Fi Direct is selected only in `CONNECTED`; radio availability alone is insufficient.

## Queue target

```text
QUEUED -> SENDING -> ACKNOWLEDGED
SENDING -> RETRYING -> SENDING
QUEUED/RETRYING/SENDING -> EXPIRED
RETRYING -> FAILED
```

The existing Room queue still persists retry count and errors, but next-retry timestamps, full TTL worker behavior, persistent replay inbox state, and exact `ACKNOWLEDGED` naming require the next integration step.

ACK is transport/application receipt, never PLAYED or HEARD.
