# VAANI Source Preservation Record

**Recorded:** 2026-11-02, before dependency installation or application feature changes.

## Git baseline

- Worktree: `D:\vibe\vaani` / `D:\vibe\VAANI` (one case-insensitive directory).
- Branch: `vaani/main`.
- Tracked baseline commit: `f228b923c7d37e905001b08f9ecdb73ec0473423` (`Initialize VAANI product foundation and AI boundaries`).
- Original Android worktree: `/mnt/d/vibe/vokie`, branch `sih/ui-fidelity-corrections-wip`, commit `c8f6bb1`; it is dirty and was not modified.
- Additional worktree: `/tmp/vokie-p115a`, detached `7ef8509`; untouched.

At record time, the root worktree contains pre-existing uncommitted Android/web-document changes and untracked web source. Nothing was reset, discarded, or overwritten.

## Bundle checkpoint

- Artifact: `vaani.git.bundle`
- SHA-256: `5e6fac3ab141357889c2178ac53696901cda043901de29701ef254a96823b698`
- `git bundle verify`: passed.
- Complete-history bundle ref: `refs/heads/main` → `081854c547c29f2e2d1373cbadcaeff0e105aaf1`.
- Web product commits: `a55390d` (web/server baseline) and `081854c` (v2 Claude Design visual identity).

## Bundle-to-working-tree comparison

Compared bundle checkout `081854c` with current source using recursive comparison excluding dependency directories:

| Path | Result |
|---|---|
| `server/` | Exact match with bundle source. |
| `client/` | Exact match except `client/public/favicon.svg` differs locally and `client/_to_delete/` exists only locally. |
| `client/_to_delete/` | Local legacy components (`Conversation.tsx`, `MessageBubble.tsx`); retained without modification. |
| Current root web docs | Present as uncommitted work and historically recoverable from the bundle. |

The local favicon and legacy components are intentionally preserved. The bundle is the recovery point and must not be overwritten.

## Recovery procedure

1. Verify first: `git bundle verify vaani.git.bundle` and compare the SHA-256 above.
2. Inspect without changing this worktree: `git clone vaani.git.bundle <temporary-directory>`.
3. Recover a specific bundle revision from `081854c` in a temporary clone or an explicitly approved preservation branch.
4. Never run reset/checkout against `client/`, `server/`, `app/`, or `vaani.git.bundle` in this worktree to resolve differences.

## Checkpoint policy

The next safe checkpoint is an additive Git commit that records the current web source, preservation/reconciliation docs, and the union ignore rules while retaining both Android and web files. It must not include build outputs, dependencies, local databases, provider secrets, or model artifacts.
