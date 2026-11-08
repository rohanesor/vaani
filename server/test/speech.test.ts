import { describe, expect, it } from 'vitest';
import { GnaniSpeechRecognitionProvider, GnaniSpeechSynthesisProvider } from '../src/speech/gnani.js';
import { parseWav, sliceWav, waveform } from '../src/speech/wav.js';
import { toneWav } from './helpers.js';

const opts = { apiKey: 'test-key', baseUrl: 'https://api.example.test', sttModel: 'prisma-v3.0', ttsModel: 'timbre-v2.5', ttsVoice: 'Kaveri' };

function fakeFetch(handler: (url: string, init: RequestInit) => Response | Promise<Response>) {
  const calls: { url: string; init: RequestInit }[] = [];
  const f = (async (url: string, init: RequestInit) => { calls.push({ url, init }); return handler(url, init); }) as unknown as typeof fetch;
  return { f, calls };
}

describe('wav utilities', () => {
  it('parses, slices and computes a waveform from real samples', () => {
    const wav = toneWav(65);
    expect(parseWav(wav)).toMatchObject({ sampleRate: 16000, channels: 1, durationMs: 65000 });
    const parts = sliceWav(wav, 29);
    expect(parts.map((p) => parseWav(p).durationMs)).toEqual([29000, 29000, 7000]);
    const w = waveform(toneWav(1));
    expect(w).toHaveLength(48);
    expect(Math.max(...w)).toBe(1);
  });
  it('rejects non-PCM16 audio', () => {
    const bad = toneWav(1); bad.writeUInt16LE(32, 34); // claim 32-bit
    expect(() => parseWav(bad)).toThrow(/16-bit/);
    expect(() => parseWav(Buffer.from('OggS....'))).toThrow();
  });
});

describe('Gnani STT', () => {
  it('posts multipart audio per ≤29 s segment with the documented fields', async () => {
    const { f, calls } = fakeFetch(() => Response.json({ success: true, transcript: 'vanakkam', detected_language_code: 'ta-IN', model: 'prisma-v3.0' }));
    const stt = new GnaniSpeechRecognitionProvider({ ...opts, fetchImpl: f });
    const out = await stt.transcribe(toneWav(40), 'auto');
    expect(calls).toHaveLength(2);
    expect(calls[0].url).toBe('https://api.example.test/stt/v3');
    expect((calls[0].init.headers as Record<string, string>)['X-API-Key-ID']).toBe('test-key');
    const form = calls[0].init.body as FormData;
    expect(form.get('model')).toBe('prisma-v3.0');
    expect(form.get('language_code')).toBe('indic-auto');
    expect(form.get('audio_file')).toBeInstanceOf(Blob);
    expect(out).toEqual({ text: 'vanakkam vanakkam', detectedLanguage: 'ta-IN', model: 'prisma-v3.0', segments: 2 });
  });
  it('maps HTTP errors and missing configuration', async () => {
    const { f } = fakeFetch(() => Response.json({ success: false, error: { type: 'rate', message: 'slow' } }, { status: 429 }));
    await expect(new GnaniSpeechRecognitionProvider({ ...opts, fetchImpl: f }).transcribe(toneWav(1), 'hi-IN')).rejects.toMatchObject({ code: 'rate_limited' });
    await expect(new GnaniSpeechRecognitionProvider({ ...opts, apiKey: undefined }).transcribe(toneWav(1), 'hi-IN')).rejects.toMatchObject({ code: 'not_configured' });
    await expect(new GnaniSpeechRecognitionProvider({ ...opts, fetchImpl: f }).transcribe(Buffer.from('nope'), 'hi-IN')).rejects.toMatchObject({ code: 'invalid_audio' });
  });
});

describe('Gnani TTS', () => {
  it('sends the documented JSON body and returns audio', async () => {
    const { f, calls } = fakeFetch(() => new Response(new Uint8Array([1, 2, 3]), { headers: { 'content-type': 'audio/mpeg' } }));
    const tts = new GnaniSpeechSynthesisProvider({ ...opts, fetchImpl: f });
    const out = await tts.synthesize('नमस्ते', 'hi-IN', { speed: 2 });
    expect(calls[0].url).toBe('https://api.example.test/api/v1/tts/inference');
    expect(JSON.parse(calls[0].init.body as string)).toEqual({ text: 'नमस्ते', model: 'timbre-v2.5', voice: 'Kaveri', language: 'hi-IN', speed: 1.15, audio_config: { container: 'mp3', bitrate: '64k' } });
    expect(out.mime).toBe('audio/mpeg');
    expect(out.audio.length).toBe(3);
  });
});
