import http from 'node:http';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { loadConfig, describeConfig, validateProductionConfig } from './config.js';
import { openDb } from './db.js';
import { Store } from './store.js';
import { ClaudeProvider } from './ai/claude.js';
import { AIOrchestrator } from './ai/orchestrator.js';
import { IcsCalendarProvider } from './ai/actions.js';
import { GnaniSpeechRecognitionProvider, GnaniSpeechSynthesisProvider } from './speech/gnani.js';
import { createApp } from './app.js';
import { attachRealtime, type Hub } from './realtime.js';

// Structured logging helper (never logs secrets)
const log = (level: 'info' | 'warn' | 'error', event: string, data?: Record<string, unknown>) => {
  const entry = { ts: new Date().toISOString(), level, event, ...data };
  console[level === 'error' ? 'error' : 'log'](JSON.stringify(entry));
};

// Fail fast on invalid production configuration
const cfg = loadConfig();
const configErrors = validateProductionConfig(cfg);
if (configErrors.length > 0) {
  for (const err of configErrors) log('error', 'config_invalid', { error: err });
  process.exit(1);
}

const store = new Store(openDb(cfg.dbPath));
const claude = new ClaudeProvider({ apiKey: cfg.anthropicApiKey, model: cfg.claudeModel, fastModel: cfg.claudeFastModel, timeoutMs: cfg.aiTimeoutMs });
const gnani = { apiKey: cfg.gnaniApiKey, baseUrl: cfg.gnaniBaseUrl, sttModel: cfg.gnaniSttModel, ttsModel: cfg.gnaniTtsModel, ttsVoice: cfg.gnaniTtsVoice };
const deps = {
  cfg, store,
  ai: new AIOrchestrator(store, claude, cfg, new IcsCalendarProvider()),
  stt: new GnaniSpeechRecognitionProvider(gnani),
  tts: new GnaniSpeechSynthesisProvider(gnani),
};

const here = path.dirname(fileURLToPath(import.meta.url));
const app = createApp(deps, { staticDir: path.resolve(here, '../../client/dist') });
const server = http.createServer(app);
let hub: Hub;
(deps as { hub?: Hub }).hub = hub = attachRealtime(server, store, cfg.jwtSecret);

// Graceful shutdown
let shuttingDown = false;
const shutdown = (signal: string) => {
  if (shuttingDown) return;
  shuttingDown = true;
  log('info', 'shutdown_initiated', { signal });

  // Stop accepting new connections
  server.close(() => {
    log('info', 'http_server_closed');
  });

  // Close WebSocket connections gracefully
  hub.close();
  log('info', 'websocket_hub_closed');

  // Close database (if Store exposes close method, otherwise SQLite closes on process exit)
  if (typeof (store as { close?: () => void }).close === 'function') {
    (store as { close: () => void }).close();
    log('info', 'database_closed');
  }

  // Allow 5 seconds for graceful shutdown, then force exit
  setTimeout(() => {
    log('warn', 'shutdown_timeout', { message: 'Forcing exit after timeout' });
    process.exit(0);
  }, 5000).unref();
};

process.on('SIGTERM', () => shutdown('SIGTERM'));
process.on('SIGINT', () => shutdown('SIGINT'));

server.listen(cfg.port, () => {
  log('info', 'server_started', {
    port: cfg.port,
    nodeVersion: process.version,
    pid: process.pid,
    providers: describeConfig(cfg),
  });
});
