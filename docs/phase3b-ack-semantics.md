# Packet v2 acknowledgement semantics

ACK is a transport/application receipt, not proof of user playback.

## States

- `SENDING`: sender wrote all packet fragments to the transport.
- `RECEIVED`: receiver accepted a frame after framing and CRC checks.
- `REASSEMBLED`: all fragments for the message arrived and passed expiry/order checks.
- `PERSISTED`: complete message was accepted by Room idempotency storage.
- `ACKNOWLEDGED`: sender received an ACK for the message ID.
- `FAILED`/`RETRYING`/`EXPIRED`: sender could not complete the configured operation.

The current Bluetooth ACK is emitted after the receiver's protocol path accepts a message; it must not be presented as "heard" or "delivered to the user". Playback acknowledgement is not implemented.

CRC failure, invalid version, malformed language, invalid length, expired packet, duplicate, or incomplete reassembly must not generate a positive ACK. Transport connection does not authenticate a device. CRC integrity is not encryption.
