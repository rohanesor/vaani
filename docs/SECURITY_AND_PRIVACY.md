# VAANI Security and Privacy

## Verified Milestone 2 controls

- Passwords are salted and hashed with Node `scrypt` (`N=16384`); plaintext passwords are not stored or returned.
- Cloud identity derives from a signed session token, never a request-body user ID.
- Tokens are HS256-signed, expire after 14 days, and carry a random server-recorded session ID.
- `auth_sessions` records session owner, expiry, creation and revocation. `POST /api/auth/logout` revokes the current session; revoked and legacy no-session tokens are rejected by protected HTTP routes and WebSocket upgrades.
- Conversation access is membership-gated. Non-members receive 404 for conversation-scoped content to reduce resource probing.
- Group member addition is owner-only. Message/audio/reaction/AI access resolves user identity from the verified session.
- SQLite foreign keys, WAL and a busy timeout are enabled. Schema migrations run transactionally using `PRAGMA user_version`.
- Provider credentials are server environment variables only. Browser and Android application source must never contain Anthropic/Gnani keys.
- AI context is authorization-scoped and optionally redacts contact details/OTPs/account-like numbers before Claude calls.

## Privacy boundaries

- Local Android nearby messages and local speech pipelines remain device-specific until an explicit opt-in cloud sync exists.
- Gnani cloud STT/TTS is not offline. Claude and Gnani live behavior has not been exercised in this environment.
- AI-generated content is a draft/proposal until the user explicitly sends or confirms it.
- Server logs must not include passwords, bearer tokens, provider keys, or raw audio/transcript content.

## Known limitations

- JWT is stored in browser localStorage; production should move to an evaluated cookie/session strategy or add hardened CSP/XSS controls before broad deployment.
- No refresh-token rotation, device-management UI, password reset email, MFA, encryption-at-rest, backup encryption, audit-log retention, or account deletion flow exists.
- In-process auth/AI rate limits and SQLite/WebSocket state are single-instance only.
- WebSocket token-in-query is retained for current web compatibility; Android must use a secure handshake/header solution before cloud release.
- Message edit/delete is sender-only and deletion is soft. Delivery acknowledgements are deliberately not implemented: WebSocket acceptance and reads are not delivery proof.
