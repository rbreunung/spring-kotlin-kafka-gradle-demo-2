# Task Reminder: Verify allowedTools + CLAUDE.md Fix

## What was done

- Created `CLAUDE.md` in project root with instruction to avoid piped/compound bash commands
- Replaced accumulated entries in `.claude/settings.local.json` with clean wildcard rules

```json
{
  "permissions": {
    "allow": [
      "Bash(./gradlew *)",
      "Bash(git status*)",
      "Bash(git diff*)",
      "Bash(git log*)",
      "Bash(git add *)",
      "Bash(git commit*)",
      "Bash(git branch*)",
      "Bash(git merge-base*)",
      "Bash(git rm *)",
      "Bash(docker compose*)",
      "Bash(ls *)",
      "Bash(find *)"
    ]
  }
}
```

## Why

Subagents were piping gradle output (`2>&1 | tail -N`) which bypasses wildcard permission rules
(Claude Code v2.1.7 security fix blocks wildcards from matching compound commands).
This caused every gradle call to trigger an approval prompt and accumulate entries in settings.local.json.

## Verification checklist

- [ ] Start a fresh Claude Code session on this project
- [ ] Ask Claude to run `./gradlew :order:test` — confirm no approval prompt appears
- [ ] Ask Claude to run `./gradlew :order:test --tests "*.OrderRepositoryTest"` — confirm no prompt
- [ ] Ask Claude to run `git status` — confirm no prompt
- [ ] Ask Claude to run `git add order/build.gradle.kts` — confirm no prompt
- [ ] Ask Claude to run `git push` — confirm approval prompt DOES appear (destructive, not in allow list)
- [ ] Run a full implementation subagent task and confirm no gradle/git prompts during execution
- [ ] Check that `.claude/settings.local.json` is NOT growing with new entries after the session

## If prompts still appear

Check whether the command issued was a compound command (contains `|`, `&&`, `2>&1`, etc.).
If yes, the CLAUDE.md instruction was not followed — either the subagent ignored it or the prompt
template overrode it. Check the subagent prompt for piped gradle commands and remove them.
