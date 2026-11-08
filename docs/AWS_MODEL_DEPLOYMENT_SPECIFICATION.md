# AWS Model Deployment & Infrastructure Specification

**Product:** iTantra — Indian Multilingual Emergency Communication Transceiver  
**Phase:** 0.3 — AWS Model Deployment  
**Repository:** ohanesor/vokie  
**Date:** September 2026  

---

## 1. Critical SIH Architectural Invariant

> [!IMPORTANT]
> **Zero Cloud Runtime Dependency**  
> The core iTantra emergency transceiver pipeline runs **100% offline** on-device:
>
> \text{Microphone} \longrightarrow \text{Offline STT} \longrightarrow \text{Offline NLLB/CT2} \longrightarrow \text{PacketV2} \longrightarrow \text{Bluetooth / Wi-Fi Direct} \longrightarrow \text{Offline TTS} \longrightarrow \text{Speaker}
>
> Under no circumstances does runtime voice transcription, translation, network packet delivery, or voice synthesis invoke cloud APIs, AWS endpoints, or internet services.

AWS serves exclusively as **distribution and release infrastructure**, never as an online inference or coordination backend.

---

## 2. Complete Model Inventory & Licensing Audit

| Model / Artifact | Purpose | Language(s) | Runtime / Framework | Size | License | Distribution & AWS Role | Status |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Whisper Tiny (q5_1)** | Offline STT fallback | Multilingual (En, Hi, etc.) | whisper.cpp (native ARM64) | 32.15 MB | MIT | Bundled in base APK | ✅ BUNDLED OFFLINE |
| **SenseVoice-Small (int8)** | Primary STT engine | Indic + 50+ languages | sherpa-onnx ONNX Runtime | ~140 MB | Apache 2.0 | Optional offline staging | ✅ APPROVED PROTOTYPE |
| **CT2 / NLLB-200 600M** | Offline Translation | 10 Indian Languages | ctranslate2 (native ARM64) | ~619.7 MB | CC-BY-NC-4.0 | Staged in local app storage | ⚠️ DEPLOYMENT RESTRICTED (Non-Commercial) |
| **VITS Piper (en_US)** | Default English TTS | English (eng) | sherpa-onnx / ONNX | 114.0 MB | MIT | Bundled in base APK | ✅ BUNDLED OFFLINE |
| **VITS MMS Hindi (hin)** | Hindi Speech Synthesis | Hindi | sherpa-onnx / ONNX | 63.1 MB | CC-BY-NC-4.0 | Optional one-time Wi-Fi pack | ⚠️ DEPLOYMENT RESTRICTED / REQUIRES REVIEW |
| **VITS MMS Gujarati (guj)**| Gujarati Speech Synthesis| Gujarati | sherpa-onnx / ONNX | 114.0 MB | CC-BY-NC-4.0 | Optional one-time Wi-Fi pack | ⚠️ DEPLOYMENT RESTRICTED / REQUIRES REVIEW |
| **VITS MMS Marathi (mar)** | Marathi Speech Synthesis | Marathi | sherpa-onnx / ONNX | 114.0 MB | CC-BY-NC-4.0 | Optional one-time Wi-Fi pack | ⚠️ DEPLOYMENT RESTRICTED / REQUIRES REVIEW |
| **VITS MMS Kannada (kan)** | Kannada Speech Synthesis | Kannada | sherpa-onnx / ONNX | 114.0 MB | CC-BY-NC-4.0 | Optional one-time Wi-Fi pack | ⚠️ DEPLOYMENT RESTRICTED / REQUIRES REVIEW |
| **VITS MMS Malayalam (mal)**| Malayalam Speech Synth | Malayalam | sherpa-onnx / ONNX | 114.1 MB | CC-BY-NC-4.0 | Optional one-time Wi-Fi pack | ⚠️ DEPLOYMENT RESTRICTED / REQUIRES REVIEW |
| **VITS MMS Tamil (	am)**   | Tamil Speech Synthesis   | Tamil | sherpa-onnx / ONNX | 114.0 MB | CC-BY-NC-4.0 | Optional one-time Wi-Fi pack | ⚠️ DEPLOYMENT RESTRICTED / REQUIRES REVIEW |
| **VITS MMS Telugu (	el)**  | Telugu Speech Synthesis  | Telugu | sherpa-onnx / ONNX | 114.0 MB | CC-BY-NC-4.0 | Optional one-time Wi-Fi pack | ⚠️ DEPLOYMENT RESTRICTED / REQUIRES REVIEW |
| **VITS MMS Odia (ory)**    | Odia Speech Synthesis    | Odia | sherpa-onnx / ONNX | 114.0 MB | CC-BY-NC-4.0 | Optional one-time Wi-Fi pack | ⚠️ DEPLOYMENT RESTRICTED / REQUIRES REVIEW |
| **VITS MMS Bengali (en)** | Bengali Speech Synthesis | Bengali | sherpa-onnx / ONNX | 114.0 MB | CC-BY-NC-4.0 | Optional one-time Wi-Fi pack | ⚠️ DEPLOYMENT RESTRICTED / REQUIRES REVIEW |

---

## 3. Justified AWS Role

### What AWS IS Used For:
1. **Private Build Storage (Isolated S3 Bucket)**:
   - Configured via infrastructure/cloudformation/vokie-models.yaml.
   - Stores the release model archive models/v1.0.0/vokie-models-v1.0.0.tar.zst and SHA-256 verification hash.
   - S3 public access is completely blocked (BlockPublicAcls, BlockPublicPolicy, RestrictPublicBuckets).
   - Ephemeral GitHub Actions release runners access this bucket via short-lived OIDC tokens (id-token: write), staging models into signed release APKs without checking large binary blobs into git.
2. **Release Artifact & Web Distribution (CloudFront + S3)**:
   - Configured via infrastructure/cloudformation/vokie-download.yaml.
   - Hosts static landing page (website/index.html, website/release.json), signed release APKs (downloads/vX.Y.Z/iTantra-vX.Y.Z.apk), and SHA-256 hashes.
   - Origin Access Control (OAC) ensures the S3 bucket is accessible **only** through CloudFront HTTPS.
3. **Optional One-Time Wi-Fi Language Pack Acquisition**:
   - For users wishing to add non-English voices (Hindi, Tamil, Marathi, etc.), ModelDownloadManager fetches the individual pack from BuildConfig.MODEL_CDN_BASE_URL.
   - The file is checksum-verified against the local bundled manifest.json before installation and stored in context.filesDir/models/tts/<lang>. Once downloaded, speech synthesis runs 100% offline.

### What AWS is NOT Used For:
- ❌ No cloud speech recognition (no AWS Transcribe).
- ❌ No cloud machine translation (no AWS Translate).
- ❌ No cloud speech synthesis (no AWS Polly).
- ❌ No always-on compute (no EC2 instances, no ECS tasks, no SageMaker endpoints, no load balancers).
- ❌ No telemetry or tracking servers.

---

## 4. License Compliance & Redistribution Policy

- **Meta MMS and Meta NLLB-200**: Both are released by Meta AI under **CC-BY-NC-4.0** (Creative Commons Attribution-NonCommercial 4.0 International).
- **Prohibition on Commercial Exposure**: These artifacts cannot be commercially distributed or sold.
- **Redistribution Status**: Marked as DEPLOYMENT RESTRICTED / REQUIRES REVIEW.
  - For academic, non-commercial emergency SIH demonstration, private distribution under CC-BY-NC-4.0 attribution is permitted.
  - Public, commercial deployment to public CDN buckets without access authorization or licensing clearance is prohibited.

---

## 5. Security & Cost Control

### Security Architecture:
- **Zero Hardcoded Credentials**: No AWS_ACCESS_KEY_ID or AWS_SECRET_ACCESS_KEY exist in the repository or workflow YAMLs.
- **GitHub Actions OIDC**: Uses AWS IAM Identity Provider for 	oken.actions.githubusercontent.com matching repo ohanesor/vokie.
- **Local Environment**: AWS credentials/configuration unavailable (reported truthfully; no simulated credentials).

### Cost Control:
- **Resource Types**: AWS S3 Standard + AWS CloudFront (PriceClass_100).
- **Compute Cost**: **.00 / month** (zero active servers or containers).
- **Storage & Egress Cost**: Pennies per gigabyte stored; standard CloudFront free tier covers 1 TB/month transfer.
- **Teardown**: CloudFormation stacks can be decommissioned immediately with:
  `ash
  aws cloudformation delete-stack --stack-name itantra-downloads
  aws cloudformation delete-stack --stack-name itantra-models
  `

---

## 6. Offline Verification Summary

- **STT Offline**: ✅ PASS (Whisper and SenseVoice run completely on device).
- **Translation Offline**: ✅ PASS (CT2 NLLB-200 runs on local CPU/INT8).
- **TTS Offline**: ✅ PASS (Sherpa-ONNX MMS / Piper runs on local CPU/ONNX).
- **Communication Offline**: ✅ PASS (PacketV2 over RFCOMM Bluetooth / Wi-Fi Direct with local Room persistence).
