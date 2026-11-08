# LAP2 Integration Candidates

**Created:** 2025-01-27
**Purpose:** Categorize LAP2 material for selective integration into LAP1

## Integration Priority Levels

### LEVEL 1 — SAFE / INDEPENDENT (First Priority)

These components have no impact on LAP1 application code and can be integrated immediately.

| Component | Description | Risk |
|-----------|-------------|------|
| `corpus/` | Test corpus directory structure | ✅ None |
| `corpus/text/` | Text corpus files | ✅ None |
| `corpus/stress/` | Stress test corpus | ✅ None |
| Corpus metadata | JSON/YAML corpus definitions | ✅ None |
| Corpus validation scripts | Python/shell scripts | ✅ None |
| STT corpus runner | Test execution scripts | ✅ None |
| Reports | Test result documentation | ✅ None |
| Research documentation | Analysis and findings | ✅ None |

### LEVEL 2 — DEBUG/TEST INFRASTRUCTURE (Second Priority)

These components extend LAP1 with debug/test capabilities without modifying production code paths.

| Component | Description | Risk |
|-----------|-------------|------|
| Debug activities | Test-only Android activities | ⚠️ Low (AndroidManifest) |
| Test harnesses | JUnit/instrumentation tests | ⚠️ Low |
| APK identity verification | Debug verification tools | ⚠️ Low |
| Test-only instrumentation | Profiling/tracing hooks | ⚠️ Low |

### LEVEL 3 — APPLICATION INTEGRATION (Careful Review Required)

These components modify LAP1 application behavior and require careful diff analysis.

| Component | Description | Risk | Review Required |
|-----------|-------------|------|-----------------|
| TTS integration | Sherpa-ONNX TTS changes | ⚠️ Medium | TTS pipeline comparison |
| STT integration | SenseVoice/Whisper changes | ⚠️ Medium | STT pipeline comparison |
| Model manager | Download/cache logic | ⚠️ Medium | Model lifecycle review |
| Communication | PacketV2 protocol | ⚠️ Medium | Protocol compatibility |
| UI hooks | Test UI entry points | ⚠️ Medium | Frontend impact review |

### LEVEL 4 — BUILD CONFIGURATION (Last / If Required)

These components should only be integrated if absolutely necessary.

| Component | Description | Risk | Review Required |
|-----------|-------------|------|-----------------|
| Gradle changes | Build script modifications | 🔴 High | Full diff review |
| AndroidManifest | Manifest changes | 🔴 High | Permission/component audit |
| Package configuration | applicationId changes | 🔴 High | Identity verification |
| CI configuration | GitHub Actions/workflows | ⚠️ Medium | CI pipeline review |

## Integration Rules

1. **NEVER** integrate LEVEL 3/4 components without explicit human approval
2. **ALWAYS** verify frontend hashes after any integration
3. **ALWAYS** run full test suite after integration
4. **ALWAYS** build and verify APK after integration
5. **DOCUMENT** every integrated component in commit messages

## LAP2 Files Reference (From Reconciliation)

### LAP2-Only Files (32)

These exist only in LAP2 and may be candidates for integration:

- Corpus files
- Test runners
- Debug utilities
- Research documentation

### Modified on Both Sides (71)

These require careful 3-way merge or manual review:

- May contain LAP2 test hooks in production code
- May have divergent implementations
- Frontend files in this category are **DO NOT TOUCH**

### Identical (424)

These need no action.

## Current Status

| Level | Status |
|-------|--------|
| LEVEL 1 | ⏳ Awaiting LAP2 branch access |
| LEVEL 2 | ⏳ Awaiting LAP2 branch access |
| LEVEL 3 | 🚫 Not authorized for this task |
| LEVEL 4 | 🚫 Not authorized for this task |

---

**This document guides selective LAP2 integration after LAP1 baseline is established.**
