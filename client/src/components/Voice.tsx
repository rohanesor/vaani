/**
 * VAANI's voice surfaces. Every bar drawn comes from real audio: the server-computed RMS envelope
 * for stored notes, live microphone levels while recording. States shown are the real states.
 */
import { useEffect, useRef, useState } from 'react';
import { Play, Pause, Trash2, ArrowUp, Square, Mic, LoaderCircle, RotateCw, CircleAlert } from 'lucide-react';
import type { UIMessage } from '../lib/types';
import { api, ApiError, fetchBlobUrl } from '../lib/api';
import { duration, langLabel } from '../lib/format';
import { startRecording, RecorderError, type RecorderHandle } from '../lib/recorder';
import { chat } from '../state/chat';
import { Banner, Button, IconButton, Label, VoiceMark } from './ui';

function Wave({ values, progress = 0, live = false, onSeek }: { values: number[]; progress?: number; live?: boolean; onSeek?: (fraction: number) => void }) {
  return (
    <span className={`wave${live ? ' live' : ''}`} aria-hidden="true"
      onClick={onSeek ? (e) => { const r = e.currentTarget.getBoundingClientRect(); onSeek((e.clientX - r.left) / r.width); } : undefined}>
      {values.map((v, i) => <span key={i} className={i / values.length < progress ? 'on' : ''} style={{ height: `${Math.max(12, Math.round(v * 100))}%` }} />)}
    </span>
  );
}

const SPEEDS = [1, 1.5, 2];

/** Stored voice note: play/pause/seek/speed, then the transcript and its real status. */
export function VoicePlayer({ m }: { m: UIMessage }) {
  const v = m.voice!;
  const [url, setUrl] = useState<string | null>(null);
  const [state, setState] = useState<'idle' | 'loading' | 'playing' | 'paused' | 'error'>('idle');
  const [t, setT] = useState(0);
  const [speed, setSpeed] = useState(1);
  const [err, setErr] = useState('');
  const audio = useRef<HTMLAudioElement | null>(null);

  useEffect(() => () => { audio.current?.pause(); if (url) URL.revokeObjectURL(url); }, [url]);

  async function ensure(): Promise<HTMLAudioElement> {
    if (audio.current) return audio.current;
    setState('loading');
    const u = await fetchBlobUrl(`/messages/${m.id}/audio`);
    setUrl(u);
    const a = new Audio(u); audio.current = a;
    a.playbackRate = speed;
    a.ontimeupdate = () => setT(a.currentTime * 1000);
    a.onplay = () => setState('playing');
    a.onpause = () => setState('paused');
    a.onended = () => { setState('idle'); setT(0); };
    return a;
  }
  async function toggle() {
    if (m.local) return;
    try { const a = await ensure(); if (state === 'playing') a.pause(); else await a.play(); }
    catch (e) { setErr((e as Error).message); setState('error'); }
  }
  const seek = async (fraction: number) => {
    try { const a = await ensure(); a.currentTime = (fraction * v.durationMs) / 1000; setT(fraction * v.durationMs); } catch { /* shown on play */ }
  };
  const cycleSpeed = () => { const next = SPEEDS[(SPEEDS.indexOf(speed) + 1) % SPEEDS.length]; setSpeed(next); if (audio.current) audio.current.playbackRate = next; };
  const retry = () => api(`/messages/${m.id}/transcribe`, { method: 'POST', json: {} }).catch((e: ApiError) => setErr(e.message));
  const status = m.local === 'pending' ? 'Sending…' : state === 'playing' ? 'Playing' : state === 'paused' ? 'Paused' : null;

  return (
    <div className="voice-note">
      <div className={`voice-strip${state === 'playing' ? ' playing' : ''}`}>
        <button className="voice-disc" onClick={toggle} disabled={!!m.local || state === 'loading'} aria-label={state === 'playing' ? 'Pause voice note' : 'Play voice note'}>
          {state === 'loading' ? <LoaderCircle size={18} className="spin" /> : state === 'playing' ? <Pause size={18} /> : <Play size={18} />}
        </button>
        <div className="voice-track">
          <Wave values={v.waveform} progress={t / v.durationMs} onSeek={m.local ? undefined : seek} />
          <input type="range" className="voice-seek" min={0} max={v.durationMs} step={100} value={t} onChange={(e) => void seek(Number(e.target.value) / v.durationMs)}
            aria-label="Position" aria-valuetext={`${duration(t)} of ${duration(v.durationMs)}`} disabled={!!m.local} />
        </div>
        <span className="voice-time">{state === 'idle' ? duration(v.durationMs) : `${duration(t)} / ${duration(v.durationMs)}`}</span>
        <button className="voice-speed" onClick={cycleSpeed} disabled={!!m.local} aria-label={`Playback speed ${speed}×`}>{speed}×</button>
      </div>
      {status && <span className="sr-only" role="status">{status}</span>}
      {state === 'error' && <p className="inline-state error"><CircleAlert size={13} aria-hidden="true" /> {err}</p>}
      <div className="transcript" aria-live="polite">
        {m.local === 'pending' && <p className="inline-state"><LoaderCircle size={13} className="spin" aria-hidden="true" /> Uploading</p>}
        {v.transcriptStatus === 'pending' && <p className="inline-state voice"><VoiceMark size={13} live /> Transcribing…</p>}
        {v.transcriptStatus === 'done' && m.body && (
          <>
            <Label tone="voice">Transcript{v.transcriptLang ? ` · ${langLabel(v.transcriptLang)}` : ''}</Label>
            <p className="transcript-text" dir="auto">{m.body}</p>
          </>
        )}
        {v.transcriptStatus === 'done' && !m.body && <p className="inline-state">No speech detected.</p>}
        {v.transcriptStatus === 'failed' && <p className="inline-state error"><CircleAlert size={13} aria-hidden="true" /> {v.transcriptError ?? 'Transcription failed.'} <button className="link" onClick={retry}><RotateCw size={12} /> Retry</button></p>}
        {v.transcriptStatus === 'unavailable' && <p className="inline-state">Transcription isn’t set up on this server.</p>}
        {v.transcriptStatus === 'none' && !m.local && <button className="link small" onClick={retry}>Transcribe</button>}
      </div>
    </div>
  );
}

type Stage =
  | { s: 'starting' }
  | { s: 'recording'; paused: boolean }
  | { s: 'review'; wav: Blob; durationMs: number; levels: number[] }
  | { s: 'error'; message: string; code: string };

const LIVE_BARS = 56;

/** The voice stage: replaces the composer while recording. Recording ⇄ Paused → Review → Send. */
export function VoiceStage({ conversationId, replyTo, onDone }: { conversationId: number; replyTo: number | null; onDone: () => void }) {
  const [st, setSt] = useState<Stage>({ s: 'starting' });
  const [levels, setLevels] = useState<number[]>([]);
  const [ms, setMs] = useState(0);
  const handle = useRef<RecorderHandle | null>(null);
  const preview = useRef<HTMLAudioElement | null>(null);
  const [previewUrl, setPreviewUrl] = useState<string | null>(null);
  const [playing, setPlaying] = useState(false);
  const [pt, setPt] = useState(0);

  useEffect(() => {
    let cancelled = false;
    const peaks: number[] = [];
    startRecording((rms, elapsed) => {
      peaks.push(rms);
      if (peaks.length % 4 === 0) {
        const recent = peaks.slice(-LIVE_BARS * 4);
        const bars: number[] = [];
        for (let i = 0; i < recent.length; i += 4) bars.push(Math.max(...recent.slice(i, i + 4)));
        setLevels(bars.map((x) => Math.min(1, x * 6)));
        setMs(elapsed);
      }
    }, 5 * 60_000, () => void stop()).then((h) => {
      if (cancelled) { h.cancel(); return; }
      handle.current = h; setSt({ s: 'recording', paused: false });
    }).catch((e) => setSt({ s: 'error', message: (e as Error).message, code: e instanceof RecorderError ? e.code : 'failed' }));
    return () => { cancelled = true; handle.current?.cancel(); preview.current?.pause(); };
  }, []); // eslint-disable-line react-hooks/exhaustive-deps

  useEffect(() => () => { if (previewUrl) URL.revokeObjectURL(previewUrl); }, [previewUrl]);

  async function stop() {
    const h = handle.current; if (!h) return;
    handle.current = null;
    const out = await h.stop();
    if (out.durationMs < 500) { setSt({ s: 'error', code: 'short', message: 'That was too short to send.' }); return; }
    const peak = Math.max(...out.levels, 1e-6);
    const per = Math.max(1, Math.floor(out.levels.length / LIVE_BARS));
    const bars = Array.from({ length: LIVE_BARS }, (_, i) => Math.max(...out.levels.slice(i * per, (i + 1) * per), 0) / peak);
    setPreviewUrl(URL.createObjectURL(out.wav));
    setSt({ s: 'review', wav: out.wav, durationMs: out.durationMs, levels: bars });
  }
  function pauseResume() {
    const h = handle.current; if (!h || st.s !== 'recording') return;
    if (st.paused) h.resume(); else h.pause();
    setSt({ s: 'recording', paused: !st.paused });
  }
  function discard() { handle.current?.cancel(); handle.current = null; preview.current?.pause(); onDone(); }
  function send() { if (st.s !== 'review') return; preview.current?.pause(); chat.sendVoice(conversationId, st.wav, st.durationMs, st.levels, replyTo); onDone(); }
  function togglePreview() {
    if (!previewUrl) return;
    if (!preview.current) {
      const a = new Audio(previewUrl); preview.current = a;
      a.onended = () => { setPlaying(false); setPt(0); }; a.onpause = () => setPlaying(false); a.onplay = () => setPlaying(true);
      a.ontimeupdate = () => setPt(a.currentTime * 1000);
    }
    if (playing) preview.current.pause(); else void preview.current.play();
  }

  if (st.s === 'error') {
    return (
      <div className="voice-stage">
        <Banner tone={st.code === 'permission_denied' || st.code === 'short' ? 'warning' : 'error'} action={<Button size="sm" onClick={onDone}>Back to typing</Button>}>{st.message}</Banner>
      </div>
    );
  }

  const label = st.s === 'starting' ? 'Waiting for microphone' : st.s === 'review' ? 'Ready to send' : st.paused ? 'Paused' : 'Recording';
  return (
    <div className={`voice-stage vs-${st.s}${st.s === 'recording' && st.paused ? ' vs-paused' : ''}`} role="group" aria-label="Voice recorder">
      <div className="vs-top">
        <span className="vs-state"><span className="vs-dot" aria-hidden="true" />{label}</span>
        <span className="vs-time" aria-live="off">{st.s === 'review' ? (playing ? `${duration(pt)} / ${duration(st.durationMs)}` : duration(st.durationMs)) : duration(ms)}</span>
        <span className="sr-only" role="status">{label}</span>
      </div>
      <div className="vs-wave">
        {st.s === 'review' ? <Wave values={st.levels} progress={pt / st.durationMs} /> : <Wave values={[...Array(Math.max(0, LIVE_BARS - levels.length)).fill(0), ...levels]} live={st.s === 'recording' && !st.paused} />}
      </div>
      <div className="vs-controls">
        <IconButton label="Discard recording" onClick={discard}><Trash2 size={18} /></IconButton>
        {st.s === 'review' ? (
          <>
            <Button variant="secondary" icon={playing ? <Pause size={16} /> : <Play size={16} />} onClick={togglePreview}>{playing ? 'Pause' : 'Listen'}</Button>
            <Button variant="voice" icon={<ArrowUp size={16} />} onClick={send}>Send voice note</Button>
          </>
        ) : (
          <>
            <Button variant="secondary" disabled={st.s !== 'recording'} icon={st.s === 'recording' && st.paused ? <Mic size={16} /> : <Pause size={16} />} onClick={pauseResume}>
              {st.s === 'recording' && st.paused ? 'Resume' : 'Pause'}
            </Button>
            <Button variant="voice" disabled={st.s !== 'recording'} icon={<Square size={14} />} onClick={() => void stop()}>Done</Button>
          </>
        )}
      </div>
      <p className="vs-note">{st.s === 'review' ? 'Transcribed after sending, when transcription is on.' : 'Up to 5 minutes.'}</p>
    </div>
  );
}
