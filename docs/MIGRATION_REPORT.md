# VAANI Migration Report

## Source and isolation
- Original repository: `/mnt/d/vibe/vokie` (branch `sih/ui-fidelity-corrections-wip`, dirty at discovery).
- VAANI repository: `/mnt/d/vibe/VAANI` (Git worktree branch `vaani/main`, based on `c8f6bb1`).
- The source was not modified. A worktree was chosen over a 57 GB copy because only 37 GB was free; history/objects are shared safely while working files and index are independent.

## Changed/added
- Product metadata: root project name, Android application ID, application label/tagline/subtitle, manifest themes.
- Added: `docs/{PRD,ARCHITECTURE,UI_UX_SPEC,IMPLEMENTATION_PLAN,MIGRATION_REPORT}.md`; `backend/`; AI contracts/retrieval and unit test.

## Preserved dependencies/functionality
Kotlin/Gradle/Room/JNI, Bluetooth protocol, mesh, offline model pack flow, local STT/TTS/translation, and delivery infrastructure are retained. `com.vokie`, Vokie protocol/database/JNI names are intentionally not blindly renamed: changing them would require a coordinated source/JNI/database migration. The independent `applicationId` prevents install collision.

## Technical debt/blockers
The inherited app is WebView-presented despite Compose dependencies; Room/database APIs show evolving schema concerns that need baseline build verification. No communications backend, end-user authentication, Gnani API contract, or VAANI visual assets existed. Existing source physical-device and model-performance validation remains incomplete.
