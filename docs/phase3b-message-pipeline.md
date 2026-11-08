# Phase 3B message pipeline

## Status

Packet v2 is implemented as a transport-independent binary layer. Existing Room and Bluetooth code remain compatible through the `VokieProtocol` facade. No Wi-Fi Direct work is included in this phase.

Pipeline:

```text
Message -> PacketV2 fragments -> transport bytes -> CRC/version/language validation
  -> PacketReassembler -> complete Message -> Room idempotency -> LanguageRouter/TTS
```

The receiver never sends a packet to TTS until all fragments have been reassembled and validated. TTS remains explicitly blocked when no approved backend is installed.

## Language metadata

`Message.language` is canonicalized through the existing `VokieLanguage` set (`HI`, `GU`, `MR`, `KN`, `ML`, `TA`, `TE`, `OR`, `BN`, `EN`). Unknown codes are rejected; they never become English.

## Boundaries

- Production: PacketV2 framing, CRC validation, deterministic fragmentation, reassembly, expiry checks.
- Test-only: in-memory `PacketReassembler` and `ReplayGuard` tests.
- Benchmark-only: MMS.
- Blocked: approved production TTS artifact, automatic Whisper detected-language metadata, Wi-Fi Direct, persistent replay inbox.
