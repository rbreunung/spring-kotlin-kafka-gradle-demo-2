# Git Best Practices for Agents

## Branch Rules

- All commits (code + docs) belong on the current work branch.
- Never commit directly to `main` during feature development.
- Never merge a feature branch into `main` — leave integration to the user.
- Spec and planning documents may be committed on `main` when written outside a feature context.
- If main has updates needed on the feature branch: `git merge main` into the feature branch.
  Do not read files from main directly (`git show main:path` / `git -C`).
- If the feature branch is stale and **not yet pushed to origin**: rebase on main or recreate.
- If the feature branch **has been pushed to origin**: merge only; discuss recreation with the user.

## Allowed Git Operations (no interaction expected)

```bash
git status
git log --oneline -10
git diff --stat HEAD
git add path/to/specific/file.kt     # add specific files, not -A or .
git commit -m "descriptive message"  # separate from git add
git merge main                       # bring main updates into feature branch
git rebase main                      # only if branch not pushed to origin
```

Always run `git add` and `git commit` as separate commands — do not chain them.
Add only files relevant to the current slice; never use `git add -A` or `git add .`.

## Prohibited Patterns

| Pattern | Why | Instead |
|---|---|---|
| `git -C /other/path` | Changes git working dir; fragile | Use absolute paths or stay in project root |
| `git show otherbranch:file` | Reads from another branch | `git merge main` first, then read normally |
| `git log \| grep \| awk` | Complex pipelines cause interaction | Use `--format` flags directly on git commands |
| `git push --force` | Destructive | Requires explicit user confirmation |
| `git reset --hard` | Destructive | Requires explicit user confirmation |
| `git merge feature main` | Merges to main | Never merge to main — leave to user |
| `git checkout .` / `git restore .` | Discards uncommitted work | Discuss with user first |

## Commit Discipline

- **One commit per vertical slice** (one logical unit of deliverable work).
- Stage specific files by name — not all changed files at once.
- Commit message format: `type(scope): short description` (e.g. `feat(risk): add RiskKafkaListener`).
- Never accumulate multiple slices before committing.
- If interrupted mid-slice, commit the partial work as a WIP commit rather than leaving it uncommitted.

## Reading Files from Other Branches

Do not use `git show main:docs/features/FEAT-003.md` or `git -C /main/repo cat docs/...`.

If you need a file that lives on main while you are on a feature branch:
1. `git merge main` — brings all of main into the feature branch
2. Read the file normally
3. Continue working on the feature branch

## Stale Branch Recovery

```
Feature branch is behind main?
  └─ Not pushed to origin → git rebase main   (clean linear history)
  └─ Already pushed to origin → git merge main (preserve history, discuss with user)
```

## Gradle Final Verification

Always use a clean build for final verification to prevent false passes from cached results:

```bash
./gradlew :module:clean :module:test
```

## Temp and Debug Output

Never write to `/tmp` — it triggers permission prompts outside the workspace.
Use `build/agent-debug/` within the project (already git-ignored by Gradle's build directory rule).
