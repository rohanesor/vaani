# Recording Build Manifest

## Freeze candidate

- Source checkpoint before M18 commit: `6f214f3`
- Branch: `vaani/main`
- Variant: `debug` (recording candidate only; not production-signed)
- Application ID: `com.vaani.app`
- Version: `1.0.0` (code `1`)
- APK SHA-256: `5060f3f74ee7d5c0873dd6c87bda61c28a93dc88b285067af92ff56d1a38f42f`
- APK is intentionally not committed.

## Validation basis

- Android compile/assemble/JVM/androidTest source compilation: PASS.
- Critical Android instrumentation: PASS 10/10 (M16).
- Appium Android UI: PASS 8/8 (M18) on Windows Pixel API 37 / 16 KB page image.
- Server: PASS 62/62 plus typecheck/build.
- Web: PASS 8/8 Playwright plus typecheck/production build.
- Gnani deterministic: PASS 15/15 MOCK-BACKED.
- Gnani live: BLOCKED; controlled provider credentials and WAV fixture absent.

## Freeze limitations

This is not a release-signed or live-provider-verified build. Do not record a Gnani-live claim, physical nearby/radio claim, Android cloud-login claim, or protected-corpus claim from it. Any post-freeze UI/product change requires a defect reference and rerun of this manifest's validation.
