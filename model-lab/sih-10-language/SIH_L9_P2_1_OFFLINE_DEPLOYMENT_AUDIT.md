# Offline Deployment Audit

## Inference offline

**YES, conditionally.** `BundledModelStore` extracts packaged assets into app-private storage and verifies hash/size locally. `SherpaOnnxTtsEngine` synthesizes through local sherpa-onnx/AudioTrack. Existing Hindi/Tamil physical records demonstrate local synthesis/playback; no cloud inference path was found.

## Acquisition online

**YES in current optional delivery code.** `ModelDownloadManager` constructs `HttpURLConnection` URLs from `MODEL_CDN_BASE_URL`, requires an active unmetered network, and downloads TTS packs. This is acquisition, not inference.

## Full deployment offline

**PARTIAL.** A release with all approved assets bundled/pre-staged can install and synthesize without network. Current source assets contain only EN/HI/TA files even though the manifest has ten entries. Seven files cannot be bundled from this checkout. Final SIH delivery is not fully offline until approved assets are reproducibly packaged/pre-staged and optional CDN behavior is either excluded from the final path or clearly not required.
