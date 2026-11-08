/** Composition root: wires config, store, providers and routes. Dependencies are injected so tests can swap them. */
import express from 'express';
import crypto from 'node:crypto';
import fs from 'node:fs';
import path from 'node:path';
import type { VaaniConfig } from './config.js';
import { describeConfig } from './config.js';
import type { Store } from './store.js';
import type { Hub } from './realtime.js';
import { nullHub } from './realtime.js';
import type { AIOrchestrator } from './ai/orchestrator.js';
import type { SpeechRecognitionProvider, SpeechSynthesisProvider } from './speech/gnani.js';
import { requireAuth } from './auth.js';
import { errorHandler, HttpError } from './http-utils.js';
import { coreRoutes, publicRoutes } from './routes/core.js';
import { aiRoutes } from './routes/ai.js';
import { syncRoutes } from './routes/sync.js';

export interface Deps {
  cfg: VaaniConfig;
  store: Store;
  ai: AIOrchestrator;
  stt: SpeechRecognitionProvider;
  tts: SpeechSynthesisProvider;
  hub: Hub;
}

export function createApp(deps: Omit<Deps, 'hub'> & { hub?: Hub }, opts: { staticDir?: string } = {}) {
  const d = deps as Deps;
  d.hub ??= nullHub;
  const app = express();
  app.disable('x-powered-by');
  app.set('trust proxy', 'loopback');

  app.use((req, res, next) => {
    res.setHeader('X-Content-Type-Options', 'nosniff');
    res.setHeader('Referrer-Policy', 'no-referrer');
    const origin = req.headers.origin;
    if (origin && origin === d.cfg.corsOrigin) {
      res.setHeader('Access-Control-Allow-Origin', origin);
      res.setHeader('Vary', 'Origin');
      res.setHeader('Access-Control-Allow-Headers', 'Authorization, Content-Type');
      res.setHeader('Access-Control-Allow-Methods', 'GET, POST, PATCH, DELETE, OPTIONS');
      if (req.method === 'OPTIONS') { res.status(204).end(); return; }
    }
    next();
  });

  // Request correlation middleware (applies to all API requests)
  app.use('/api', (req, res, next) => {
    const requestId = crypto.randomUUID();
    const start = Date.now();
    res.setHeader('X-Request-Id', requestId);

    res.on('finish', () => {
      // Redact sensitive path segments (user IDs in paths are low-cardinality)
      const safePath = req.path.replace(/\/\d+/g, '/:id');
      const entry = {
        ts: new Date().toISOString(),
        level: res.statusCode >= 500 ? 'error' : res.statusCode >= 400 ? 'warn' : 'info',
        event: 'request',
        requestId,
        method: req.method,
        path: safePath,
        status: res.statusCode,
        durationMs: Date.now() - start,
      };
      console.log(JSON.stringify(entry));
    });

    next();
  });

  // Liveness: server is running and can respond
  app.get('/api/health', (_req, res) => res.json({ ok: true, product: 'VAANI', ...describeConfig(d.cfg) }));

  // Readiness: server is ready to handle requests (database is accessible)
  app.get('/api/ready', (_req, res) => {
    try {
      // Check database connectivity by running a simple query
      d.store.isReady();
      res.json({ ready: true, database: 'ok' });
    } catch (e) {
      res.status(503).json({ ready: false, database: 'unavailable' });
    }
  });
  app.use('/api', express.json({ limit: '64kb' }), publicRoutes(d));
  const auth = requireAuth(d.cfg.jwtSecret, (sessionId, userId) => d.store.isSessionActive(sessionId, userId));
  // express.json only parses application/json, so audio/wav uploads reach express.raw in aiRoutes untouched.
  app.use('/api', auth, express.json({ limit: '64kb' }), syncRoutes(d), aiRoutes(d), coreRoutes(d));
  app.use('/api', (_req, _res, next) => next(new HttpError(404, 'not_found', 'Unknown API route.')));

  if (opts.staticDir && fs.existsSync(opts.staticDir)) {
    app.use(express.static(opts.staticDir, { index: false, maxAge: '1h' }));
    app.get(/^(?!\/api|\/ws).*/, (_req, res) => res.sendFile(path.join(opts.staticDir!, 'index.html')));
  }
  app.use(errorHandler);
  return app;
}
