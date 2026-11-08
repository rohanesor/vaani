// VAANI Android — Appium (UiAutomator2) runtime checks for the WebView UI.
// Run against an emulator or device with the debug APK installed and an Appium server reachable:
//   APPIUM_HOST=127.0.0.1 ANDROID_UDID=emulator-5554 node tests/android/run.mjs
// Every test saves a screenshot + page source named <NN>-<PASS|FAIL|SKIP>-<test>.png, so a report can't show a
// screenshot from a different state than the one it describes.
import assert from 'node:assert/strict';
import { mkdir, writeFile } from 'node:fs/promises';
import { remote } from 'webdriverio';

const host = process.env.APPIUM_HOST ?? '172.29.80.1';
const port = Number(process.env.APPIUM_PORT ?? 4723);
const reportDir = process.env.ANDROID_UI_REPORT_DIR ?? 'tests/android/reports';
const appPackage = 'com.vaani.app';
const appActivity = 'com.vokie.MainActivity';
let driver;
let failures = 0;
let n = 0;
const summary = [];

class Skip extends Error {}
async function artifact(name) {
  await mkdir(reportDir, { recursive: true });
  const ctx = await driver.getContext().catch(() => null);
  if (ctx && ctx !== 'NATIVE_APP') await driver.switchContext('NATIVE_APP').catch(() => {});
  await driver.saveScreenshot(`${reportDir}/${name}.png`).catch(() => {});
  await writeFile(`${reportDir}/${name}.xml`, await driver.getPageSource().catch(() => ''), 'utf8');
}
async function test(name, fn) {
  const id = String(++n).padStart(2, '0');
  const slug = name.replace(/[^a-z0-9]+/gi, '-').toLowerCase();
  let status = 'PASS'; let detail = '';
  try { await fn(); }
  catch (error) { if (error instanceof Skip) { status = 'SKIP'; } else { status = 'FAIL'; failures++; } detail = error.message; }
  console.log(`${status} ${name}${detail ? `: ${detail}` : ''}`);
  await artifact(`${id}-${status}-${slug}`);
  summary.push({ id, name, status, detail, screenshot: `${id}-${status}-${slug}.png` });
}
async function clickIfPresent(selector) {
  const item = await driver.$(selector);
  if (await item.isExisting()) { await item.click(); return true; }
  return false;
}
async function resolvePermissionPrompts() {
  // Android permission controller resource ids, never coordinates.
  for (let i = 0; i < 8; i++) {
    const clicked = await clickIfPresent('id=com.android.permissioncontroller:id/permission_allow_foreground_only_button')
      || await clickIfPresent('id=com.android.permissioncontroller:id/permission_allow_button');
    if (!clicked) break;
    await driver.pause(250);
  }
}
async function web() {
  const ctx = (await driver.getContexts()).find((c) => String(c).startsWith('WEBVIEW_'));
  assert.ok(ctx, 'VAANI WebView context was not exposed');
  await driver.switchContext(ctx);
}
const mode = () => driver.execute(() => document.getElementById('app').dataset.mode);
const state = () => driver.execute(() => window.currentAppState || {});
async function shell(command, args) {
  try { await driver.switchContext('NATIVE_APP'); await driver.execute('mobile: shell', { command, args }); return true; } catch { return false; }
}
async function tab(id) { await (await driver.$(`#tabbar [data-tab="${id}"]`)).click(); await driver.waitUntil(async () => (await mode()) === id, { timeout: 5_000 }); }

try {
  driver = await remote({ hostname: host, port, path: '/', logLevel: 'error', capabilities: {
    platformName: 'Android', 'appium:automationName': 'UiAutomator2',
    'appium:udid': process.env.ANDROID_UDID ?? 'emulator-5554',
    'appium:appPackage': appPackage, 'appium:appActivity': appActivity,
    'appium:noReset': true, 'appium:chromedriverAutodownload': true, 'appium:newCommandTimeout': 120,
  }});

  await test('native startup shows the app or the permission flow', async () => {
    const pkg = await driver.getCurrentPackage();
    assert.ok([appPackage, 'com.google.android.permissioncontroller', 'com.android.permissioncontroller'].includes(pkg), `unexpected package ${pkg}`);
  });
  await test('permission prompts resolve and the WebView context is exposed', async () => {
    await resolvePermissionPrompts();
    const contexts = await driver.getContexts();
    assert.ok(contexts.includes('NATIVE_APP'));
    assert.ok(contexts.some((c) => String(c).startsWith('WEBVIEW_')), 'VAANI WebView context was not exposed');
  });
  await test('WebView loads the VAANI UI: title, bridge, icons, fonts', async () => {
    await web();
    assert.equal(await driver.getTitle(), 'VAANI — Every language. One conversation.');
    const r = await driver.execute(() => ({
      bridge: typeof window.ITantraNative === 'object' && !!window.ITantraNative,
      state: !!window.currentAppState,
      symbols: document.querySelectorAll('symbol').length,
      missing: [...document.querySelectorAll('use')].filter((u) => !document.querySelector(u.getAttribute('href'))).length,
      ligatures: document.querySelectorAll('.material-symbols-outlined').length,
    }));
    assert.ok(r.bridge, 'ITantraNative bridge missing');
    assert.ok(r.state, 'no state received from native');
    assert.ok(r.symbols >= 40 && r.missing === 0 && r.ligatures === 0, JSON.stringify(r));
  });
  await test('first launch: onboarding reaches Chats (home)', async () => {
    await web();
    if ((await mode()) === 'onboarding') {
      await (await driver.$('[data-act="ob-next"]')).click();
      assert.equal((await driver.$$('.lang-opt')).length, 10, 'expected 10 languages');
      await (await driver.$('.lang-opt[data-code="TA"]')).click();
      await (await driver.$('[data-act="ob-next"]')).click();
      await (await driver.$('#name-input')).setValue('Appium Tester');
      await (await driver.$('[data-act="ob-name"]')).click();
      await (await driver.$('[data-act="ob-finish"]')).click();
    }
    await driver.waitUntil(async () => (await mode()) === 'chats', { timeout: 5_000 });
  });
  await test('Chats shows separate Internet and Nearby indicators from native state', async () => {
    await web(); await tab('chats');
    const s = await state();
    assert.ok(s.network && ['none', 'connecting', 'limited', 'online'].includes(s.network.state), 'native network state missing: ' + JSON.stringify(s.network));
    assert.ok(s.nearby && ['discovered', 'connected', 'trusted', 'pairingIncoming', 'pairingOutgoing'].every((k) => Number.isInteger(s.nearby[k])), 'native nearby counts missing');
    assert.equal((await driver.$$('.status-row .status-pill')).length, 2);
  });
  await test('tabs: Chats, Nearby, Settings; Speak opens from the Chats button', async () => {
    await web();
    for (const t of ['nearby', 'settings', 'chats']) await tab(t);
    await (await driver.$('[data-act="open-speak"]')).click();
    await driver.waitUntil(async () => (await mode()) === 'speak', { timeout: 5_000 });
    assert.ok(await (await driver.$('#ptt')).isExisting(), 'PTT key missing');
    await driver.switchContext('NATIVE_APP'); await driver.back(); await web();
    assert.equal(await mode(), 'chats');
  });
  await test('Chats rows carry an origin badge from native state (no prototype rows)', async () => {
    await web(); await tab('chats');
    const rows = await driver.$$('.conv'); const empty = await (await driver.$('.empty')).isExisting();
    assert.ok(rows.length > 0 || empty, 'neither conversations nor empty state');
    const html = await driver.execute(() => document.body.innerHTML);
    assert.ok(!/screen-chat-sarav|>Sarav</.test(html), 'prototype data present');
    const s = await state();
    for (const c of s.conversations || []) assert.ok(['nearby', 'cloud-linked'].includes(c.origin), 'origin missing for ' + c.id);
  });
  await test('conversation opens with composer and Context sheet (needs an existing nearby conversation)', async () => {
    await web(); await tab('chats');
    const rows = await driver.$$('.conv[data-act="open-chat"]');
    if (!rows.length) throw new Skip('no local conversations on this device — pair two devices to exercise this');
    await rows[0].click();
    await driver.waitUntil(async () => (await mode()) === 'conversation', { timeout: 5_000 });
    assert.ok(await (await driver.$('#composer')).isExisting());
    await (await driver.$('.ctx-btn')).click();
    assert.equal((await driver.$$('.ctx-action')).length, 4);
  });
  await test('system back: sheet → conversation → Chats', async () => {
    await web();
    if ((await mode()) !== 'conversation') throw new Skip('no conversation open');
    await driver.switchContext('NATIVE_APP'); await driver.back(); await driver.back(); await web();
    assert.equal(await mode(), 'chats');
  });
  await test('language sheet lists 10 languages and updates native state', async () => {
    await web(); await tab('chats');
    await (await driver.$('[data-act="open-speak"]')).click();
    await (await driver.$('.lang-row-btn')).click();
    assert.equal((await driver.$$('.sheet .lang-opt')).length, 10);
    await (await driver.$('.sheet .lang-opt[data-code="TA"]')).click();
    await driver.waitUntil(async () => (await state()).language === 'TA', { timeout: 5_000, timeoutMsg: 'native language did not become TA' });
    assert.ok((await (await driver.$('.lang-row-btn')).getText()).includes('தமிழ்'));
    await driver.switchContext('NATIVE_APP'); await driver.back(); await web();
  });
  await test('Nearby: four separate counts and scan', async () => {
    await web(); await tab('nearby');
    assert.equal((await driver.$$('.stat')).length, 4);
    await (await driver.$('[data-act="scan"]')).click();
  });

  /* ---- ONLINE / OFFLINE (native). Needs: appium --allow-insecure=adb_shell. These toggle real radios. ---- */
  await test('offline: with Wi-Fi and mobile data off, native reports internet "none" and the app keeps working', async () => {
    if (!(await shell('svc', ['wifi', 'disable']))) throw new Skip('mobile: shell not allowed — start Appium with --allow-insecure=adb_shell');
    await shell('svc', ['data', 'disable']);
    try {
      await web();
      await driver.waitUntil(async () => ((await state()).network || {}).state === 'none', { timeout: 15_000, timeoutMsg: 'native did not report internet "none"' });
      await tab('chats');
      assert.ok(/No internet/.test(await (await driver.$('.status-row .status-pill')).getText()));
      const s = await state();
      assert.ok(Number.isInteger(s.nearby.connected), 'nearby counts still exported while offline');
      await tab('nearby'); await (await driver.$('[data-act="scan"]')).click();
    } finally { await artifact('offline-state'); }
  });
  await test('reconnect: native reports "online" only after Android validates internet', async () => {
    if (!(await shell('svc', ['wifi', 'enable']))) throw new Skip('mobile: shell not allowed');
    await shell('svc', ['data', 'enable']);
    await web();
    const seen = new Set();
    await driver.waitUntil(async () => { const st = ((await state()).network || {}).state; seen.add(st); return st === 'online'; },
      { timeout: 45_000, interval: 500, timeoutMsg: 'native never reported validated internet — check the emulator has internet access' });
    await tab('chats');
    assert.ok(/Online/.test(await (await driver.$('.status-row .status-pill')).getText()));
    console.log('    states seen while reconnecting:', [...seen].join(' → '));
  });
  await test('online cloud: sign-in, sync and AI (needs a configured build and VAANI_TEST_EMAIL/PASSWORD)', async () => {
    await web();
    const s = await state();
    if (!s.cloud || s.cloud.configured === false) throw new Skip('cloud is not configured in this build (aiBackendBaseUrl empty)');
    if (!process.env.VAANI_TEST_EMAIL || !process.env.VAANI_TEST_PASSWORD) throw new Skip('set VAANI_TEST_EMAIL and VAANI_TEST_PASSWORD for a test account');
    await tab('settings');
    if (!s.cloud.signedIn) {
      await (await driver.$('#cloud-email')).setValue(process.env.VAANI_TEST_EMAIL);
      await (await driver.$('#cloud-password')).setValue(process.env.VAANI_TEST_PASSWORD);
      await (await driver.$('#cloud-form button[type="submit"]')).click();
      await driver.waitUntil(async () => (await state()).cloud.signedIn, { timeout: 20_000, timeoutMsg: 'sign-in did not complete' });
    }
    if (!(await state()).cloud.syncEnabled) await (await driver.$('#sync-toggle')).click();
    await (await driver.$('[data-act="sync-now"]')).click();
    await driver.waitUntil(async () => ['Synced', 'PartialError', 'Error', 'AuthRequired'].includes((await state()).cloud.syncState), { timeout: 30_000 });
    const after = await state();
    console.log('    sync state:', after.cloud.syncState, '· cloud conversations on device:', (after.cloudConversations || []).length);
    assert.equal(after.cloud.syncState, 'Synced');
  });
  await test('Settings: speed segmented control updates native ttsSpeed', async () => {
    await web(); await tab('settings');
    await (await driver.$('[data-act="tts-speed"][data-v="1.25"]')).click();
    await driver.waitUntil(async () => Math.abs((await driver.execute(() => window.currentAppState.ttsSpeed)) - 1.25) < 0.01, { timeout: 5_000 });
    await (await driver.$('[data-act="tts-speed"][data-v="1"]')).click();
  });
  await test('dark mode follows the system theme', async () => {
    try { await driver.execute('mobile: shell', { command: 'cmd', args: ['uimode', 'night', 'yes'] }); }
    catch { throw new Skip('mobile: shell not allowed — start Appium with --allow-insecure=adb_shell'); }
    await driver.pause(800); await web();
    const theme = await driver.execute(() => document.documentElement.dataset.theme);
    await driver.execute('mobile: shell', { command: 'cmd', args: ['uimode', 'night', 'no'] }).catch(() => {});
    assert.equal(theme, 'dark');
  });
  await test('restart keeps a healthy process', async () => {
    await driver.terminateApp(appPackage);
    await driver.startActivity(appPackage, appActivity);
    await driver.waitUntil(async () => (await driver.getCurrentPackage()) === appPackage, { timeout: 8_000, interval: 250 });
  });
} finally {
  if (driver) await driver.deleteSession().catch(() => {});
  await mkdir(reportDir, { recursive: true });
  await writeFile(`${reportDir}/summary.json`, JSON.stringify({ ranAt: new Date().toISOString(), results: summary }, null, 2));
}
if (failures) process.exitCode = 1;
