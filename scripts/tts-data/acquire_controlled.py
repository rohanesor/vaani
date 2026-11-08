#!/usr/bin/env python3
"""Plan or execute a bounded IndicVoices-R acquisition.

This tool is deliberately fail-closed. Without --approve-acquisition it only
calls official metadata endpoints and performs no audio request. The approval
path requires an operator-supplied, reviewed record manifest and byte budget;
it never recursively downloads a dataset or an archive.
"""
import argparse
import hashlib
import json
import os
import random
import shutil
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from collections import Counter
from datetime import datetime, timezone
from pathlib import Path

DATASET = "ai4bharat/indicvoices_r"
REVISION = "5f4495c91d500742a58d1be2ab07d77f73c0acf8"
LANGUAGES = {"hi": "Hindi", "gu": "Gujarati", "mr": "Marathi", "kn": "Kannada", "ml": "Malayalam", "ta": "Tamil", "te": "Telugu", "or": "Odia", "bn": "Bengali"}
SPLITS = {"train", "test"}
DENIED_FIELDS = {"audio", "audio.bytes", "audio.path"}
REQUIRED_RECORD_FIELDS = {"source_file", "source_record_id", "language", "speaker_id", "gender", "duration", "scenario", "task_name", "original_text", "normalized_text"}


def api_json(endpoint, params, token):
    query = urllib.parse.urlencode(params)
    request = urllib.request.Request(
        f"https://datasets-server.huggingface.co/{endpoint}?{query}",
        headers={"Authorization": f"Bearer {token}", "Accept": "application/json"},
    )
    with urllib.request.urlopen(request, timeout=120) as response:
        return json.load(response)


def parse_csv(value, allowed, name):
    values = [x.strip().lower() for x in value.split(",") if x.strip()]
    if not values or any(x not in allowed for x in values):
        raise ValueError(f"{name} must contain only: {','.join(sorted(allowed))}")
    return list(dict.fromkeys(values))


def bytes_for_hours(hours, rate=48000, channels=1, width=2):
    return int(hours * 3600 * rate * channels * width)


def check_disk(path, required):
    usage = shutil.disk_usage(path)
    if usage.free < required:
        raise RuntimeError(f"insufficient disk space: need {required} bytes, have {usage.free}")


def load_plan(path):
    with path.open(encoding="utf-8") as stream:
        value = json.load(stream)
    if not isinstance(value, dict) or not isinstance(value.get("records"), list):
        raise ValueError("manifest must be a JSON object with a records array")
    return value


def deterministic_order(records, seed):
    """Return a reproducible order without exposing or changing record data."""
    ordered = list(records)
    random.Random(seed).shuffle(ordered)
    return ordered


def speaker_disjoint(records):
    by_split = {}
    for record in records:
        by_split.setdefault(record["split"], set()).add((record["language"], record["speaker_id"]))
    names = sorted(by_split)
    return all(not (by_split[left] & by_split[right]) for i, left in enumerate(names) for right in names[i + 1:])


def validate_records(records, languages, splits):
    seen = set()
    for index, record in enumerate(records):
        missing = REQUIRED_RECORD_FIELDS - set(record)
        if missing:
            raise ValueError(f"record {index} missing fields: {sorted(missing)}")
        language = str(record["language"]).lower()
        if language not in languages:
            raise ValueError(f"record {index} language is not selected: {language}")
        if record.get("split") not in splits:
            raise ValueError(f"record {index} split is not selected: {record.get('split')}")
        identity = (record["source_file"], str(record["source_record_id"]))
        if identity in seen:
            raise ValueError(f"duplicate source record: {identity}")
        seen.add(identity)
        if not record.get("speaker_id"):
            raise ValueError(f"record {index} has empty speaker_id")
        if not isinstance(record["duration"], (int, float)) or record["duration"] <= 0:
            raise ValueError(f"record {index} has invalid duration")
    return seen


def build_parser():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--dataset", default=DATASET)
    parser.add_argument("--revision", default=REVISION)
    parser.add_argument("--languages", default=",".join(LANGUAGES))
    parser.add_argument("--splits", default="train,test")
    parser.add_argument("--max-hours-per-language", type=float, default=10.0)
    parser.add_argument("--max-bytes", type=int, default=0, help="hard source-byte ceiling; mandatory for approval")
    parser.add_argument("--output", type=Path, required=True)
    parser.add_argument("--manifest", type=Path, required=True, help="reviewed selection manifest; never an audio directory")
    parser.add_argument("--require-speaker-disjoint-splits", action="store_true")
    parser.add_argument("--no-training", action="store_true", required=True)
    parser.add_argument("--dry-run", action="store_true")
    parser.add_argument("--approve-acquisition", action="store_true")
    parser.add_argument("--seed", type=int, default=202602)
    parser.add_argument("--staging-multiplier", type=float, default=2.0)
    return parser


def main(argv=None):
    args = build_parser().parse_args(argv)
    if args.dataset != DATASET or args.revision != REVISION:
        raise SystemExit(f"refusing unpinned source; require {DATASET} at {REVISION}")
    if args.max_hours_per_language <= 0 or args.staging_multiplier < 1:
        raise SystemExit("hours and staging multiplier must be positive")
    try:
        languages = parse_csv(args.languages, set(LANGUAGES), "--languages")
        splits = parse_csv(args.splits, SPLITS, "--splits")
    except ValueError as error:
        raise SystemExit(str(error))
    if args.approve_acquisition and args.dry_run:
        raise SystemExit("--dry-run and --approve-acquisition are mutually exclusive")
    token = os.environ.get("HF_TOKEN")
    if not token:
        raise SystemExit("HF_TOKEN is required and was not found; no request made")
    if not args.approve_acquisition:
        # This mode is intentionally metadata-only. It does not call /rows or
        # dereference a Parquet/audio URL.
        plans = []
        for iso in languages:
            config = LANGUAGES[iso]
            info = api_json("info", {"dataset": args.dataset, "config": config, "revision": args.revision}, token)
            metadata = info["dataset_info"]
            for split in splits:
                split_info = metadata["splits"].get(split, {})
                plans.append({"language": iso, "split": split, "estimatedRecords": split_info.get("num_examples", "UNKNOWN"), "estimatedBytes": "UNKNOWN (duration-selected rows; source has embedded audio)", "publishedSplitBytes": split_info.get("num_bytes", "UNKNOWN"), "publishedConfigDownloadBytes": metadata.get("download_size", "UNKNOWN")})
        print("ACQUISITION PLAN (DRY RUN; zero audio acquisition)")
        print(f"Dataset: {args.dataset}\nRevision: {args.revision}\nMaximum allowed hours/language: {args.max_hours_per_language}\nMaximum allowed bytes: {args.max_bytes or 'NOT SET'}")
        for item in plans:
            print(f"{item['language']} {item['split']}: records={item['estimatedRecords']} estimated_bytes={item['estimatedBytes']}")
        print(json.dumps({"mode": "DRY_RUN", "dataset": args.dataset, "revision": args.revision, "languages": languages, "splits": splits, "maxHoursPerLanguage": args.max_hours_per_language, "maxBytes": args.max_bytes or "NOT_SET", "audioAcquired": False, "plans": plans, "durationAvailability": "UNKNOWN until approved records are acquired", "speakerAvailability": "UNKNOWN until approved records are acquired", "warning": "No /rows, Parquet, audio, or archive URL was requested."}, indent=2))
        return 0
    if not args.no_training:
        raise SystemExit("--no-training is mandatory")
    if not args.approve_acquisition or args.max_bytes <= 0:
        raise SystemExit("approval requires --approve-acquisition and a positive --max-bytes")
    if args.manifest.is_absolute() is False and ".git" in str(args.manifest):
        raise SystemExit("refusing a manifest inside .git")
    plan = load_plan(args.manifest)
    if plan.get("dataset") != args.dataset or plan.get("revision") != args.revision:
        raise SystemExit("selection manifest is not pinned to the requested source")
    validate_records(plan["records"], set(languages), set(splits))
    totals = Counter(); speakers = {iso: set() for iso in languages}; split_speakers = {}
    for record in plan["records"]:
        iso = record["language"].lower(); totals[iso] += record["duration"]; speakers[iso].add(record["speaker_id"])
        split_speakers.setdefault(record["split"], set()).add((iso, record["speaker_id"]))
    for iso, seconds in totals.items():
        if seconds > args.max_hours_per_language * 3600 + 1e-9:
            raise SystemExit(f"{iso} exceeds hour budget")
    if args.require_speaker_disjoint_splits and not speaker_disjoint(plan["records"]):
        raise SystemExit("speaker-disjoint split assertion failed")
    source_bytes = sum(int(r.get("source_bytes", 0)) for r in plan["records"])
    if source_bytes <= 0 or source_bytes > args.max_bytes:
        raise SystemExit("source bytes must be present for every selected record and fit --max-bytes")
    check_disk(args.output, int(source_bytes * args.staging_multiplier))
    raise SystemExit("approved transfer implementation requires an owner-approved audio URL/record manifest; no audio request made by this safety build")


if __name__ == "__main__":
    try:
        raise SystemExit(main())
    except (OSError, ValueError, urllib.error.HTTPError) as error:
        # Never include environment variables or request headers in errors.
        print(f"acquisition failed safely: {error}", file=sys.stderr)
        raise SystemExit(1)
