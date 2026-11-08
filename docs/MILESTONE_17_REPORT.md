# M17 — Web and Android UI Automation

## Baseline

- Initial commit: `6f24dac`
- Branch: `vaani/main`
- Working tree: clean before M17.

## Automation result

Appium was installed as a repository development dependency, with UiAutomator2 installed into an isolated external Appium home. The healthy Windows Pixel was `device`, boot complete, API 37, model `sdk_gphone16k_x86_64`.

The initial Appium smoke suite passed **5/5** against the installed debug APK: startup, OS permission interaction, WebView context, branding/title, process persistence, and restart. Failure screenshots/XML are captured under ignored `tests/android/reports/`.

The Appium WebView bridge initially lacked Chromedriver for Chrome 145; controlled driver download was enabled in the isolated Appium home and the title assertion passed. This establishes a usable native/Appium layer despite Espresso's removed-InputManager incompatibility.

## Existing suites

- Android critical instrumentation: PASS 10/10 (prior M16 result).
- Android full Espresso suite: BLOCKED/FAIL on API 37 test-framework injection; protected corpus remains unstaged.
- Server deterministic Gnani/contracts and full suite: PASS 62/62 (prior validated result).
- Playwright: PASS 8/8 (prior validated result).
- Live Gnani: BLOCKED; no credentials used and no claim made.

## Limits

Only actual executed Appium checks are counted. Account login, cloud-sync UI, local peer messaging, language selection, and physical audio/radio workflows lack an isolated Android backend/peer fixture and remain NOT RUN—not simulated.

## Added tooling

- `tests/android/run.mjs`
- Root commands: `test:web`, `test:android`, `test:gnani`, `test:all`
- Setup, strategy, coverage-matrix documentation.

## Remaining blockers

1. Add controlled Android test fixtures for bridge/cloud/peer journeys.
2. Run UI suite on stable Android image or update Espresso stack.
3. Stage authorized corpus externally for corpus pipeline.
4. Obtain controlled Gnani credentials for a separately gated live smoke test.
