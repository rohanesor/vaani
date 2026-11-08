# SIH Git Model Lab Sync 01

## Source and target
- Source branch: `origin/sih/laptop2-model-lab`
- Source commit: `874458fa3776acc12e059c5c7bfb786926cbf49f`
- Laptop-1 target branch: `sih/laptop1-c1-c2-integration`
- Target pre-sync HEAD: `713e84b30007de56e2494e862083914a9fd40e30`
- Recovery branch: `sih/laptop1-before-model-lab-sync` at the same pre-sync HEAD.

## Outcome
No Laptop-2 file was imported. The selected lightweight evidence files already exist on Laptop 1 as tracked files with byte-identical Git blob IDs:

- `docs/model-lab-final-report.md`
- `docs/model-lab-2-language-final.md`
- `docs/model-lab-2-transfer-inventory.md`
- `docs/model-integration-plan.md`
- `model-lab/bench/out/trans_results.json`
- `model-lab/bench/out/ta_tts_results.json`
- `model-lab/bench/out/e2e_results.json`

## Skipped/collisions
`model-lab/models/MANIFEST.json` exists locally as an untracked/ignored file and was deliberately not overwritten.

The Laptop-2 commit tracks 18 benchmark WAV outputs that exactly collide with locally preserved untracked WAV files: `en_0.wav`–`en_5.wav`, `hi_0.wav`–`hi_5.wav`, and `ta_mms_0.wav`–`ta_mms_4.wav` under `model-lab/bench/out/`. They were not imported or replaced.

## Large artifacts excluded
The source commit includes model archives/binaries, generated WAVs, and six MSVC DLL files. No `.wav`, `.onnx`, `.bin`, archive, or `.dll` source object was restored. Existing local CT2/NLLB/model artifacts, MSVC runtime files, logs, and benchmark outputs remain untouched.

## Provenance
The benchmark JSON evidence listed above is byte-identical to the version in source commit `874458f`. This establishes content identity only; it does not establish that Laptop-1 local WAV files produced those results.

## Production boundary
Inspection of `git diff-tree --no-commit-id --name-status -r 874458f` found zero `app/` production Android source paths. This sync created no Android source changes and did not cherry-pick, merge, rebase, reset, clean, stash, or delete files.
