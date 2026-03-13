# Trade Execution Platform — Claude Code Instructions

## Bash Command Style

When running build, test, or git commands:
- **Do not pipe or redirect output** — avoid `|`, `2>&1`, `&&` chains in a single Bash call
- Run gradle and git as simple commands: `./gradlew :order:test`, not `./gradlew :order:test 2>&1 | tail -40`
- If you need multiple sequential operations, use separate Bash tool calls
- Output is automatically truncated by Claude Code — manual tail/grep is unnecessary

This keeps commands simple enough to be matched by pre-approved wildcard rules and avoids
repeated approval prompts during autonomous implementation sessions.
