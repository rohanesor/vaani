# Packet v2

## Wire format

All integers are big-endian. No Kotlin/Java object serialization is used.

```text
MAGIC:uint16
VERSION:uint8 = 2
FLAGS:uint8
MESSAGE_ID:utf8-length:uint16 + bytes
SEQUENCE_NUMBER:int64
SOURCE_ID:utf8-length:uint16 + bytes
TIMESTAMP:int64 (Unix milliseconds)
TTL:int64 (milliseconds)
PRIORITY:uint8
LANGUAGE_CODE:utf8-length:uint16 + bytes
FRAGMENT_INDEX:uint16
FRAGMENT_COUNT:uint16
PAYLOAD_LENGTH:int32
PAYLOAD:bytes
MESSAGE_TYPE:uint8
RECEIVER_ID:utf8-length:uint16 + bytes
REQUIRES_ACK:boolean
CRC32:uint32
```

Payload is limited to 8 KiB per fragment and complete frames to 16 KiB. CRC32 covers every preceding wire byte. It provides integrity only: it is not encryption, authentication, or identity proof.

`PacketV2.fromMessage` deterministically fragments UTF-8 payload bytes. `PacketReassembler` rejects expired packets, ignores duplicate fragments, waits for every index, orders fragments, and only then creates a complete message. Missing fragments time out.

## Compatibility

`VokieProtocol` is a compatibility facade and now emits/decodes Packet v2 for existing Bluetooth callers. A fragmented message must use `PacketV2.fromMessage`; the facade rejects a fragmented message rather than silently truncating it.

Replay persistence across process death and complete sequence-window policy require the Room inbox migration in the next hardening step. No cryptographic security claim is made.
