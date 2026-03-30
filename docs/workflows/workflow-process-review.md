# Retrospective Review Workflow

```mermaid
flowchart TD
    A[Read all RETRO-NNN docs] --> B[Create branch]
    B --> C[Identify themes]
    C --> D[Draft improvements per theme]
    D --> E[Present + discuss with user]
    E --> F[Apply agreed changes]
    F --> G[Write RETRO-REVIEW-NNN doc]
    G --> H[Commit all changes]
    H --> I[Offer Integration]
```

## Context Budget

Files to read:
- All `docs/retrospectives/RETRO-NNN-*.md` files (use glob)
- All `docs/retrospectives/RETRO-REVIEW-NNN.md` files — read to identify which retros are already reviewed
- `AGENTS.md` (for potential updates)
- Any specific workflow or feature doc flagged for improvement

---

## Steps

### STEP 1: Read All Retrospectives

Use glob to list all files matching `docs/retrospectives/RETRO-[0-9]*.md`.

Read each one. For any `RETRO-REVIEW-NNN.md` files that exist, read them to determine which retrospectives have already been reviewed — focus analysis on the **unreviewed** ones, but note any recurring patterns from older ones too.

### STEP 1b: Create Branch

Before making any changes, allocate the RETRO-REVIEW ID and create a branch:

1. Read `docs/registry.md`, find the highest existing RETRO-REVIEW-NNN, allocate the next number
2. Add a row to the registry immediately: `| RETRO-REVIEW-NNN | RETRO-REVIEW | [session date] | in-progress |`
3. Create the branch:

```bash
git checkout main    # or master
git pull
git checkout -b chore/workflow-process-review-NNN
```

All workflow edits and the review doc commit go on this branch.

### STEP 2: Identify Themes

Group friction points and suggestions across all retrospectives by category:

| Category | Description |
|---|---|
| **Context Loading** | Too many files read, wrong files loaded, context overflows |
| **Spec Clarity** | Ambiguous requirements, missing edge cases, vague acceptance criteria |
| **Test Coverage** | Test gaps, unclear test descriptions, tests that don't catch real bugs |
| **Workflow Steps** | Steps that are confusing, missing, in the wrong order, or unnecessary |
| **Git Flow** | Branching, commit, or PR issues |
| **Documentation** | Docs too long, outdated, inconsistent, missing |
| **Other** | Anything not captured above |

For each category: list the RETRO IDs that mention it and count occurrences.

### STEP 3: Draft Improvements

For each category with **2 or more mentions**, draft 2–3 actionable improvements:
- Identify the specific friction from the retrospectives
- Propose a concrete change (edit a skill, add a step, remove a step, update a template, etc.)
- Reference the RETRO IDs as evidence

### STEP 4: Present and Discuss

Present the analysis to the user:

> "I reviewed [N] retrospectives. Here are the recurring themes:
>
> **[Category]** (mentioned N times: RETRO-001, RETRO-003)
> - [Description of friction]
> - Proposed fix: [specific change]
>
> [repeat for each theme]
>
> Which of these do you want to act on?"

Discuss each proposed change. Some may need adjustment. Agree on a prioritized list before making any changes.

### STEP 5: Apply Agreed Changes

For each agreed improvement, make the change:

| Target | Action |
|---|---|
| Workflow | Edit `docs/workflows/[workflow].md` |
| AGENTS.md | Edit `AGENTS.md` |
| Feature spec | Edit `docs/features/FEAT-NNN-*.md` |
| Document template | Edit `docs/templates/[template].md` |

Commit each logical group of changes:
```
chore: [brief description of improvement] — retro review finding
```

### STEP 6: Write Review Doc

1. Use the RETRO-REVIEW-NNN allocated and registered in STEP 1b
2. Copy `docs/templates/retro-review-template.md` to `docs/retrospectives/RETRO-REVIEW-NNN.md`
3. Fill:
   - Retrospectives reviewed (list RETRO IDs)
   - Previously reviewed up to (highest RETRO-REVIEW NNN before this one, if any)
   - Themes found with examples and agreed actions
   - Changes made (list of files updated)
4. Commit: `chore(RETRO-REVIEW-NNN): retrospective review — [one-line theme summary]`

### STEP 7: Offer Integration

Ask the user:
> "The retro-review changes are on `chore/workflow-process-review-NNN`. How would you like to integrate them?
> - **A) Open a pull/merge request** — create a PR/MR for review
> - **B) Merge locally** — merge into main now
> - **C) Keep on branch** — continue work before integrating"

If **B**: confirm explicitly with the user before merging.

---

## Completion Checklist

Use this to verify the workflow was followed completely before declaring done:

- [ ] RETRO-REVIEW-NNN allocated and row added to `docs/registry.md` immediately (STEP 1b)
- [ ] Branch created: `chore/workflow-process-review-NNN` (STEP 1b)
- [ ] All unreviewed RETRO-NNN documents read (STEP 1)
- [ ] Themes identified and presented to user (STEPs 2–4)
- [ ] All agreed improvements applied to `docs/workflows/` files only (STEP 5)
- [ ] Commits made for each logical group of changes (STEP 5)
- [ ] `docs/retrospectives/RETRO-REVIEW-NNN.md` written and committed (STEP 6)
- [ ] RETRO-REVIEW-NNN status updated to `complete` in `docs/registry.md` (STEP 6)
- [ ] Offer Integration step presented to user (STEP 7)
