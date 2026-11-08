# N2 — Automatic Reconnection + Store-and-Forward

## 1. Existing reconnect architecture
**PASS**

Audited and retained:
- `BluetoothTransport`: target-specific reconnect job, generation cancellation, intentional-disconnect suppression, bounded exponential backoff/jitter (`ReconnectPolicy.BLUETOOTH`).
- `WifiDirectTransport`: one reconnect job, target address, generation cancellation, rediscovery/connect lifecycle, intentional-disconnect suppression, `ReconnectPolicy.WIFI_DIRECT`.
- `OutboundMessageProcessor`: Room-backed queue worker, serial processing mutex, retry policy.
- `RoomMessageRepository` / Room: messages persist before queue processing; startup `recoverInterrupted()` changes `TRANSMITTING` to `QUEUED`.
- `InboundPacketCoordinator`: Room received-packet replay key `(sourceDeviceId, messageId, sequenceNumber)` and message-table ID insertion deduplication.

No STT, CT2/NLLB, TTS, PacketV2 wire format, or reconnect-policy parameters were changed.

## 2. Changes made
**PASS**

- Added peer ID to production pending-ACK registration.
- ACK resolution now prefers PacketV2 ACK sender identity and has a transport-scoped compatibility fallback for legacy Bluetooth MAC-vs-app-ID addressing.
- `DecodedTransportFrame.Ack` now carries the ACK sender identity; application handling no longer derives it from selected/current UI peer.
- Added peer-specific recovery: only the disconnected peer's `TRANSMITTING` messages are returned to `QUEUED`; startup recovery remains global because all interrupted work must be restored after process death.
- Queue worker now refuses to send an explicit Peer B Bluetooth message over an active Peer A Bluetooth link. It leaves that message persisted and pending for the correct peer.
- Queue SQL has deterministic FIFO tie-breaking: `timestamp ASC, rowid ASC`.
- Message UI now shows `PENDING`, `RETRYING`, `FAILED`, progress while sending, and delivered check state instead of showing queued messages as delivered.

## 3. Persistence behaviour
**PASS**

`createMessage()` validates then inserts the message in Room before it is observed by the outbound queue. Stable `id`, `receiverId`, `sequenceNumber`, text, and language are retained through retries. On unexpected disconnect, in-flight work for that peer returns to `QUEUED`; on startup, all `TRANSMITTING` work returns to `QUEUED`.

Claim: **persistent local queuing + automatic best-effort reconnect/delivery**, not guaranteed delivery after permanent radio/device failure.

## 4. Message state machine
**PASS**

`QUEUED → TRANSMITTING → RECEIVED_BY_PEER` after ACK.

Failure path: `TRANSMITTING → RETRYING → QUEUED` while retry budget remains; `→ FAILED` at existing retry exhaustion, with manual retry available. Disconnect recovery: `TRANSMITTING → QUEUED` without a new logical message.

## 5. Deduplication
**PASS**

Receiver replay records are keyed by `(sourceDeviceId, messageId, sequenceNumber)`. Duplicate frames are ACKed again but are not published to the presentation flow. Room `messages.id` insertion is an additional idempotency boundary. Thus a lost ACK retransmission does not create a second conversation entry, translation, or TTS enqueue.

## 6. Ordering
**PASS (documented scope)**

Outbound Room queue is FIFO by `timestamp ASC, rowid ASC`. The serial queue worker preserves order among messages currently deliverable to the same active peer. A message for a disconnected peer is intentionally skipped so it cannot block a later message for a connected peer; therefore ordering is **per deliverable peer/link**, not a global total order across independently available peers.

## 7. Retry/reconnect behaviour
**PASS**

Existing parameters retained:
- Bluetooth: max 12 attempts; 1 s base, 20 s cap, 800 ms jitter.
- Wi-Fi Direct: max 10 attempts; 2 s base, 30 s cap, 1 s jitter.

Reconnect jobs are single-job/generation guarded and cancelled for intentional disconnect.

## 8. Multi-peer behaviour
**PARTIAL**

Session and queue state are peer scoped. A Bluetooth message with explicit receiver Peer B will remain queued rather than route over Peer A's active Bluetooth connection. ACK session bookkeeping uses packet sender identity rather than UI selection.

Current Android Bluetooth RFCOMM and Wi-Fi Direct implementations each maintain one active physical link; true simultaneous three-phone transport fan-out requires a future multi-socket transport layer and is not claimed by N2.

## 9. Frontend changes
**PASS**

Only message delivery state presentation was changed. No network dashboard or raw exceptions were added.

## 10. Tests added
**PASS**

- `StoreAndForwardIsolationTest`: peer/message/sequence ACK isolation, wrong-peer ACK, duplicate ACK, stable retry identity, lost-ACK logical identity.
- `RoomMessageRepositoryTest`: peer-specific interrupted-transmission recovery.

Existing reconnect/disconnect/inbound dedup tests cover disconnect, retry policy, startup recovery, duplicate packet handling, duplicate ACK, and peer isolation.

## 11. Test results
**PASS**

`./gradlew :app:testDebugUnitTest --no-daemon`

**390 passed, 0 failed.**

## 12. Build result
**PASS**

`./gradlew :app:assembleDebug --no-daemon` completed successfully.

- APK: `app/build/outputs/apk/debug/app-debug.apk`
- SHA-256: `be303af6522359cd534ce428a9104aa4445be65cbdd9df16bddfa8a80a08077b`

## 13. Physical validation result
**PARTIAL**

The N2 APK installed and launched on the Realme RMX3782 (SKIN ~41.7°C, thermal status 0). Existing Speak UI and language controls rendered after installation.

Real store-and-forward validation is **not** complete: it requires Phone A and Phone B to break and restore an actual local link, verify pending/resend/ACK, and confirm exactly-once UI/TTS delivery. No such multi-device evidence is claimed.

## 14. Known limitations
- One active RFCOMM and one active Wi-Fi Direct TCP link; no claim of arbitrary simultaneous physical peers.
- Bluetooth MAC addressing and PacketV2 app-device IDs differ in legacy flows, hence ACK uses sender identity first and a same-transport compatibility fallback.
- Retry exhaustion remains terminal `FAILED` under the existing policy; user can manually retry.
- No guarantee under permanent peer/radio failure.

## 15. Files changed
- `app/src/main/java/com/vokie/communication/PendingAckRegistry.kt`
- `app/src/main/java/com/vokie/communication/TransportManager.kt`
- `app/src/main/java/com/vokie/communication/OutboundMessageProcessor.kt`
- `app/src/main/java/com/vokie/data/local/VokieDatabase.kt`
- `app/src/main/java/com/vokie/data/MessageRepository.kt`
- `app/src/main/java/com/vokie/VokieApplication.kt`
- `app/src/main/java/com/vokie/ui/communication/CommunicationViewModel.kt`
- `app/src/main/java/com/vokie/ui/components/MessageBubble.kt`
- `app/src/test/java/com/vokie/communication/StoreAndForwardIsolationTest.kt`
- `app/src/test/java/com/vokie/data/RoomMessageRepositoryTest.kt`

## 16. Git commit hash
**PASS** — implementation commit `215e7b0`
