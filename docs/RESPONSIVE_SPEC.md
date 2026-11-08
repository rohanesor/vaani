# VAANI — Responsive Specification (v2)

| Width | Layout |
|---|---|
| ≥ 1280 | Index 340 · stage. Context pane is a third column (392); the transcript reflows and stays pinned to the latest message. |
| 1024–1279 | Index 340 · stage. Context pane overlays from the right with a scrim. |
| 760–1023 | Index 300 · stage; settings nav 220; Today’s start cards stack. |
| < 760 | One region at a time: the index is home; a conversation, Companion or Settings is full screen with a back button. No bottom tab bar. Context pane and sheets become bottom sheets. VAANI/Speak keys collapse to their marks; message tools open on tap (focus) above the message; the composer hint hides. |

Rules: layouts reorganise rather than scale; reading column capped at 720 px; `100dvh` + safe-area insets; textarea grows to 176 px and re-fits on width change; waveforms shrink their bar gaps instead of overflowing.
Verified in Chromium at 1440×900 (light), 1280×820 (dark) and 390×844 (phone, touch).
