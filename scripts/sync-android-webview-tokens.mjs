#!/usr/bin/env node
// Keeps the Android WebView's design tokens identical to the web client's.
//   node scripts/sync-android-webview-tokens.mjs          copy client tokens into the Android assets
//   node scripts/sync-android-webview-tokens.mjs --check  exit 1 if they differ (use in CI)
import { readFile, writeFile } from 'node:fs/promises';
import { fileURLToPath } from 'node:url';
import path from 'node:path';

const root = path.resolve(path.dirname(fileURLToPath(import.meta.url)), '..');
const source = path.join(root, 'client/src/styles/tokens.css');
const target = path.join(root, 'app/src/main/assets/www/tokens.css');
const [a, b] = await Promise.all([readFile(source, 'utf8'), readFile(target, 'utf8').catch(() => '')]);
if (process.argv.includes('--check')) {
  if (a !== b) { console.error('Android tokens.css differs from client/src/styles/tokens.css. Run: node scripts/sync-android-webview-tokens.mjs'); process.exit(1); }
  console.log('Android tokens.css matches the web client.');
} else if (a === b) console.log('Already in sync.');
else { await writeFile(target, a); console.log('Copied client tokens.css into the Android WebView assets.'); }
