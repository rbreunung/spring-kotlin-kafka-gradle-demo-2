# RETRO-023: Feature Spec — FEAT-014

Date: 2026-04-05
Workflow: feature-spec
Related: FEAT-014
Duration: ~1 session

---

## What Went Well

- Brainstorming skill worked well — collaborative Q&A + visual companion produced a clear, well-scoped design
- Visual companion (browser mockups) was effective for layout decisions (layout structure, admin dashboard)
- Design covered all sections cleanly: architecture, BFF pattern, component structure, build integration, Docker, testing

## What Was Difficult

- Visual companion server failed on first launch due to `node` not being in `PATH` — required manual discovery of nvm node binary before the server started
- Feature spec workflow requires a new branch + worktree, but the branch was created in the main repo working directory (which was already in use), causing `git worktree add` to fail with "already used by worktree"; spec work ended up in the main repo rather than a dedicated worktree as requested

## Suggested Improvements

### 1. Workflow Steps — Visual Companion Server Launch

**Description:** The brainstorming skill's server launch fails silently when `node` is not in `PATH`. The nvm-managed node at `~/.nvm/versions/node/*/bin/node` is not on the default shell `PATH` when Claude Code runs bash commands, causing a cryptic "server failed to start" error.

**Actionable Change:** Add a node discovery step to the brainstorming skill's visual companion startup: before running `start-server.sh`, probe common node locations (`~/.nvm/versions/node/*/bin/node`, `/opt/homebrew/bin/node`, `/usr/local/bin/node`) and prepend the found path to `PATH`. Document this in `skills/brainstorming/visual-companion.md` under "Starting a Session".

### 2. Git Flow — Worktree Creation for Feature Spec

**Description:** The feature-spec workflow (STEP 2) runs `git checkout -b feat/FEAT-NNN` in the main repo, which then occupies that branch. A subsequent `git worktree add` for the same branch fails because the branch is already checked out. The spec ends up in the main repo working directory rather than an isolated worktree.

**Actionable Change:** Update `docs/workflows/feature-spec.md` STEP 2 to use worktree-first creation: `git worktree add .claude/worktrees/feat-NNN-title -b feat/FEAT-NNN-title` from the main repo (without checking out the branch in the main repo first). This keeps the main repo on `main` and puts all spec work in the new worktree.
