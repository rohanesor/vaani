import { readFile, stat } from 'node:fs/promises';

const key = process.env.GNANI_API_KEY;
const wavPath = process.env.GNANI_LIVE_WAV;
const base = (process.env.GNANI_BASE_URL ?? 'https://api.vachana.ai').replace(/\/$/, '');
const language = process.env.GNANI_LIVE_LANGUAGE ?? 'en-IN';
const sttModel = process.env.GNANI_STT_MODEL ?? 'prisma-v3.0';
const ttsModel = process.env.GNANI_TTS_MODEL ?? 'timbre-v2.5';
const voice = process.env.GNANI_TTS_VOICE ?? 'Kaveri';

if (!key || !wavPath) {
  console.error('BLOCKED live Gnani smoke requires GNANI_API_KEY and GNANI_LIVE_WAV (a controlled WAV fixture outside git).');
  process.exit(2);
}
const audio = await readFile(wavPath);
if (!(await stat(wavPath)).isFile() || audio.length === 0) throw new Error('GNANI_LIVE_WAV is not a readable non-empty file.');
const headers = { 'X-API-Key-ID': key };
const sttStarted = performance.now();
const form = new FormData();
form.append('audio_file', new Blob([audio], { type: 'audio/wav' }), 'controlled-fixture.wav');
form.append('model', sttModel);
form.append('language_code', language);
const stt = await fetch(`${base}/stt/v3`, { method: 'POST', headers, body: form, signal: AbortSignal.timeout(30_000) });
const sttBody = await stt.json().catch(() => null);
if (!stt.ok || !sttBody || typeof sttBody.transcript !== 'string') throw new Error(`STT failed with HTTP ${stt.status}; no credential or response body is printed.`);
const sttMs = Math.round(performance.now() - sttStarted);
const ttsStarted = performance.now();
const tts = await fetch(`${base}/api/v1/tts/inference`, { method: 'POST', headers: { ...headers, 'Content-Type': 'application/json' }, body: JSON.stringify({ text: sttBody.transcript || 'VAANI speech test', model: ttsModel, voice, language, speed: 1, audio_config: { container: 'mp3', bitrate: '64k' } }), signal: AbortSignal.timeout(30_000) });
const output = Buffer.from(await tts.arrayBuffer());
if (!tts.ok || !tts.headers.get('content-type')?.startsWith('audio/') || output.length === 0) throw new Error(`TTS failed with HTTP ${tts.status}; no credential or response body is printed.`);
console.log(JSON.stringify({ result: 'LIVE-VERIFIED', language, detectedLanguage: sttBody.detected_language_code ?? null, transcriptCharacters: sttBody.transcript.length, sttProviderMs: sttMs, ttsProviderMs: Math.round(performance.now() - ttsStarted), ttsBytes: output.length }));
