import assert from 'node:assert/strict';
import { mkdir, writeFile } from 'node:fs/promises';
import { remote } from 'webdriverio';

const host = process.env.APPIUM_HOST ?? '172.29.80.1';
const port = Number(process.env.APPIUM_PORT ?? 4723);
const reportDir = process.env.ANDROID_UI_REPORT_DIR ?? 'tests/android/reports';
const appPackage = 'com.vaani.app';
let driver;
let failures = 0;

async function artifact(name) {
  await mkdir(reportDir, { recursive: true });
  await driver.saveScreenshot(`${reportDir}/${name}.png`).catch(() => {});
  await writeFile(`${reportDir}/${name}.xml`, await driver.getPageSource().catch(() => ''), 'utf8');
}
async function test(name, fn) {
  try { await fn(); console.log(`PASS ${name}`); }
  catch (error) { failures++; console.error(`FAIL ${name}: ${error.message}`); await artifact(name.replace(/[^a-z0-9]+/gi, '-')); }
}
async function clickIfPresent(selector) {
  const item = await driver.$(selector);
  if (await item.isExisting()) { await item.click(); return true; }
  return false;
}
async function resolvePermissionPrompts() {
  // Android permission controller resource ids, never coordinates. These vary by permission type.
  for (let i = 0; i < 8; i++) {
    const clicked = await clickIfPresent('id=com.android.permissioncontroller:id/permission_allow_foreground_only_button')
      || await clickIfPresent('id=com.android.permissioncontroller:id/permission_allow_button');
    if (!clicked) break;
    await driver.pause(250);
  }
}

try {
  driver = await remote({ hostname: host, port, path: '/', logLevel: 'error', capabilities: {
    platformName: 'Android', 'appium:automationName': 'UiAutomator2',
    'appium:udid': process.env.ANDROID_UDID ?? 'emulator-5554',
    'appium:appPackage': appPackage, 'appium:appActivity': 'com.vokie.MainActivity',
    'appium:noReset': true, 'appium:chromedriverAutodownload': true, 'appium:newCommandTimeout': 120,
  }});

  await test('native startup exposes permission flow or app', async () => {
    const pkg = await driver.getCurrentPackage();
    assert.ok([appPackage, 'com.google.android.permissioncontroller'].includes(pkg), `unexpected package ${pkg}`);
    await artifact('startup');
  });
  await test('permission prompts are controllable with UiAutomator2', async () => {
    await resolvePermissionPrompts();
    const contexts = await driver.getContexts();
    assert.ok(contexts.includes('NATIVE_APP'));
    assert.ok(contexts.some((context) => context.startsWith('WEBVIEW_')), 'VAANI WebView context was not exposed');
  });
  await test('WebView is automatable and exposes VAANI branding', async () => {
    const webView = (await driver.getContexts()).find((context) => context.startsWith('WEBVIEW_'));
    assert.ok(webView, 'VAANI WebView context was not exposed');
    await driver.switchContext(webView);
    assert.equal(await driver.getTitle(), 'VAANI — Every language. One conversation.');
    await driver.switchContext('NATIVE_APP');
  });
  await test('first-launch flow reaches the transcript communication home', async () => {
    const webView = (await driver.getContexts()).find((context) => context.startsWith('WEBVIEW_'));
    assert.ok(webView);
    await driver.switchContext(webView);
    if (await (await driver.$('#screen-a1.active')).isExisting()) {
      await (await driver.$('#screen-a1 .btn-primary')).click();
      await (await driver.$('#screen-a2.active .btn-primary')).click();
      const name = await driver.$('#name-input');
      await name.setValue('M18 Tester');
      await (await driver.$('#screen-a3.active .btn-primary')).click();
      await (await driver.$('#screen-a4 .lang-row[data-lang="Tamil"]')).click();
      await (await driver.$('#screen-a4.active .btn-primary')).click();
    }
    await (await driver.$('#screen-speak.active')).waitForDisplayed({ timeout: 5_000 });
    await driver.switchContext('NATIVE_APP');
  });
  await test('transcript navigation opens and returns from messages', async () => {
    const webView = (await driver.getContexts()).find((context) => context.startsWith('WEBVIEW_'));
    assert.ok(webView);
    await driver.switchContext(webView);
    await (await driver.$('#screen-speak .bnav-tab:nth-child(2)')).click();
    await (await driver.$('#screen-messages.active')).waitForDisplayed({ timeout: 5_000 });
    assert.ok(await (await driver.$('#screen-messages')).isDisplayed());
    await (await driver.$('#screen-messages .bnav-tab:nth-child(1)')).click();
    await (await driver.$('#screen-speak.active')).waitForDisplayed({ timeout: 5_000 });
    await driver.switchContext('NATIVE_APP');
  });
  await test('supported language selection updates the real WebView state', async () => {
    const webView = (await driver.getContexts()).find((context) => context.startsWith('WEBVIEW_'));
    assert.ok(webView);
    await driver.switchContext(webView);
    await (await driver.$('#screen-speak [onclick="showLanguagePicker()"]')).click();
    const options = await driver.$$('#language-picker-list > div');
    assert.ok(options.length >= 6, `expected supported language options, got ${options.length}`);
    await options[2].click(); // Tamil, a documented supported local UI option.
    assert.ok((await (await driver.$('#speak-language-label')).getText()).includes('Tamil'));
    await driver.switchContext('NATIVE_APP');
  });
  await test('application remains alive after permission handling', async () => {
    await driver.activateApp(appPackage);
    await driver.pause(800);
    assert.equal(await driver.getCurrentPackage(), appPackage);
    await artifact('app-active');
  });
  await test('restart retains a healthy application process', async () => {
    await driver.terminateApp(appPackage);
    // UiAutomator2's activateApp can return before Android resumes the WebView activity
    // on this API 37 image. Starting the manifest activity is the deterministic lifecycle API.
    await driver.startActivity(appPackage, 'com.vokie.MainActivity');
    await driver.waitUntil(async () => (await driver.getCurrentPackage()) === appPackage, { timeout: 8_000, interval: 250 });
  });
} finally {
  if (driver) await driver.deleteSession();
}
if (failures) process.exitCode = 1;
