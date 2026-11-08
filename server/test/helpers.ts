import request from 'supertest';
import { loadConfig } from '../src/config.js';
import { openDb } from '../src/db.js';
import { Store } from '../src/store.js';
import { AIOrchestrator } from '../src/ai/orchestrator.js';
import { IcsCalendarProvider } from '../src/ai/actions.js';
import type { AICompletionRequest, AICompletionResult, AIProvider } from '../src/ai/types.js';
import type { SpeechRecognitionProvider, SpeechSynthesisProvider } from '../src/speech/gnani.js';
import { createApp } from '../src/app.js';
import { buildWav } from '../src/speech/wav.js';

/** Scripted fake AI provider: records requests, returns queued responses. */
export class FakeAI implements AIProvider {
  readonly name = 'fake';
  configured = true;
  calls: AICompletionRequest[] = [];
  queue: (string | Error)[] = [];
  delayMs = 0;
  isConfigured() { return this.configured; }
  async complete(req: AICompletionRequest): Promise<AICompletionResult> {
    this.calls.push(req);
    if (this.delayMs) await new Promise((res, rej) => {
      const t = setTimeout(res, this.delayMs);
      req.signal?.addEventListener('abort', () => { clearTimeout(t); rej(new Error('aborted')); });
    });
    const next = this.queue.shift() ?? 'ok';
    if (next instanceof Error) throw next;
    if (req.onText) for (const part of next.match(/.{1,5}/gs) ?? []) req.onText(part);
    return { text: next, model: 'fake-model', inputTokens: 10, outputTokens: 5, stopReason: 'end_turn' };
  }
}

export class FakeSTT implements SpeechRecognitionProvider {
  readonly name = 'fake-stt';
  configured = true;
  result = { text: 'kal chaar baje project discuss karte hain', detectedLanguage: 'hi-IN', model: 'fake', segments: 1 };
  fail: Error | null = null;
  isConfigured() { return this.configured; }
  async transcribe() { if (this.fail) throw this.fail; return this.result; }
}

export class FakeTTS implements SpeechSynthesisProvider {
  readonly name = 'fake-tts';
  isConfigured() { return true; }
  async synthesize() { return { audio: Buffer.from('ID3fake'), mime: 'audio/mpeg' }; }
}

export function setup(over: { aiRpm?: number; budget?: number; timeoutMs?: number } = {}) {
  const cfg = loadConfig({ dbPath: ':memory:', jwtSecret: 'x'.repeat(40), aiRequestsPerMinute: over.aiRpm ?? 100, aiDailyTokenBudget: over.budget ?? 1_000_000, aiTimeoutMs: over.timeoutMs ?? 5000 });
  const store = new Store(openDb(':memory:'));
  const ai = new FakeAI();
  const stt = new FakeSTT();
  const orchestrator = new AIOrchestrator(store, ai, cfg, new IcsCalendarProvider());
  const deps: Parameters<typeof createApp>[0] = { cfg, store, ai: orchestrator, stt, tts: new FakeTTS() };
  const app = createApp(deps);
  const api = request(app);
  return { cfg, store, ai, stt, orchestrator, app, api, deps };
}

export async function register(api: ReturnType<typeof request>, name: string, lang = 'en-IN') {
  const res = await api.post('/api/auth/register').send({ email: `${name.toLowerCase()}@example.com`, password: 'correct horse', displayName: name, preferredLanguage: lang });
  if (res.status !== 201) throw new Error(`register failed: ${res.status} ${JSON.stringify(res.body)}`);
  return { token: res.body.token as string, id: res.body.user.id as number, auth: { Authorization: `Bearer ${res.body.token}` } };
}

/** 16 kHz mono 16-bit sine tone WAV. */
export function toneWav(seconds: number, sampleRate = 16000): Buffer {
  const n = Math.floor(seconds * sampleRate);
  const pcm = Buffer.alloc(n * 2);
  for (let i = 0; i < n; i++) pcm.writeInt16LE(Math.round(Math.sin((i / sampleRate) * 2 * Math.PI * 220) * 12000 * (i / n)), i * 2);
  return buildWav(pcm, sampleRate, 1);
}
