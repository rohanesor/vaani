# Android UI Automation

VAANI uses repository-local Appium 3 and WebdriverIO with the UiAutomator2 driver. This bypasses the API-37 Espresso input-injection incompatibility for native lifecycle and permission automation.

## Architecture

- UiAutomator2: Android package/activity, runtime permission dialogs, lifecycle, screenshots and XML source.
- Appium WebView context: DOM access for the WebView-hosted VAANI UI.
- `tests/android/run.mjs`: deterministic smoke suite; it writes failure diagnostics to ignored `tests/android/reports/`.

## Run

1. Build/install the debug APK with the configured Android SDK.
2. Start Appium on the Windows host with an isolated `APPIUM_HOME` and UiAutomator2 driver. Enable `uiautomator2:chromedriver_autodownload` for the installed WebView version.
3. From WSL, point `APPIUM_HOST` to the Windows/WSL gateway and run:

```sh
APPIUM_HOST=172.29.80.1 npm run test:android
```

No Windows path or SDK path is committed. See `TEST_ENVIRONMENT_SETUP.md`.

## Executed coverage

On the Windows API-37 16K Pixel, the Appium suite passed 5/5:

1. Native startup reaches either the OS permission flow or VAANI.
2. UiAutomator2 controls permission prompts using Android resource IDs.
3. VAANI WebView context is exposed.
4. WebView branding title is asserted through Chromedriver.
5. App remains foreground/alive and restarts through its manifest activity.

This is real device automation, not Espresso. It does **not** claim cloud login, messaging, voice transcription, or sync end-to-end coverage: the Android product UI is a local WebView bridge and those scenarios need controlled peer/backend fixtures before meaningful assertions can be made.
