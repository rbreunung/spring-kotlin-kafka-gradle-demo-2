---
name: feature-spec
description: Use to run the feature specification workflow. TRIGGER when user wants to specify, design, or document a new feature before implementation. Creates feature spec, updates architecture doc, and writes the implementation plan.
---

Follow the workflow defined in `docs/workflows/feature-spec.md` exactly.

## Claude Code Tool Mappings

| Workflow says...                        | Use this Claude Code capability                        |
|-----------------------------------------|--------------------------------------------------------|
| "Ask the user [question]"               | Conversational reply — no special tool needed          |
| "Present options and wait"              | Conversational reply                                   |
| "Read file [path]"                      | Read tool                                              |
| "Write file at [path]"                  | Write tool                                             |
| "Run bash command [cmd]"                | Bash tool (follow Bash Command Style in AGENTS.md)     |
| "Commit with message [msg]"             | Bash: `git add [files]` then `git commit -m "[msg]"`   |

## Claude Code-Specific Notes

- At workflow start, create a TaskCreate entry for each numbered step; mark complete with TaskUpdate as you go
- No writes to `/tmp` — use `build/agent-debug/` for any temp output
- No pipes, heredocs, or multiline bash strings — see Bash Command Style in AGENTS.md
