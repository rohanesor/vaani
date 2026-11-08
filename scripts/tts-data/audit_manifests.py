#!/usr/bin/env python3
"""Summarize a private JSONL TTS manifest without copying source data.

Required record fields: file, language, speaker, duration, sample_rate, format,
transcript, source, license, checksum. Input data is intentionally not committed.
"""
import argparse
import json
import statistics
from collections import Counter, defaultdict
from pathlib import Path

REQUIRED = {'file', 'language', 'speaker', 'duration', 'sample_rate', 'format', 'transcript', 'source', 'license', 'checksum'}

def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('manifest', type=Path, help='private JSONL manifest')
    parser.add_argument('--output', type=Path, required=True)
    args = parser.parse_args()
    groups = defaultdict(lambda: {'durations': [], 'speakers': Counter(), 'genders': Counter(), 'rates': Counter(), 'utterances': 0})
    missing = Counter()
    with args.manifest.open(encoding='utf-8') as stream:
        for line_no, line in enumerate(stream, 1):
            if not line.strip(): continue
            row = json.loads(line)
            absent = REQUIRED - row.keys()
            if absent: missing.update(absent); continue
            language = str(row['language']).lower()
            group = groups[language]; group['utterances'] += 1
            try: group['durations'].append(float(row['duration']))
            except (TypeError, ValueError): missing['duration'] += 1
            group['speakers'][str(row['speaker'])] += 1
            group['genders'][str(row.get('gender', 'UNKNOWN'))] += 1
            group['rates'][str(row['sample_rate'])] += 1
    result = {'schemaVersion': 1, 'languages': {}, 'missingRequiredFields': dict(missing)}
    for language, group in sorted(groups.items()):
        values = group['durations']
        result['languages'][language] = {
            'utterances': group['utterances'],
            'hours': sum(values) / 3600 if values else 'UNKNOWN',
            'averageDurationSeconds': statistics.fmean(values) if values else 'UNKNOWN',
            'medianDurationSeconds': statistics.median(values) if values else 'UNKNOWN',
            'speakers': len(group['speakers']),
            'utterancesPerSpeaker': dict(group['speakers']),
            'genderUtterances': dict(group['genders']),
            'sampleRates': dict(group['rates']),
        }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(result, indent=2, ensure_ascii=False) + '\n', encoding='utf-8')

if __name__ == '__main__': main()
