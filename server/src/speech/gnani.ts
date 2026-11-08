/**
 * Gnani (Vachana) speech providers.
 * Contracts taken from https://docs.gnani.ai (Quick Start, TTS inference), checked 2026-11-02:
 *  - STT REST:  POST {base}/stt/v3  multipart: audio_file, model, language_code   header X-API-Key-ID   (≤30 s per file)
 *  - TTS REST:  POST {base}/api/v1/tts/inference  JSON: text, model, voice, language, speed, audio_config  → binary audio
 * These are cloud services: they require network access and are NOT offline capabilities.
 */
import { sliceWav, AudioFormatError } from './wav.js';

export interface TranscriptionResult { text: string; detectedLanguage: string | null; model: string; segments: number }
export interface SpeechRecognitionProvider {
  readonly name: string;
  isConfigured(): boolean;
  transcribe(wav: Buffer, language: string | 'auto', signal?: AbortSignal): Promise<TranscriptionResult>;
}

export interface SynthesisResult { audio: Buffer; mime: string }
export interface SpeechSynthesisProvider {
  readonly name: string;
  isConfigured(): boolean;
  synthesize(text: string, language: string, opts?: { voice?: string; speed?: number; signal?: AbortSignal }): Promise<SynthesisResult>;
}

/** Optional contextual language processing (e.g. Gnani Evon). Interface only — not implemented in this release. */
export interface LanguageProcessingProvider {
  readonly name: string;
  isConfigured(): boolean;
  detectLanguage(text: string): Promise<string | null>;
}

export class SpeechError extends Error {
  constructor(public code: 'not_configured' | 'invalid_audio' | 'rate_limited' | 'timeout' | 'upstream' | 'rejected', message: string, public status = 502) { super(message); }
}

export interface GnaniOptions { apiKey?: string; baseUrl: string; sttModel: string; ttsModel: string; ttsVoice: string; timeoutMs?: number; fetchImpl?: typeof fetch }

const MAX_SEGMENT_SECONDS = 29;

export class GnaniSpeechRecognitionProvider implements SpeechRecognitionProvider {
  readonly name = 'gnani-prisma';
  constructor(private o: GnaniOptions) {}
  isConfigured() { return Boolean(this.o.apiKey); }

  async transcribe(wav: Buffer, language: string | 'auto', signal?: AbortSignal): Promise<TranscriptionResult> {
    if (!this.o.apiKey) throw new SpeechError('not_configured', 'Speech recognition is not configured (GNANI_API_KEY is missing).', 503);
    let segments: Buffer[];
    try { segments = sliceWav(wav, MAX_SEGMENT_SECONDS); } catch (e) {
      throw new SpeechError('invalid_audio', e instanceof AudioFormatError ? e.message : 'Unreadable audio.', 400);
    }
    const autoSupported = this.o.sttModel.startsWith('prisma-v3');
    const langCode = language === 'auto' ? (autoSupported ? 'indic-auto' : 'en-IN') : language;
    const texts: string[] = [];
    let detected: string | null = null;
    for (const seg of segments) {
      const form = new FormData();
      form.append('audio_file', new Blob([new Uint8Array(seg)], { type: 'audio/wav' }), 'voice.wav');
      form.append('model', this.o.sttModel);
      form.append('language_code', langCode);
      const res = await this.call(`${this.o.baseUrl}/stt/v3`, { method: 'POST', headers: { 'X-API-Key-ID': this.o.apiKey }, body: form }, signal);
      const json = (await res.json().catch(() => null)) as { success?: boolean; transcript?: string; detected_language_code?: string } | null;
      if (!json || json.success === false || typeof json.transcript !== 'string') throw new SpeechError('upstream', 'Speech recognition returned an unexpected response.');
      if (json.transcript.trim()) texts.push(json.transcript.trim());
      detected ??= json.detected_language_code ?? null;
    }
    return { text: texts.join(' '), detectedLanguage: detected ?? (language === 'auto' ? null : language), model: this.o.sttModel, segments: segments.length };
  }

  private call(url: string, init: RequestInit, signal?: AbortSignal) { return gnaniFetch(this.o, url, init, signal); }
}

export class GnaniSpeechSynthesisProvider implements SpeechSynthesisProvider {
  readonly name = 'gnani-timbre';
  constructor(private o: GnaniOptions) {}
  isConfigured() { return Boolean(this.o.apiKey); }

  async synthesize(text: string, language: string, opts: { voice?: string; speed?: number; signal?: AbortSignal } = {}): Promise<SynthesisResult> {
    if (!this.o.apiKey) throw new SpeechError('not_configured', 'Speech synthesis is not configured (GNANI_API_KEY is missing).', 503);
    const t = text.trim();
    if (!t) throw new SpeechError('rejected', 'Nothing to read aloud.', 400);
    if (t.length > 2000) throw new SpeechError('rejected', 'Text is too long to read aloud (max 2000 characters).', 400);
    const speed = Math.min(1.15, Math.max(0.85, opts.speed ?? 1));
    const res = await gnaniFetch(this.o, `${this.o.baseUrl}/api/v1/tts/inference`, {
      method: 'POST',
      headers: { 'X-API-Key-ID': this.o.apiKey, 'Content-Type': 'application/json' },
      body: JSON.stringify({ text: t, model: this.o.ttsModel, voice: opts.voice ?? this.o.ttsVoice, language, speed, audio_config: { container: 'mp3', bitrate: '64k' } }),
    }, opts.signal);
    const mime = res.headers.get('content-type') ?? 'audio/mpeg';
    if (!mime.startsWith('audio/')) throw new SpeechError('upstream', 'Speech synthesis returned an unexpected response.');
    return { audio: Buffer.from(await res.arrayBuffer()), mime };
  }
}

async function gnaniFetch(o: GnaniOptions, url: string, init: RequestInit, signal?: AbortSignal): Promise<Response> {
  const timeout = AbortSignal.timeout(o.timeoutMs ?? 30_000);
  const f = o.fetchImpl ?? fetch;
  let res: Response;
  try {
    res = await f(url, { ...init, signal: signal ? AbortSignal.any([signal, timeout]) : timeout });
  } catch {
    if (timeout.aborted) throw new SpeechError('timeout', 'The speech service took too long to respond.', 504);
    throw new SpeechError('upstream', 'Could not reach the speech service.');
  }
  if (res.ok) return res;
  const body = (await res.json().catch(() => null)) as { error?: { message?: string } } | null;
  const detail = body?.error?.message ? `: ${body.error.message.slice(0, 200)}` : '';
  if (res.status === 429) throw new SpeechError('rate_limited', 'The speech service is rate-limiting requests.', 429);
  if (res.status === 401 || res.status === 403) throw new SpeechError('not_configured', `The speech service rejected the server's credentials${detail}.`, 503);
  if (res.status === 400 || res.status === 413 || res.status === 422) throw new SpeechError('rejected', `The speech service rejected the audio${detail}.`, 400);
  throw new SpeechError('upstream', `The speech service returned ${res.status}.`);
}
