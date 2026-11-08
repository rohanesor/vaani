# UI fidelity correction — incomplete / physical gate blocked

The final-screen continuation below supersedes the earlier build/status snapshot.

Starting SHA: `c1c977f5e710be30f2c8488a2262526ef7c48275`.
Checkpoint: `sih/before-ui-fidelity-fix`. Tracked worktree was clean before edits. Existing untracked exports/models/logs preserved.
Validated production APK remains SHA `072f0387fc5104d1eea4d9d2d66eb39faca1b91b33eb4da9029e7513997614c9`, not the prior UI candidate.

## Source comparison and corrections

Export: `iTantra.dc.html` (14-screen board), `iTantra Logo.dc.html`, `github.md` and generated `support.js`. The export uses CSS-built illustrations and Material Symbols Rounded, not an available local font file. Android sans-serif is still a font substitution; exact Helvetica metrics are not verified.

- Home: removed oversized bordered engineering-style header, tagline, language-routing summary and text Settings button from Speak. Added export's 19sp mark, settings icon and separate 14dp-radius connection panel at 22dp margins. Emergency actions remain in Messages and existing emergency sheet; no backend changes.
- Home language row: bordered surface, translate/chevron icons, 15sp language text and export padding; replaced plain text button.
- Home title/hint: 22sp/14sp and export line heights rather than shared oversized typography.
- Navigation: 11sp uppercase labels, 0.6sp tracking, background matching the screen.
- Wordmark: proportional dot/stem, baseline offset and inter-letter gap at 19/30sp sizes.
- Intro: removed extra wordmark; anchored dots and 56dp CTA at bottom, restored 28dp margins, 30/36sp heading, 16/25.6sp body. Rebuilt Hello/bars/namaste illustration and capability icon rows; retained truthful supported-language copy.
- Attribution: 3-part progress, 11sp step label, 26sp heading, 56dp option rows, custom radio visual and bottom-anchored rectangular CTA. Empty selection remains optional via Continue. No redundant Skip button in this screen.
- Centralized export-specific values in `DesignSpec.kt`.

## Screen acceptance inventory

| Screen | Current status / remaining discrepancy |
|---|---|
| 01 Splash | Existing system splash; export's photographic placeholder and layout not reproduced. FAIL for exact fidelity. |
| 02–04 Intro | Layout/illustrations corrected; not inspected in installed candidate. Ring motion and exact font metrics remain unverified. |
| 05 Attribution | Corrected dimensions/CTA, persistence unchanged; new render unverified. |
| 06 Primary language | Existing independent language selectors/search differ from exported primary-only layout. Still requires fidelity work without changing routing. |
| 07 Download | Real CDN state connected, but dialog remains different from full-page export. Still requires fidelity work. |
| 08 Failure/retry | Real retry, but dialog not exported page layout; no fake resume promise. |
| 09 Complete | Existing completion layout differs from exported grouped READY list. Readiness must remain truthful. |
| 10 Home | Corrected in source; installation and screenshot comparison BLOCKED. |
| 11 Listening | Same home-state surface rather than exact listening layout. Still requires work. |
| 12 Incoming | Real playback/text state but not complete exact export layout. Still requires work. |
| 13 Channel | Existing message/translation controls differ from export voice log. Still requires work. |
| 14 Settings | Real functionality, but separate row cards differ from grouped export sections. Still requires work. |

Do not describe this change as exact fidelity or a complete implementation.

## Verification

- Unit tests: **331 passed, 0 failed, 0 skipped** (JUnit XML).
- `assembleDebug` and `assembleDebugAndroidTest`: PASS.
- Instrumentation suite updated for attribution's optional Continue action; built but not run in this revision.
- APK: `app/build/outputs/apk/debug/app-debug.apk`.
- SHA: `5ad421506a64eda4ab39499a423f223f4e5ab6c2ac804e143738a42275635ad9`.
- Device: Realme RMX3782/API 35, serial MJPVXCSG9HYL65YL.
- Install attempt through safety runner: **blocked before installation** by thermal gate. Pre-existing APK backed up and hash-verified at Windows TEMP `itantra-ui-20260909-225632-853`.
- Recheck after 60 seconds: thermal status **3**, skin **46.011°C** (HAL). Stop physical testing. No thermal override, inference or further app launches.
- Candidate not installed/launched/visually inspected. Earlier revision screenshots are not evidence for this revision.
- vivo/API 34: unavailable, not tested.
- Voice loop, timings, duplicates, queue drain: NOT TESTED. No inference/runtime/transport changes, but unchanged source is not physical regression proof.

## Result

UI IMPLEMENTATION: FAIL (incomplete)
DESIGN FIDELITY: FAIL (not verified; known differences remain)
ONBOARDING: FAIL (visual acceptance pending)
SETUP: FAIL (known layout differences remain)
LANGUAGE DOWNLOAD UI: FAIL (dialog vs full-page mismatch)
MAIN EMERGENCY UI: FAIL (candidate not inspected)
ANIMATIONS: FAIL (not all export motion reproduced)
VOICE LOOP: FAIL (not tested; thermal block, no receiver)
TESTS: 331 passed / 0 failed
ASSEMBLE DEBUG: PASS

FAIL here means acceptance criteria unmet, not an observed backend failure. Finish the remaining screens and repeat screenshot comparisons on cooled devices. No production promotion.


## Final-screen continuation (starting f536da3)

Checkpoint: `sih/before-ui-final-screen-pass`; branch remains `sih/ui-fidelity-corrections-wip`.
Starting tracked worktree clean. No model, transport, translation, routing, timing, queue or engine files changed.

### Closed source-level discrepancies (not physical PASS)

- Download inventory, selected language, progress, completion and failure/retry now use a full-screen destination with designed step header, 28dp margins, 26sp title, 14dp pack cards, 6dp progress track and bottom 56dp CTAs. Removed both generic AlertDialogs. Only the existing downloader supplies progress; no fake bytes/ETA/resume/cancellation claims. The custom full-screen window preserves the underlying setup/settings state.
- Completion: outlined 104dp success circle / 52dp check, 28sp heading, grouped language summary and bottom-anchored Open action. No extra wordmark. Summary labels remain input/output instead of falsely reporting model readiness.
- Setup: shared step indicator replaces branding block; 28dp margins, 58dp selection rows, 1dp strokes. Input/output selection semantics unchanged.
- Settings: grouped LANGUAGE and DEVICE panels, 21dp icons / 20dp chevrons / 16sp labels, back icon instead of text button. Removed technical emergency-policy explanation from normal copy. Rescue Locate remains accessible; developer tools debug-only.
- Channel header: real peer name, Back, PTT/CALL selector tied to existing preference, compact SOS access. Removed brand/dashboard header from conversation. Backend untouched.
- Navigation: rounded mic/forum/hub icons match the export categories; label order remains SPEAK/MESSAGES/DEVICES.
- Listening: animated activity bars replace the microphone glyph, without unmounting, moving, or changing the persistent PTT pointer recognizer. Removed extra button elevation. Activity bars are decorative state indicators, not measured amplitude.
- Incoming: 150dp ring, 104dp amber speaker disc and 52dp icon, compact amber activity bars; source/translation remain actual presentation values.
- Added reusable DesignAction, SetupProgress and DesignVoiceBars primitives; one shared animation clock per bar group.
- Added three PackPage instrumentation fixture tests for progress, failure/retry and completion. Compiled/packaged only; no physical run claimed.

### Per-screen recheck / acceptance remains open

| Screen | Remaining visual or integration gap |
|---|---|
| 01 Splash | Native system splash retained; export's photo is a placeholder, not a supplied usable standalone photo. No fake model-progress bar added. |
| 02–04 Intro | Previously corrected layouts remain; exact illustration centering and all ring/transition motion still need source refinement and screenshot comparison. |
| 05 Attribution | Source corrected in previous pass; no new on-device comparison. |
| 06 Primary language | Independent input/output editor intentionally remains, so layout still differs from primary-only reference. Search placement and larger headings still need refinement. |
| 07 Download | Full-screen source implemented. Actual installer percent only; no ETA/byte stream API. Closing continues background work because no cancellation API exists. |
| 08 Retry | Full-screen source implemented. Backend discards failed temporary data; do not promise retained progress. Retry visual not inspected. |
| 09 Complete | Dimensions/grouping improved; input/output summary differs from installed-pack READY rows. |
| 10 Home | Not inspected for current build. Font substitution persists. |
| 11 Listening | Gesture surface stays mounted; header/navigation/label positions not yet identical to dedicated reference page. No fabricated partial transcript. |
| 12 Incoming | Circle/bars corrected; actual playback duration/progress unavailable, replay remains in message controls. Full layout not identical. |
| 13 Channel | Header corrected; text/delivery/emergency controls retained rather than fake prerecorded waveform/time data. Message-card structure remains different. |
| 14 Settings | Grouped rows corrected; speech controls still need closer custom toggle/slider treatment. Locate/debug entry retained for functionality. |

No claim that EVERY discrepancy is closed. Font/assets/spacing/responsive layouts and full motion are not accepted without screenshots. No delayed screen transition added around PTT: preserving pointer lifetime takes priority.

### Verification and thermal stop

- `testDebugUnitTest`: **331 passed / 0 failed**.
- `assembleDebug`, `assembleDebugAndroidTest`: **PASS**.
- New APK SHA: `7d2a2b491a8d06302a0e561013ac010e214dd140c8a6e74fd062a3f11c9a08d6`.
- APK path: `app/build/outputs/apk/debug/app-debug.apk`.
- Realme connected/authorized, but pre-install check: thermal status **3**, HAL skin **47.978°C**. No install, launch, data clear or inference in this pass. Previous installed APK unchanged.
- **PHYSICAL VISUAL VALIDATION: BLOCKED — THERMAL**.
- Vivo/API 34 not connected. No all-screen screenshots, voice-loop or timing/queue regression results for this APK.
- UI source inspection and build are not substitutes for visual acceptance. All visual acceptance categories remain FAIL/unverified; build/unit tests alone PASS.
