# Test Environment Setup

## Web/server

```sh
npm --prefix server install
npm --prefix client install
npm run test:web
npm run test:gnani
```

`test:gnani` is deterministic/mock-backed server coverage only. Do not put provider keys in commands or source.

## Windows Android + Appium

Requirements: healthy Windows emulator (`adb devices` = `device`, `sys.boot_completed=1`), debug APK installed, Windows Node, and Android SDK available to Appium.

Install repository dependencies once:

```sh
npm install
```

On Windows, set a non-repository `APPIUM_HOME`, install UiAutomator2 using the repository-local Appium CLI, then start Appium with the UiAutomator2-scoped Chromedriver download permission. The Appium home/driver cache remains outside git. Set `APPIUM_HOST` to the Windows host IP visible from WSL, then run `npm run test:android`.

The first WebView run downloads a compatible Chromedriver into that isolated cache. It must be repeated when the emulator WebView major version changes.

## Safety

Use only emulator/test data. Android reports are written under ignored `tests/android/reports/`; never commit APKs, AVD data, SDK paths, credentials, audio corpus, screenshots with personal data, or provider traffic containing secrets.
