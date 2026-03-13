# Git Best Practices for Agents

## Branch Rules

You are either on **main** or on a **feature branch**. Rules differ:

**On a feature branch:**
- All commits (code + docs) go on the feature branch — never to `main`.
- The **only permitted merge** on a feature branch is `git merge main` (bringing main updates in).
  No other merge direction or branch pairing is allowed without explicit user approval.
- Never merge the feature branch into `main` — leave integration to the user.
- Do not read files from `main` directly (`git show main:path` or `git -C`);
  run `git merge main` first, then read files normally.

**On `main` (spec/planning work, no feature branch active):**
- Spec, planning, and documentation commits go directly on `main`.

**Stale feature branch (behind main):**
- Not pushed to origin → `git rebase main` (clean linear history).
- Already pushed to origin → `git merge main` (preserve history; discuss recreation with user).

## Allowed Operations

```bash
git status
git log --oneline -10
git diff --stat HEAD
git add path/to/specific/file.kt   # specific files only, never -A or .
git commit -m "type(scope): msg"   # always a separate step from git add
git merge main                     # bring main into feature branch
git rebase main                    # only if branch not yet pushed to origin
```

## Prohibited Patterns

| Pattern | Why | Instead |
|---|---|---|
| `git -C /other/path` | Changes working dir; fragile | Stay in project root |
| `git show otherbranch:file` | Reads from another branch | `git merge main`, then read normally |
| `git log \| grep \| awk` | Pipelines cause interaction | Use `--format` flags directly |
| `git push --force` | Destructive | Requires explicit user confirmation |
| `git reset --hard` | Destructive | Requires explicit user confirmation |
| `git merge feature main` | Merges to main | Never — leave to user |
| `git checkout .` / `git restore .` | Discards uncommitted work | Discuss with user first |
| `git add -A` / `git add .` | Stages unintended files | Add specific files by name |

## Commit Discipline

- **One commit per vertical slice** (one logical unit of deliverable work).
- Commit message format: `type(scope): short description` (e.g. `feat(risk): add RiskKafkaListener`).
- Never accumulate multiple slices before committing.
