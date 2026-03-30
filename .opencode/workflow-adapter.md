# opencode Workflow Adapter

Read this file at the start of every session. It defines how to run this project's
workflows in opencode with any model (including local models via ollama).

---

## Workflow Discovery

All workflows are defined in `docs/workflows/`. Read the relevant file and follow its steps.

| Trigger Phrase | Workflow File |
|---|---|
| "initialize project" | `docs/workflows/init-project.md` |
| "spec feature [name]" | `docs/workflows/feature-spec.md` |
| "plan first spec feature [name]" | `docs/workflows/feature-spec.md` (plan-first mode — see below) |
| "implement feature FEAT-NNN" | `docs/workflows/feature-impl.md` |
| "fix a bug" | `docs/workflows/bug-fix.md` |
| "run retrospective" | `docs/workflows/retrospective.md` |
| "run workflow process review" | `docs/workflows/workflow-process-review.md` |
| "run retrospective review" | `docs/workflows/workflow-process-review.md` (alias) |

---

## Default Mode: Interactive

Run all workflows in **interactive mode** by default. In interactive mode you can freely:
- Read files
- Ask the user questions
- Write files
- Run bash commands

All in the same session, in any order. There is no restriction on asking questions
mid-workflow. Most workflows should be run this way.

---

## Plan-First Mode (Optional)

Triggered when the user says **"plan first"** before the workflow trigger phrase, or when
opencode is in plan/read-only mode.

In plan-first mode, run the **entire workflow including all Q&A** normally — but
**defer all write and bash operations** until the user says **"implement"**.

### What to do at each deferred operation

**Write a file** — output the full intended content as a labeled fenced code block:

```
📄 docs/features/FEAT-012-cancel-orders.md
` ` `markdown
[full file content here]
` ` `
```

**Registry allocation** — state the ID you will assign:

```
📋 Will allocate FEAT-012 in docs/registry.md (next available after FEAT-011)
```

**Run a bash command** — show the command, don't execute:

```
🔧 Will run: git checkout -b feat/FEAT-012-cancel-orders
```

**Commit** — show the intended message, don't commit:

```
💾 Will commit: feat(FEAT-012): add feature spec and implementation plan
```

### End of plan-first mode

After all workflow steps are complete (including all Q&A), output this summary:

> "Planning complete. Here's what will be written when you say 'implement':
> - 📋 docs/registry.md — FEAT-012 and PLAN-012 rows
> - 📄 docs/features/FEAT-012-cancel-orders.md
> - 📄 docs/plans/PLAN-012-cancel-orders.md
> - 🔧 git checkout -b feat/FEAT-012-cancel-orders
> - 💾 commit: feat(FEAT-012): add feature spec and implementation plan
>
> Say **'implement'** to apply all of the above, or ask me to revise anything first."

### When the user says "implement"

Apply all deferred operations in the order they were planned within the **same session**:
1. Create branch
2. Write all files (registry first — it gates subsequent steps)
3. Run all bash commands
4. Commit

---

## Tool Mappings

| Workflow says...              | Interactive mode      | Plan-first mode                       |
|-------------------------------|-----------------------|---------------------------------------|
| "Ask the user [question]"     | Ask normally          | Ask normally — Q&A is never deferred  |
| "Read file [path]"            | Read normally         | Read normally                         |
| "Write file at [path]"        | Write the file        | Output as labeled `📄` code block      |
| "Run bash command [cmd]"      | Run the command       | Show with `🔧` prefix, defer           |
| "Commit with message [msg]"   | git add + git commit  | Show with `💾` prefix, defer           |

---

## Naming Reference

| Name | What it is |
|---|---|
| `retrospective` (RETRO-NNN) | A document written after completing a task — what went well, what was hard |
| `workflow-process-review` (RETRO-REVIEW-NNN) | A process that reads all retros and improves workflows |

These are **different**. "Run retrospective" writes a RETRO-NNN doc. "Run workflow process review" runs the improvement process.
