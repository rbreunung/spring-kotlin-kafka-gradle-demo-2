---
name: init-project
description: Use to initialize a new project from the template. TRIGGER automatically when docs/project-idea.md does not exist. Guides project setup via Q&A, creates core docs, and makes the workspace ready for feature development.
---

Follow the workflow defined in `docs/workflows/init-project.md` exactly.

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
