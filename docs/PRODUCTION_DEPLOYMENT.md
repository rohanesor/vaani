# VAANI Production Deployment Specification

## Runtime Requirements

| Requirement | Value |
|---|---|
| Node.js | ≥ 22.13 (for native SQLite module) |
| Architecture | x64 or arm64 |
| Memory | ≥ 512MB recommended |
| Persistent storage | Required for SQLite database |
| Network | HTTP/WebSocket ingress |

## Build and Start Commands

```bash
# Development
cd server && npm run dev

# Production build
cd server && npm run build

# Production start
cd server && NODE_ENV=production node --env-file=.env dist/index.js
```

## Required Environment Variables

| Variable | Required | Description |
|---|---|---|
| `VAANI_JWT_SECRET` | **Production** | ≥ 32 character secret for JWT signing. **Required in production.** |
| `VAANI_DB_PATH` | Optional | SQLite database path (default: `./data/vaani.db`) |
| `PORT` | Optional | HTTP server port (default: `8787`) |
| `VAANI_CORS_ORIGIN` | Optional | Allowed CORS origin (default: `http://localhost:5173`) |

## Optional Provider Configuration

| Variable | Description |
|---|---|
| `ANTHROPIC_API_KEY` | Claude API key for AI assistance |
| `VAANI_CLAUDE_MODEL` | Primary Claude model (default: `claude-sonnet-5-5`) |
| `VAANI_CLAUDE_FAST_MODEL` | Fast Claude model (default: `claude-haiku-5-5`) |
| `GNANI_API_KEY` | Gnani Vachana API key for speech services |
| `GNANI_BASE_URL` | Gnani API base URL (default: `https://api.vachana.ai`) |
| `GNANI_STT_MODEL` | Speech-to-text model (default: `prisma-v3.0`) |
| `GNANI_TTS_MODEL` | Text-to-speech model (default: `timbre-v2.5`) |
| `GNANI_TTS_VOICE` | TTS voice (default: `Kaveri`) |

## Rate Limiting and Budget Configuration

| Variable | Default | Description |
|---|---|---|
| `VAANI_AI_RPM` | `20` | AI requests per user per minute |
| `VAANI_AI_DAILY_TOKENS` | `200000` | Daily token budget per user |
| `VAANI_AI_TIMEOUT_MS` | `45000` | AI request timeout in milliseconds |

## Database Initialization

The database is automatically initialized on first startup:
1. Parent directories are created if they don't exist
2. Schema migrations run in order within transactions
3. `PRAGMA user_version` tracks migration state
4. WAL mode is enabled for concurrent reads

### Migration Safety

- Migrations are additive and non-destructive
- Each migration runs in a transaction
- Failed migrations roll back completely
- Schema version is only incremented on success
- No automatic destructive migrations

### Backup Recommendations

```bash
# Hot backup (WAL mode allows concurrent reads)
sqlite3 /path/to/vaani.db ".backup '/path/to/backup.db'"

# Or checkpoint and copy
sqlite3 /path/to/vaani.db "PRAGMA wal_checkpoint(TRUNCATE);"
cp /path/to/vaani.db /path/to/backup.db
```

## Health and Readiness Endpoints

### Liveness (`GET /api/health`)

Returns `200 OK` if the server is running and can respond.

```json
{
  "ok": true,
  "product": "VAANI",
  "claude": { "configured": true, "model": "claude-sonnet-5-5", "fastModel": "claude-haiku-5-5" },
  "gnani": { "configured": true, "sttModel": "prisma-v3.0", "ttsModel": "timbre-v2.5" },
  "jwtSecret": "configured"
}
```

**Note:** Liveness does not fail if optional providers (Claude, Gnani) are unavailable.

### Readiness (`GET /api/ready`)

Returns `200 OK` if the server is ready to handle requests (database is accessible).

```json
{ "ready": true, "database": "ok" }
```

Returns `503 Service Unavailable` if database is inaccessible:

```json
{ "ready": false, "database": "unavailable" }
```

## Graceful Shutdown

The server handles `SIGTERM` and `SIGINT` signals:

1. Stops accepting new HTTP connections
2. Closes WebSocket hub (terminates all connections)
3. Closes database connection
4. Exits after 5-second timeout if connections don't drain

Example log output:
```json
{"ts":"...","level":"info","event":"shutdown_initiated","signal":"SIGTERM"}
{"ts":"...","level":"info","event":"websocket_hub_closed"}
{"ts":"...","level":"info","event":"http_server_closed"}
{"ts":"...","level":"info","event":"database_closed"}
```

## WebSocket Deployment

The server requires WebSocket support for realtime features:

- Path: `/ws?token=<jwt>`
- Protocol: Standard WebSocket (RFC 6455)
- Heartbeat: 30-second ping interval
- Max payload: 16KB

### Proxy Configuration (nginx example)

```nginx
location /ws {
    proxy_pass http://localhost:8787;
    proxy_http_version 1.1;
    proxy_set_header Upgrade $http_upgrade;
    proxy_set_header Connection "upgrade";
    proxy_read_timeout 3600s;
}
```

## Single-Instance Deployment Restriction

**The current deployment model is single-instance only.**

### Process-Local State

| Component | Storage | Multi-Instance Impact |
|---|---|---|
| Rate limiter | In-memory | Each instance has separate limits |
| WebSocket hub | In-memory | Clients on different instances can't see each other |
| Database | SQLite file | Single-writer lock |

### Why Single-Instance

1. **Rate limiting**: `SlidingWindowLimiter` is in-memory. Multiple instances would allow users to exceed limits by connecting to different instances.

2. **WebSocket fanout**: The realtime hub maintains an in-memory map of user→WebSocket connections. Messages are fanned out only to connections on the same instance.

3. **SQLite**: While WAL mode allows concurrent reads, only one writer can operate at a time. High-write workloads would contend on the database lock.

### Multi-Instance Requirements (Future)

To support multiple instances:
1. Replace in-memory rate limiter with Redis-backed sliding window
2. Add Redis pub/sub for WebSocket message fanout
3. Consider PostgreSQL or managed database for higher concurrency

**Until these are implemented, deploy as a single instance.**

## Production Configuration Validation

The server validates configuration on startup:

1. `VAANI_JWT_SECRET` is required in `NODE_ENV=production`
2. `VAANI_CORS_ORIGIN` must be a valid HTTP(S) URL
3. Invalid configuration causes immediate exit with error log

### Example Startup Failure

```json
{"ts":"...","level":"error","event":"config_invalid","error":"VAANI_JWT_SECRET is required in production"}
```

## Operational Logging

All API requests are logged in structured JSON format:

```json
{
  "ts": "2026-11-02T12:34:56.789Z",
  "level": "info",
  "event": "request",
  "requestId": "550e8400-e29b-41d4-a716-446655440000",
  "method": "POST",
  "path": "/api/conversations/:id/messages",
  "status": 201,
  "durationMs": 45
}
```

### Privacy Guarantees

- API keys are never logged
- Authorization headers are not logged
- Message bodies are not logged
- User IDs in paths are generalized to `:id`
- Error messages are normalized (no stack traces to clients)

### Request Correlation

Every API response includes `X-Request-Id` header for tracing.

## Security Checklist

- [ ] Set `VAANI_JWT_SECRET` to a cryptographically random 32+ character string
- [ ] Set `VAANI_CORS_ORIGIN` to your production domain (HTTPS)
- [ ] Configure HTTPS termination at load balancer/proxy
- [ ] Ensure `.env` file has restricted permissions (600)
- [ ] Never commit `.env` or API keys to version control
- [ ] Enable database backups
- [ ] Monitor `/api/ready` for deployment health checks

## Container Deployment Example

```dockerfile
FROM node:22-slim
WORKDIR /app
COPY server/package*.json ./
RUN npm ci --omit=dev
COPY server/dist ./dist
COPY client/dist ./client/dist
ENV NODE_ENV=production
EXPOSE 8787
HEALTHCHECK CMD curl -f http://localhost:8787/api/ready || exit 1
CMD ["node", "dist/index.js"]
```

## Platform-Specific Notes

### Kubernetes / Cloud Run

- Set readiness probe to `GET /api/ready`
- Set liveness probe to `GET /api/health`
- Use persistent volume for SQLite database
- Configure graceful shutdown timeout ≥ 10 seconds

### Fly.io / Railway

- Single-instance deployment recommended
- Mount persistent volume at `/data`
- Set `VAANI_DB_PATH=/data/vaani.db`

### Traditional VM

- Use systemd for process management
- Configure log rotation for stdout
- Schedule database backups via cron
