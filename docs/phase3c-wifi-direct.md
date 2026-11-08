# Phase 3C Wi-Fi Direct

`WifiDirectTransport` uses Android `WifiP2pManager` for discovery, peer listing, connection lifecycle, group-owner/client socket setup, and cleanup. The group owner accepts a TCP connection on the fixed local service port; a client connects to the group-owner address. This is local Wi-Fi Direct and does not require internet or cloud relay.

TCP framing is four-byte big-endian length followed by opaque Packet v2 bytes. Reads use `readFully`, reject zero/oversized frames, and never assume one TCP read equals one packet.

Status: **IMPLEMENTED IN CODE; PHYSICAL VALIDATION NOT AVAILABLE**. Android permission state, peer discovery, group formation, reconnect behavior, and throughput require two-device testing.
