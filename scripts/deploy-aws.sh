#!/usr/bin/env bash
set -Eeuo pipefail

# Publish one signed APK and the static site. Versioned release objects are immutable.
# Required: AWS_REGION, VOKIE_BUCKET, VOKIE_CLOUDFRONT_DISTRIBUTION_ID.
# Usage: deploy-aws.sh path/to/Vokie-v1.0.0.apk 1.0.0 <git-sha> [versionCode]

usage() { echo "Usage: $0 APK_PATH VERSION GIT_SHA [VERSION_CODE]" >&2; exit 2; }
[ "$#" -ge 3 ] || usage

apk_path=$1
version=$2
git_sha=$3
version_code=${4:-}
bucket=${VOKIE_BUCKET:?VOKIE_BUCKET is required}
region=${AWS_REGION:?AWS_REGION is required}
distribution=${VOKIE_CLOUDFRONT_DISTRIBUTION_ID:?VOKIE_CLOUDFRONT_DISTRIBUTION_ID is required}
command -v aws >/dev/null || { echo "AWS CLI is required" >&2; exit 1; }
command -v sha256sum >/dev/null || { echo "sha256sum is required" >&2; exit 1; }
command -v python3 >/dev/null || { echo "python3 is required" >&2; exit 1; }
[ -f "$apk_path" ] || { echo "APK not found: $apk_path" >&2; exit 1; }
[[ "$version" =~ ^[0-9]+\.[0-9]+\.[0-9]+$ ]] || { echo "Version must be MAJOR.MINOR.PATCH" >&2; exit 1; }
apk_file=$(basename "$apk_path")
if [[ "$apk_file" == "iTantra-v${version}.apk" ]]; then
  primary_name="iTantra-v${version}.apk"
  latest_name="iTantra-latest.apk"
elif [[ "$apk_file" == "Vokie-v${version}.apk" ]]; then
  primary_name="Vokie-v${version}.apk"
  latest_name="Vokie-latest.apk"
else
  echo "APK must be named iTantra-v${version}.apk or Vokie-v${version}.apk" >&2
  exit 1
fi

aws sts get-caller-identity --region "$region" >/dev/null
sha_file="${apk_path}.sha256"
sha256sum "$apk_path" > "$sha_file"
sha=$(awk '{print $1}' "$sha_file")
size=$(du -h "$apk_path" | awk '{print $1}')
versioned="downloads/v${version}"

# Never overwrite a versioned artifact. Re-running the same release is safe only
# after the operator removes the accidental/incomplete object intentionally.
if aws s3api head-object --bucket "$bucket" --key "$versioned/$primary_name" --region "$region" >/dev/null 2>&1; then
  echo "Refusing to overwrite immutable release: s3://$bucket/$versioned/$primary_name" >&2
  exit 1
fi

aws s3 cp "$apk_path" "s3://$bucket/$versioned/$primary_name" --region "$region" \
  --content-type application/vnd.android.package-archive --cache-control 'public,max-age=31536000,immutable'
aws s3 cp "$sha_file" "s3://$bucket/$versioned/$primary_name.sha256" --region "$region" \
  --content-type text/plain --cache-control 'public,max-age=31536000,immutable'
aws s3 cp "$apk_path" "s3://$bucket/downloads/latest/$latest_name" --region "$region" \
  --content-type application/vnd.android.package-archive --cache-control 'public,max-age=300'
aws s3 cp "$sha_file" "s3://$bucket/downloads/latest/$latest_name.sha256" --region "$region" \
  --content-type text/plain --cache-control 'public,max-age=300'

staging=$(mktemp -d)
trap 'rm -rf "$staging" "$sha_file"' EXIT
cp -R website/. "$staging/"
mkdir -p "$staging/downloads/v${version}"
release_json() {
  local path=$1
  python3 - "$path" "$version" "$git_sha" "$sha" "$size" "$version_code" <<'PY'
import json, sys
path, version, commit, sha, size, version_code = sys.argv[1:]
with open(path, 'w', encoding='utf-8') as f:
    data = {
        'version': f'v{version}',
        'versionCode': version_code if version_code else None,
        'commit': commit,
        'sha256': sha,
        'size': size,
        'date': __import__('datetime').date.today().isoformat(),
        'minAndroid': 'Android 7.0+',
        'languages': ['English', 'Hindi', 'Gujarati', 'Marathi', 'Kannada', 'Malayalam', 'Tamil', 'Telugu', 'Odia', 'Bengali'],
        'offlineModelsBundled': True,
        'internetRequiredAfterInstall': False,
        'downloadUrl': f'downloads/latest/{latest_name}'
    }
    if not data['versionCode']:
        del data['versionCode']
    json.dump(data, f, indent=2)
    f.write('\n')
PY
}

release_json "$staging/release.json"
release_json "$staging/downloads/v${version}/release.json"

aws s3 cp "$staging/downloads/v${version}/release.json" "s3://$bucket/downloads/v${version}/release.json" --region "$region" \
  --content-type application/json --cache-control 'public,max-age=300'
aws s3 sync "$staging" "s3://$bucket/" --region "$region" --exclude 'downloads/*' \
  --cache-control 'public,max-age=300' --delete
aws cloudfront create-invalidation --distribution-id "$distribution" --paths \
  '/index.html' '/release.json' '/downloads/latest/*' '/assets/*' --region "$region" >/dev/null

aws s3api head-object --bucket "$bucket" --key "$versioned/$primary_name" --region "$region" >/dev/null
aws s3api head-object --bucket "$bucket" --key "downloads/latest/$latest_name" --region "$region" >/dev/null
aws s3api head-object --bucket "$bucket" --key "release.json" --region "$region" >/dev/null
echo "Published iTantra v${version} (versionCode=${version_code:-unknown}, sha=${sha}) to s3://${bucket} and invalidated CloudFront ${distribution}."
