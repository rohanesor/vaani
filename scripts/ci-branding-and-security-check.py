#!/usr/bin/env python3
"""
iTantra CI - Branding, Website Integrity, and Secret Safety Check
Validates:
1. Zero accidental user-visible 'Vokie' branding in UI and website surfaces.
2. Preserves expected technical identifiers (com.vokie, vokie_ct2, VokieTheme, etc.).
3. Website HTML/JSON syntax and asset reference validity.
4. Basic secret detection (AWS keys, private keys).
"""

import os
import re
import sys
import json
import html.parser
import subprocess

REPO_ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), ".."))

# -------------------------------------------------------------
# 1. Branding Regression Check
# -------------------------------------------------------------

# Allowed technical identifiers where 'vokie' is required for binary/API/asset compatibility
ALLOWED_TECHNICAL_PATTERNS = [
    re.compile(r'package\s+com\.vokie', re.IGNORECASE),
    re.compile(r'import\s+com\.vokie', re.IGNORECASE),
    re.compile(r'com\.vokie', re.IGNORECASE),
    re.compile(r'vokie_whisper', re.IGNORECASE),
    re.compile(r'vokie_ct2', re.IGNORECASE),
    re.compile(r'libvokie', re.IGNORECASE),
    re.compile(r'VokieTheme', re.IGNORECASE),
    re.compile(r'VokiePalette', re.IGNORECASE),
    re.compile(r'VokieFonts', re.IGNORECASE),
    re.compile(r'VokieColors', re.IGNORECASE),
    re.compile(r'VokieDimens', re.IGNORECASE),
    re.compile(r'LocalVokie', re.IGNORECASE),
    re.compile(r'Theme\.Vokie', re.IGNORECASE),
    re.compile(r'vokie[-_]bg[-_]base', re.IGNORECASE),
    re.compile(r'vokie[-_]logo', re.IGNORECASE),
    re.compile(r'vokie[-_]splash', re.IGNORECASE),
    re.compile(r'vokie[-_]social', re.IGNORECASE),
    re.compile(r'vokie\.db', re.IGNORECASE),
    re.compile(r'vokie_identity', re.IGNORECASE),
    re.compile(r'vokie_communication', re.IGNORECASE),
    re.compile(r'vokie_map', re.IGNORECASE),
    re.compile(r'VokieProtocol', re.IGNORECASE),
    re.compile(r'VokieApplication', re.IGNORECASE),
    re.compile(r'VokieAudioPlayer', re.IGNORECASE),
    re.compile(r'VokieAudioRecorder', re.IGNORECASE),
    re.compile(r'VokieNotificationManager', re.IGNORECASE),
    re.compile(r'VokieDatabase', re.IGNORECASE),
    re.compile(r'VokieLanguage', re.IGNORECASE),
    re.compile(r'VokieMessageId', re.IGNORECASE),
    re.compile(r'VokieLog', re.IGNORECASE),
    re.compile(r'VokiePanel', re.IGNORECASE),
    re.compile(r'VokieApp', re.IGNORECASE),
    re.compile(r'VokieTypography', re.IGNORECASE),
    re.compile(r'rohanesor/vokie', re.IGNORECASE),
    re.compile(r'Vokie-\$\{release\.version\}\.apk', re.IGNORECASE),
    re.compile(r'VOKIE\]\[', re.IGNORECASE),
    re.compile(r'VOKIE_', re.IGNORECASE),
    re.compile(r'vokie[-_]models', re.IGNORECASE),
    re.compile(r'VOKIE_BUCKET', re.IGNORECASE),
    re.compile(r'VOKIE_MODELS', re.IGNORECASE),
    re.compile(r'VOKIE_CLOUDFRONT', re.IGNORECASE),
]

def is_line_allowed_technical(line: str) -> bool:
    cleaned = line
    for pat in ALLOWED_TECHNICAL_PATTERNS:
        cleaned = pat.sub('', cleaned)
    return 'vokie' not in cleaned.lower()

def get_tracked_files(repo_root: str) -> list:
    try:
        res = subprocess.run(["git", "ls-files"], cwd=repo_root, capture_output=True, text=True, check=True)
        return [f.strip() for f in res.stdout.splitlines() if f.strip()]
    except Exception:
        file_list = []
        ignored = {'.git', '.gradle', 'build', '.cxx', 'models', 'data', '.idea', '__pycache__', 'node_modules'}
        for root, dirs, files in os.walk(repo_root):
            dirs[:] = [d for d in dirs if d not in ignored]
            for f in files:
                rel = os.path.relpath(os.path.join(root, f), repo_root).replace("\\", "/")
                file_list.append(rel)
        return file_list

def check_branding(repo_root: str, tracked_files: list) -> list:
    errors = []
    target_prefixes = ("app/src/main/res/", "app/src/main/java/com/vokie/ui/", "website/")

    for rel_path in tracked_files:
        if not rel_path.startswith(target_prefixes):
            continue
        if not rel_path.endswith(('.kt', '.xml', '.html', '.js', '.css', '.json')):
            continue
        full_path = os.path.join(repo_root, rel_path)
        if not os.path.isfile(full_path):
            continue
        with open(full_path, 'r', encoding='utf-8', errors='ignore') as fp:
            for lno, line in enumerate(fp, 1):
                if 'vokie' in line.lower():
                    if not is_line_allowed_technical(line):
                        errors.append(f"{rel_path}:{lno}: Unintentional Vokie branding: {line.strip()}")
    return errors

# -------------------------------------------------------------
# 2. Website Integrity Check
# -------------------------------------------------------------

class AssetExtractor(html.parser.HTMLParser):
    def __init__(self):
        super().__init__()
        self.assets = []

    def handle_starttag(self, tag, attrs):
        attr_dict = dict(attrs)
        for key in ['src', 'href']:
            val = attr_dict.get(key)
            if val and not val.startswith(('http://', 'https://', '#', 'mailto:')):
                self.assets.append(val.lstrip('/'))

def check_website(repo_root: str) -> list:
    errors = []
    website_dir = os.path.join(repo_root, "website")
    index_path = os.path.join(website_dir, "index.html")
    release_path = os.path.join(website_dir, "release.json")

    # 1. HTML Syntax & Assets
    if not os.path.exists(index_path):
        errors.append("website/index.html is missing")
    else:
        try:
            with open(index_path, 'r', encoding='utf-8') as f:
                content = f.read()
            extractor = AssetExtractor()
            extractor.feed(content)

            for asset in extractor.assets:
                asset_path = os.path.join(website_dir, asset)
                if not os.path.exists(asset_path):
                    errors.append(f"website/index.html references non-existent asset: {asset}")
        except Exception as e:
            errors.append(f"Failed to parse website/index.html: {e}")

    # 2. release.json
    if os.path.exists(release_path):
        try:
            with open(release_path, 'r', encoding='utf-8') as f:
                json.load(f)
        except Exception as e:
            errors.append(f"Failed to parse website/release.json: {e}")

    return errors

# -------------------------------------------------------------
# 3. Secret Safety Check
# -------------------------------------------------------------

SECRET_PATTERNS = [
    (re.compile(r'AKIA[0-9A-Z]{16}'), "Hardcoded AWS Access Key"),
    (re.compile(r'-----BEGIN (?:RSA|EC|DSA|OPENSSH|PRIVATE) KEY-----'), "Hardcoded Private Key"),
]

def check_secrets(repo_root: str, tracked_files: list) -> list:
    errors = []
    for rel_path in tracked_files:
        if rel_path.endswith(('.png', '.wav', '.so', '.bin', '.tar.zst', '.zip', '.jar', '.aar', '.log', '.dex')):
            continue
        if rel_path.startswith(('docs/', 'model-lab/')):
            continue
        full_path = os.path.join(repo_root, rel_path)
        if not os.path.isfile(full_path):
            continue
        try:
            with open(full_path, 'r', encoding='utf-8', errors='ignore') as fp:
                for lno, line in enumerate(fp, 1):
                    for pat, desc in SECRET_PATTERNS:
                        if pat.search(line):
                            if any(dummy in line.lower() for dummy in ['dummy', 'example', 'placeholder', '${{', 'test']):
                                continue
                            errors.append(f"{rel_path}:{lno}: {desc}")
        except Exception:
            pass
    return errors

# -------------------------------------------------------------
# Main Runner
# -------------------------------------------------------------

def main():
    repo_root = os.path.abspath(os.path.join(os.path.dirname(__file__), "..")) if os.path.basename(os.path.dirname(__file__)) == "scripts" else os.getcwd()

    print("==================================================")
    print("iTantra CI — Quality, Branding, and Security Audit")
    print(f"Repository Root: {repo_root}")
    print("==================================================")

    all_errors = []
    tracked_files = get_tracked_files(repo_root)
    print(f"Auditing {len(tracked_files)} version-controlled files...")

    print("[1/3] Checking for accidental visible 'Vokie' branding...")
    branding_errs = check_branding(repo_root, tracked_files)
    if branding_errs:
        for err in branding_errs:
            print(f"  ERROR: {err}")
        all_errors.extend(branding_errs)
    else:
        print("  PASS: Zero unintentional visible Vokie branding detected.")

    print("[2/3] Checking website integrity and assets...")
    website_errs = check_website(repo_root)
    if website_errs:
        for err in website_errs:
            print(f"  ERROR: {err}")
        all_errors.extend(website_errs)
    else:
        print("  PASS: Website HTML, JSON, and assets validated.")

    print("[3/3] Scanning for accidental secrets / keys...")
    secret_errs = check_secrets(repo_root, tracked_files)
    if secret_errs:
        for err in secret_errs:
            print(f"  ERROR: {err}")
        all_errors.extend(secret_errs)
    else:
        print("  PASS: No secrets detected.")

    print("==================================================")
    if all_errors:
        print(f"FAILED: {len(all_errors)} error(s) found.")
        sys.exit(1)
    else:
        print("SUCCESS: All CI validation checks passed.")
        sys.exit(0)

if __name__ == "__main__":
    main()
