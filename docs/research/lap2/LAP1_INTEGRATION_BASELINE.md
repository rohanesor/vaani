# LAP1 Integration Baseline

**Created:** 2025-01-27
**Purpose:** Source-of-truth documentation for LAP1 baseline before LAP2 integration

## Git State

| Property | Value |
|----------|-------|
| **Branch** | `sih/ui-fidelity-corrections-wip` |
| **HEAD SHA** | `73563ab9c6fb9e48662034112d2d8d01a5353309` |
| **Protection Tag** | `lap1-working-baseline` → `73563ab` |
| **Remote** | `origin` → `https://github.com/rohanesor/vokie.git` |
| **Common Ancestor (LAP2)** | `1c20506` |
| **Original LAP1 Tip** | `d753dff` (6 commits behind current HEAD) |
| **Frontend Introduction** | `c1c977f` |
| **Working Tree State** | ✅ Clean |

## Additional LAP1 Commits (d753dff → 73563ab)

```
73563ab docs: LAP 1.6 language integration completion report
d07f70a feat: complete ten-language integration gates
6339bff docs: complete LAP 1.6-1.10 engineering hardening audits
695085b docs: complete LAP 1.1-1.5 engineering hardening audits
1c0e6cd fix: present receiver translations in recipient language
436f9a3 fix: resolve MAC↔UUID mapping in message filtering
d753dff docs: update validation report with MAC↔UUID bug fix
```

These are legitimate LAP1 commits (language integration, bug fixes, documentation).
No LAP2 material is present.

## Build Configuration

| Property | Value |
|----------|-------|
| **Gradle Version** | 8.9 |
| **Android Gradle Plugin** | 8.5.2 |
| **Kotlin Version** | 1.9.24 |
| **Compile SDK** | 34 |
| **Min SDK** | 24 |
| **Target SDK** | 34 |
| **NDK Version** | 27.0.12077973 |
| **Java Version** | 17 |
| **Compose Compiler** | 1.5.14 |

## Package Identity

| Property | Value |
|----------|-------|
| **Namespace** | `com.vokie` |
| **Application ID** | `com.vokie` |
| **Debug Application ID** | `com.vokie` (no suffix) |
| **Version Name** | `1.0.0` (default) |
| **Version Code** | `1` (default) |

## Build Variants

| Variant | Signing | Minification | Notes |
|---------|---------|--------------|-------|
| `debug` | Debug keystore | Disabled | Default development build |
| `release` | Requires env vars | Enabled (R8) | Production signing required |

## Signing Configuration

Release signing requires environment variables:
- `ANDROID_KEYSTORE_PATH`
- `ANDROID_KEYSTORE_PASSWORD`
- `ANDROID_KEY_ALIAS`
- `ANDROID_KEY_PASSWORD`

## Build Features

- ✅ Jetpack Compose
- ✅ BuildConfig generation
- ✅ Native code (CMake)
- ✅ Vector drawable support

## Native Configuration

- ABI Filter: `arm64-v8a` only
- STL: `c++_shared`
- CTranslate2 JNI integration

## LAP2 Divergence Reference

| Metric | Value |
|--------|-------|
| Common Ancestor | `1c20506` |
| LAP1-only commits | 23+ (now 29 including recent work) |
| LAP2-only commits | 11 |
| LAP1-only files | 65 |
| LAP2-only files | 32 |
| Modified both sides | 71 |
| Identical | 424 |

---

**This document establishes the LAP1 baseline for protected integration.**
