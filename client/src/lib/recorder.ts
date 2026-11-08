/**
 * Microphone recorder producing 16 kHz mono 16-bit PCM WAV (the format Gnani STT accepts).
 * Live levels come from the actual captured samples, so the recording waveform is real.
 */

const TARGET_RATE = 16000;

const WORKLET = `
class VaaniCapture extends AudioWorkletProcessor {
  constructor() { super(); this.paused = false; this.port.onmessage = (e) => { this.paused = e.data === 'pause'; }; }
  process(inputs) {
    const ch = inputs[0] && inputs[0][0];
    if (ch && !this.paused) this.port.postMessage(ch.slice(0));
    return true;
  }
}
registerProcessor('vaani-capture', VaaniCapture);`;

export type RecorderErrorCode = 'unsupported' | 'permission_denied' | 'no_device' | 'failed';
export class RecorderError extends Error {
  code: RecorderErrorCode;
  constructor(code: RecorderErrorCode, message: string) { super(message); this.code = code; }
}

export interface RecorderHandle {
  pause(): void;
  resume(): void;
  stop(): Promise<{ wav: Blob; durationMs: number; levels: number[] }>;
  cancel(): void;
  readonly paused: boolean;
}

export async function startRecording(onLevel: (rms: number, elapsedMs: number) => void, maxMs = 5 * 60_000, onLimit?: () => void): Promise<RecorderHandle> {
  if (!navigator.mediaDevices?.getUserMedia || typeof AudioWorkletNode === 'undefined')
    throw new RecorderError('unsupported', 'This browser can’t record audio.');
  let stream: MediaStream;
  try {
    stream = await navigator.mediaDevices.getUserMedia({ audio: { channelCount: 1, echoCancellation: true, noiseSuppression: true, autoGainControl: true } });
  } catch (e) {
    const name = (e as DOMException).name;
    if (name === 'NotAllowedError' || name === 'SecurityError') throw new RecorderError('permission_denied', 'Microphone access is blocked. Allow it in your browser’s site settings to record voice notes.');
    if (name === 'NotFoundError') throw new RecorderError('no_device', 'No microphone was found.');
    throw new RecorderError('failed', 'Couldn’t start the microphone.');
  }
  const ctx = new AudioContext();
  const url = URL.createObjectURL(new Blob([WORKLET], { type: 'application/javascript' }));
  await ctx.audioWorklet.addModule(url);
  URL.revokeObjectURL(url);
  const src = ctx.createMediaStreamSource(stream);
  const node = new AudioWorkletNode(ctx, 'vaani-capture');
  const chunks: Float32Array[] = [];
  const levels: number[] = [];
  let samples = 0;
  let paused = false;
  let stopped = false;

  node.port.onmessage = (e: MessageEvent<Float32Array>) => {
    if (stopped) return;
    const c = e.data;
    chunks.push(c);
    samples += c.length;
    let sum = 0;
    for (let i = 0; i < c.length; i++) sum += c[i] * c[i];
    const rms = Math.sqrt(sum / c.length);
    levels.push(rms);
    const elapsed = (samples / ctx.sampleRate) * 1000;
    onLevel(rms, elapsed);
    if (elapsed >= maxMs) onLimit?.();
  };
  src.connect(node);
  // Keep the graph pulling without audible output.
  const mute = ctx.createGain(); mute.gain.value = 0; node.connect(mute).connect(ctx.destination);

  const teardown = () => { stopped = true; stream.getTracks().forEach((t) => t.stop()); void ctx.close(); };

  return {
    get paused() { return paused; },
    pause() { paused = true; node.port.postMessage('pause'); },
    resume() { paused = false; node.port.postMessage('resume'); },
    cancel() { teardown(); },
    async stop() {
      const rate = ctx.sampleRate;
      teardown();
      const pcm = new Float32Array(samples);
      let off = 0;
      for (const c of chunks) { pcm.set(c, off); off += c.length; }
      const down = resample(pcm, rate, TARGET_RATE);
      return { wav: encodeWav(down, TARGET_RATE), durationMs: Math.round((down.length / TARGET_RATE) * 1000), levels };
    },
  };
}

/** Linear-interpolation resampler with a simple box low-pass when downsampling. */
export function resample(input: Float32Array, from: number, to: number): Float32Array {
  if (from === to) return input;
  const ratio = from / to;
  const out = new Float32Array(Math.floor(input.length / ratio));
  for (let i = 0; i < out.length; i++) {
    const start = i * ratio;
    const end = Math.min(input.length, (i + 1) * ratio);
    let sum = 0, n = 0;
    for (let j = Math.floor(start); j < end; j++) { sum += input[j]; n++; }
    out[i] = n ? sum / n : 0;
  }
  return out;
}

export function encodeWav(samples: Float32Array, rate: number): Blob {
  const buf = new ArrayBuffer(44 + samples.length * 2);
  const v = new DataView(buf);
  const w = (o: number, s: string) => { for (let i = 0; i < s.length; i++) v.setUint8(o + i, s.charCodeAt(i)); };
  w(0, 'RIFF'); v.setUint32(4, 36 + samples.length * 2, true); w(8, 'WAVE'); w(12, 'fmt ');
  v.setUint32(16, 16, true); v.setUint16(20, 1, true); v.setUint16(22, 1, true); v.setUint32(24, rate, true);
  v.setUint32(28, rate * 2, true); v.setUint16(32, 2, true); v.setUint16(34, 16, true); w(36, 'data'); v.setUint32(40, samples.length * 2, true);
  for (let i = 0; i < samples.length; i++) {
    const s = Math.max(-1, Math.min(1, samples[i]));
    v.setInt16(44 + i * 2, s < 0 ? s * 0x8000 : s * 0x7fff, true);
  }
  return new Blob([buf], { type: 'audio/wav' });
}
