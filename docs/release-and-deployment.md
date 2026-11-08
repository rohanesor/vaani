# iTantra release and deployment

AWS is a distribution boundary only. The Android communication path does not call AWS, S3, CloudFront, or any backend.

## One-time Android signing setup

Generate a production keystore on a secure workstation and store it in an approved secret manager:

```bash
keytool -genkeypair -v -keystore vokie-release.jks \
  -alias vokie -keyalg RSA -keysize 4096 -validity 10000
base64 -w 0 vokie-release.jks > vokie-release.jks.b64
```

Configure these GitHub Actions secrets. Never commit the keystore or passwords:

- `ANDROID_KEYSTORE_BASE64`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

The release workflow refuses to run when any is missing. The keystore is decoded only into the ephemeral runner.

## AWS infrastructure

Requirements: AWS CLI, an AWS account, and permission to create CloudFormation/IAM resources. From `infrastructure/cloudformation/vokie-download.yaml`, create the stack with a globally unique bucket name:

```bash
aws cloudformation deploy \
  --template-file infrastructure/cloudformation/vokie-download.yaml \
  --stack-name vokie-download \
  --parameter-overrides BucketName=downloads.example-vokie-name \
  --capabilities CAPABILITY_NAMED_IAM
aws cloudformation describe-stacks --stack-name vokie-download
```

The stack creates a retained, encrypted, versioned private S3 bucket, CloudFront Origin Access Control, an HTTPS-redirecting distribution, and a bucket policy that permits reads only from that distribution.

Create a GitHub OIDC provider in IAM (`token.actions.githubusercontent.com`) and a deployment role using the templates in `infrastructure/iam/`. Replace the placeholders with the AWS account, bucket, and distribution values. The trust policy is restricted to `rohanesor/vokie` release tags (`v*`). Attach only the generated deployment policy; do not grant AdministratorAccess.

## GitHub configuration

Repository/environment configuration:

- Secret `AWS_DEPLOY_ROLE_ARN`
- Variables `AWS_REGION`, `VOKIE_BUCKET`, `VOKIE_CLOUDFRONT_DISTRIBUTION_ID`
- Android signing secrets above
- Variable `VOKIE_MODELS_BUCKET`: private model bucket name
- Variable `VOKIE_MODELS_KEY`: `models/v1.0.0/vokie-models-v1.0.0.tar.zst`
- Variable `VOKIE_MODELS_SHA256_KEY`: `models/v1.0.0/vokie-models-v1.0.0.sha256`

The release job uses GitHub OIDC and has `id-token: write`; no long-lived AWS access key is used.

## Bundled offline model archive

Production builds are self-contained. The protected archive is never a GitHub asset or secret. Create the isolated private bucket with `infrastructure/cloudformation/vokie-models.yaml`; it has public access blocked and is not connected to CloudFront. The required object path is `s3://<VOKIE_MODELS_BUCKET>/models/v1.0.0/vokie-models-v1.0.0.tar.zst`, with its companion checksum at `s3://<VOKIE_MODELS_BUCKET>/models/v1.0.0/vokie-models-v1.0.0.sha256`.

The `.tar.zst` must contain `models/manifest.json`, `stt/ggml-tiny-q5_1.bin`, and `model.onnx` plus `tokens.txt` for each of `eng`, `hin`, `guj`, `mar`, `kan`, `mal`, `tam`, `tel`, `ory`, and `ben`. The manifest lists an exact SHA-256 and byte size for every one of those 21 files. Upload only after independent verification:

```bash
sha256sum vokie-models-v1.0.0.tar.zst > vokie-models-v1.0.0.sha256
aws s3 cp vokie-models-v1.0.0.tar.zst s3://PRIVATE_BUCKET/models/v1.0.0/
aws s3 cp vokie-models-v1.0.0.sha256 s3://PRIVATE_BUCKET/models/v1.0.0/
```

`scripts/stage-bundled-models.py` rejects an incomplete or mismatched archive before Gradle runs, then stages only Whisper and English TTS into the base APK while retaining the full manifest. The OIDC role reads the archive only in the ephemeral release runner. On first launch iTantra atomically extracts and re-verifies base assets to private storage.

The other nine verified TTS packs are user-confirmed one-time Wi-Fi downloads. Publish their individually checksummed files from an extracted verified archive with `scripts/publish-language-packs.sh PRIVATE_MODELS_BUCKET extracted/models`. The app downloads only from the configured `BuildConfig.MODEL_CDN_BASE_URL`, validates size and SHA-256 against its bundled manifest, then atomically installs the pack into private storage. Speech inference never requires a network connection.

## Release flow

```bash
git checkout main
git pull
git tag v0.1.0
git push origin v0.1.0
```

The tag workflow tests, lints, assembles the signed release, computes SHA-256, publishes immutable `downloads/v0.1.0/` artifacts and mutable `downloads/latest/` aliases, uploads the static website, invalidates CloudFront, and creates a GitHub Release.

Versioned artifacts are never overwritten. The workflow fails if the versioned APK already exists.

## Local deployment verification

With AWS credentials that are intentionally allowed to deploy:

```bash
AWS_REGION=... VOKIE_BUCKET=... VOKIE_CLOUDFRONT_DISTRIBUTION_ID=... \
  scripts/deploy-aws.sh release/Vokie-v0.1.0.apk 0.1.0 "$(git rev-parse HEAD)"
```

This script validates the APK name, calculates SHA-256, refuses an existing versioned object, uploads the version and latest aliases, generates website `release.json`, uploads the site, invalidates CloudFront, and verifies both website and APK objects.

No AWS deployment is considered successful until the script exits zero and the CloudFront URL serves the uploaded files.
