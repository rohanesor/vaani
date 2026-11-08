/** AI companion, translation, summaries, voice-to-action, voice notes and speech. */
import express, { Router, type Response } from 'express';
import type { Deps } from '../app.js';
import { HttpError, reqInt, reqLanguage, reqString } from '../http-utils.js';
import { AIError } from '../ai/types.js';
import type { AssistOp } from '../ai/orchestrator.js';
import { AudioFormatError, parseWav, waveform } from '../speech/wav.js';
import { SpeechError } from '../speech/gnani.js';

const toHttp = (e: unknown) => {
  if (e instanceof AIError) return new HttpError(e.status, `ai_${e.code}`, e.message, { retryable: e.retryable });
  if (e instanceof SpeechError) return new HttpError(e.status, `speech_${e.code}`, e.message);
  return e;
};

function abortOnClose(res: Response) {
  const ac = new AbortController();
  res.on('close', () => { if (!res.writableFinished) ac.abort(); });
  return ac.signal;
}

const MAX_VOICE_MS = 5 * 60_000;

export function aiRoutes(d: Deps) {
  const r = Router();
  const uid = (req: { userId?: number }) => req.userId!;

  r.get('/ai/status', (req, res) => res.json({
    ...d.ai.status(uid(req)),
    speech: { recognition: { provider: d.stt.name, configured: d.stt.isConfigured() }, synthesis: { provider: d.tts.name, configured: d.tts.isConfigured() } },
  }));

  /** Streaming companion (SSE): events context → delta* → done | error. Cancelling = closing the request. */
  r.post('/ai/assist', async (req, res) => {
    const b = req.body ?? {};
    const op = b.op as AssistOp;
    if (!['explain', 'reply', 'rewrite', 'ask'].includes(op)) throw new HttpError(400, 'invalid_request', 'Unknown AI operation.');
    const conversationId = reqInt(b.conversationId, 'conversationId');
    d.store.assertMember(conversationId, uid(req)); // fail before opening the stream
    const signal = abortOnClose(res);
    res.writeHead(200, { 'Content-Type': 'text/event-stream; charset=utf-8', 'Cache-Control': 'no-cache, no-transform', Connection: 'keep-alive', 'X-Accel-Buffering': 'no' });
    const emit = (event: string, data: unknown) => { if (!res.writableEnded) res.write(`event: ${event}\ndata: ${JSON.stringify(data)}\n\n`); };
    try {
      const out = await d.ai.assist(uid(req), {
        conversationId, op,
        messageId: b.messageId ? reqInt(b.messageId, 'messageId') : undefined,
        draft: typeof b.draft === 'string' ? b.draft : undefined,
        style: typeof b.style === 'string' ? b.style : undefined,
        instruction: typeof b.instruction === 'string' ? b.instruction : undefined,
        question: typeof b.question === 'string' ? b.question : undefined,
      }, { signal, onContext: (c) => emit('context', c), onText: (t) => emit('delta', { text: t }) });
      emit('done', { text: out.text });
    } catch (e) {
      const h = toHttp(e);
      if (h instanceof HttpError) emit('error', { code: h.code, message: h.message, details: h.details });
      else { console.error('[vaani] assist failed', e); emit('error', { code: 'internal', message: 'Something went wrong.' }); }
    }
    res.end();
  });

  r.post('/ai/translate', async (req, res) => {
    try {
      res.json(await d.ai.translate(uid(req), reqInt(req.body?.messageId, 'messageId'), reqLanguage(req.body?.targetLanguage, 'targetLanguage'), abortOnClose(res)));
    } catch (e) { throw toHttp(e); }
  });

  r.post('/ai/summarize', async (req, res) => {
    try {
      const since = req.body?.sinceMessageId ? reqInt(req.body.sinceMessageId, 'sinceMessageId') : undefined;
      res.json(await d.ai.summarize(uid(req), reqInt(req.body?.conversationId, 'conversationId'), { sinceMessageId: since, signal: abortOnClose(res) }));
    } catch (e) { throw toHttp(e); }
  });

  r.post('/ai/actions/extract', async (req, res) => {
    try {
      res.json(await d.ai.extractActions(uid(req), reqInt(req.body?.messageId, 'messageId'), String(req.body?.timezone ?? ''), abortOnClose(res)));
    } catch (e) { throw toHttp(e); }
  });

  r.post('/ai/actions/:id/confirm', async (req, res) => {
    try {
      const out = await d.ai.confirmAction(uid(req), reqInt(req.params.id, 'id'), req.body?.edits, String(req.body?.timezone ?? ''));
      if (out.result.kind === 'message') {
        const m = (out.result as { message: { conversationId: number } }).message;
        d.hub.toConversation(m.conversationId, 'message.new', m);
      }
      res.json(out);
    } catch (e) { throw toHttp(e); }
  });

  r.post('/ai/actions/:id/dismiss', (req, res) => res.json(d.ai.dismissAction(uid(req), reqInt(req.params.id, 'id'))));

  /* ---------------- voice notes ---------------- */
  r.post('/conversations/:id/voice', express.raw({ type: ['audio/wav', 'audio/x-wav', 'audio/wave'], limit: '12mb' }), (req, res) => {
    const conversationId = reqInt(req.params.id, 'id');
    d.store.assertMember(conversationId, uid(req));
    if (!Buffer.isBuffer(req.body) || req.body.length === 0) throw new HttpError(415, 'unsupported_media', 'Upload the recording as audio/wav.');
    let info;
    try { info = parseWav(req.body); } catch (e) { throw new HttpError(400, 'invalid_audio', e instanceof AudioFormatError ? e.message : 'Unreadable audio.'); }
    if (info.durationMs < 300) throw new HttpError(400, 'invalid_audio', 'Recording is too short.');
    if (info.durationMs > MAX_VOICE_MS) throw new HttpError(400, 'invalid_audio', 'Voice notes can be at most 5 minutes.');
    const clientId = typeof req.query.clientId === 'string' ? req.query.clientId.slice(0, 64) : null;
    const replyTo = req.query.replyTo ? reqInt(req.query.replyTo, 'replyTo') : null;
    const language = typeof req.query.language === 'string' && req.query.language !== 'auto' ? reqLanguage(req.query.language) : 'auto';
    const settings = d.store.getSettings(uid(req));
    const willTranscribe = settings.voiceTranscription && d.stt.isConfigured();
    const status = !settings.voiceTranscription ? 'none' : willTranscribe ? 'pending' : 'unavailable';

    const msg = d.store.insertMessage({ conversationId, senderId: uid(req), kind: 'voice', body: null, replyTo, clientId });
    if (!msg.voice) d.store.saveVoice(msg.id, { mime: 'audio/wav', durationMs: info.durationMs, waveform: waveform(req.body), audio: req.body, transcriptStatus: status });
    const full = d.store.messagesByIds([msg.id])[0];
    d.hub.toConversation(conversationId, 'message.new', full);
    res.status(201).json(full);
    if (willTranscribe && !msg.voice) void transcribeInBackground(d, full.id, req.body, language);
  });

  r.get('/messages/:id/audio', (req, res) => {
    const { mime, audio } = d.store.getVoiceAudio(reqInt(req.params.id, 'id'), uid(req));
    res.setHeader('Content-Type', mime);
    res.setHeader('Cache-Control', 'private, max-age=86400');
    res.send(Buffer.from(audio));
  });

  r.post('/messages/:id/transcribe', (req, res) => {
    const id = reqInt(req.params.id, 'id');
    const msg = d.store.getMessage(id, uid(req));
    if (msg.kind !== 'voice') throw new HttpError(400, 'invalid_request', 'Only voice notes can be transcribed.');
    if (!d.stt.isConfigured()) throw new HttpError(503, 'speech_not_configured', 'Speech recognition is not configured on this server (GNANI_API_KEY).');
    if (msg.voice?.transcriptStatus === 'pending') throw new HttpError(409, 'conflict', 'Transcription is already in progress.');
    const { audio } = d.store.getVoiceAudio(id, uid(req));
    const language = typeof req.body?.language === 'string' && req.body.language !== 'auto' ? reqLanguage(req.body.language) : 'auto';
    d.store.setTranscript(id, 'pending', null, null, null);
    const updated = d.store.messagesByIds([id])[0];
    d.hub.toConversation(msg.conversationId, 'message.updated', updated);
    res.status(202).json(updated);
    void transcribeInBackground(d, id, Buffer.from(audio), language);
  });

  /* ---------------- text-to-speech ---------------- */
  r.post('/tts', async (req, res) => {
    const text = reqString(req.body, 'text', { min: 1, max: 2000 });
    const language = reqLanguage(req.body?.language);
    try {
      const out = await d.tts.synthesize(text, language, { signal: abortOnClose(res) });
      res.setHeader('Content-Type', out.mime);
      res.send(out.audio);
    } catch (e) { throw toHttp(e); }
  });

  return r;
}

async function transcribeInBackground(d: Deps, messageId: number, wav: Buffer, language: string) {
  try {
    const out = await d.stt.transcribe(wav, language);
    d.store.setTranscript(messageId, 'done', out.text || null, out.detectedLanguage, out.text ? null : 'No speech detected.');
  } catch (e) {
    const message = e instanceof SpeechError ? e.message : 'Transcription failed.';
    if (!(e instanceof SpeechError)) console.error('[vaani] transcription failed', e);
    d.store.setTranscript(messageId, 'failed', null, null, message);
  }
  const msg = d.store.messagesByIds([messageId])[0];
  if (msg) d.hub.toConversation(msg.conversationId, 'message.updated', msg);
}
