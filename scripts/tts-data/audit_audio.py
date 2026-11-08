#!/usr/bin/env python3
"""Audit private PCM WAV files for format, duration, clipping, and decode failures."""
import argparse
import json
import wave
from collections import Counter
from pathlib import Path

def main():
    p=argparse.ArgumentParser(); p.add_argument('audio_root',type=Path); p.add_argument('--output',type=Path,required=True); p.add_argument('--max-files',type=int,default=0); a=p.parse_args()
    files=sorted(a.audio_root.rglob('*.wav')); files=files if a.max_files <= 0 else files[:a.max_files]
    rates=Counter(); channels=Counter(); widths=Counter(); failures=[]; durations=[]; clipped=0
    for path in files:
        try:
            with wave.open(str(path),'rb') as w:
                rate=w.getframerate(); frames=w.getnframes(); width=w.getsampwidth(); channel=w.getnchannels()
                rates[rate]+=1; channels[channel]+=1; widths[width]+=1; durations.append(frames/rate if rate else 0)
                if width == 2:
                    raw=w.readframes(frames)
                    if b'\xff\x7f' in raw or b'\x00\x80' in raw: clipped += 1
        except (wave.Error, EOFError, OSError) as error: failures.append({'file':str(path),'error':str(error)})
    result={'schemaVersion':1,'filesScanned':len(files),'decodeFailures':failures,'sampleRates':dict(rates),'channels':dict(channels),'sampleWidthsBytes':dict(widths),'durationSeconds':{'total':sum(durations),'min':min(durations) if durations else 'UNKNOWN','max':max(durations) if durations else 'UNKNOWN'},'filesWithPotential16BitClipping':clipped,'limitations':'WAV PCM structural audit only; noise, transcript match, and non-16-bit clipping require separate analysis.'}
    a.output.parent.mkdir(parents=True,exist_ok=True); a.output.write_text(json.dumps(result,indent=2)+'\n',encoding='utf-8')
if __name__=='__main__': main()
