/** Minimal PCM WAV parsing / slicing / waveform extraction. Only 16-bit PCM is accepted. */

export interface WavInfo { sampleRate: number; channels: number; bitsPerSample: number; dataOffset: number; dataLength: number; durationMs: number }

export class AudioFormatError extends Error {}

export function parseWav(buf: Buffer): WavInfo {
  if (buf.length < 44 || buf.toString('ascii', 0, 4) !== 'RIFF' || buf.toString('ascii', 8, 12) !== 'WAVE')
    throw new AudioFormatError('Audio must be a WAV file.');
  let off = 12;
  let fmt: { format: number; channels: number; sampleRate: number; bits: number } | null = null;
  while (off + 8 <= buf.length) {
    const id = buf.toString('ascii', off, off + 4);
    const size = buf.readUInt32LE(off + 4);
    const body = off + 8;
    if (id === 'fmt ') {
      fmt = { format: buf.readUInt16LE(body), channels: buf.readUInt16LE(body + 2), sampleRate: buf.readUInt32LE(body + 4), bits: buf.readUInt16LE(body + 14) };
    } else if (id === 'data') {
      if (!fmt) throw new AudioFormatError('WAV data chunk appears before format chunk.');
      if (fmt.format !== 1 || fmt.bits !== 16) throw new AudioFormatError('Only 16-bit PCM WAV is supported.');
      if (fmt.channels < 1 || fmt.channels > 2) throw new AudioFormatError('Only mono or stereo audio is supported.');
      if (fmt.sampleRate < 8000 || fmt.sampleRate > 48000) throw new AudioFormatError('Sample rate must be between 8 kHz and 48 kHz.');
      const dataLength = Math.min(size, buf.length - body);
      const bytesPerSec = fmt.sampleRate * fmt.channels * 2;
      return { sampleRate: fmt.sampleRate, channels: fmt.channels, bitsPerSample: 16, dataOffset: body, dataLength, durationMs: Math.round((dataLength / bytesPerSec) * 1000) };
    }
    off = body + size + (size % 2);
  }
  throw new AudioFormatError('WAV file has no audio data.');
}

export function buildWav(pcm: Buffer, sampleRate: number, channels: number): Buffer {
  const h = Buffer.alloc(44);
  h.write('RIFF', 0); h.writeUInt32LE(36 + pcm.length, 4); h.write('WAVE', 8);
  h.write('fmt ', 12); h.writeUInt32LE(16, 16); h.writeUInt16LE(1, 20); h.writeUInt16LE(channels, 22);
  h.writeUInt32LE(sampleRate, 24); h.writeUInt32LE(sampleRate * channels * 2, 28); h.writeUInt16LE(channels * 2, 32); h.writeUInt16LE(16, 34);
  h.write('data', 36); h.writeUInt32LE(pcm.length, 40);
  return Buffer.concat([h, pcm]);
}

/** Split into standalone WAV files no longer than `maxSeconds` (Gnani REST STT accepts ≤30 s). */
export function sliceWav(buf: Buffer, maxSeconds: number): Buffer[] {
  const info = parseWav(buf);
  const frame = info.channels * 2;
  const chunkBytes = Math.floor(info.sampleRate * maxSeconds) * frame;
  const pcm = buf.subarray(info.dataOffset, info.dataOffset + info.dataLength);
  const out: Buffer[] = [];
  for (let i = 0; i < pcm.length; i += chunkBytes) out.push(buildWav(Buffer.from(pcm.subarray(i, i + chunkBytes)), info.sampleRate, info.channels));
  return out;
}

/** Peak-normalised RMS envelope computed from the actual samples (values 0..1). */
export function waveform(buf: Buffer, bins = 48): number[] {
  const info = parseWav(buf);
  const samples = Math.floor(info.dataLength / 2 / info.channels);
  if (samples === 0) return new Array(bins).fill(0);
  const per = Math.max(1, Math.floor(samples / bins));
  const out: number[] = [];
  for (let b = 0; b < bins; b++) {
    let sum = 0, n = 0;
    for (let s = b * per; s < Math.min(samples, (b + 1) * per); s++) {
      const v = buf.readInt16LE(info.dataOffset + s * 2 * info.channels) / 32768;
      sum += v * v; n++;
    }
    out.push(n ? Math.sqrt(sum / n) : 0);
  }
  const peak = Math.max(...out, 1e-6);
  return out.map((v) => Math.round((v / peak) * 100) / 100);
}
