# Training data source manifests

This directory contains source indexes only. It must never contain audio, complete corpus metadata, speaker PII, credentials, or downloaded model artifacts.

The indexes record only authoritative pre-acquisition facts. Fields marked `UNKNOWN` must be filled by the reproducible audit scripts after official manifests are acquired into ignored private storage. Do not infer absent values from archive names or file sizes.

Run the scripts in `scripts/tts-data/` against private manifests/audio and retain generated reports under ignored `.research/` until their data/consent review is approved.
