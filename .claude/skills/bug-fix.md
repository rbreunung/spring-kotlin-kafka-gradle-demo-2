---
name: bug-fix
description: Use to report and fix a bug. TRIGGER when user reports a defect, unexpected behavior, crash, or error. Phase 1 is structured intake (planning mode). Phase 2 is autonomous root cause analysis, fix, and test coverage.
---

# Bug Fix Workflow

```mermaid
flowchart TD
    A[Structured Q&A — 8 questions] --> B[Write bug report draft]
    B --> C{User confirms?}
    C -->|corrections| A
    C -->|confirmed| D[Allocate BUG-NNN + create doc]
    D --> E[Create fix branch]
    E --> F[Read source files — max 5]
    F --> G[Identify root cause]
    G --> H[Write failing repro test]
    H --> I[Implement fix]
    I --> J{Tests pass?}
    J -->|no| I
    J -->|yes| K[Commit fix + test]
    K --> L[Update bug report]
    L --> M[Doc review]
    M --> N{Retrospective?}
    N -->|yes| O[Write retro]
    N -->|no| P[Offer PR]
    O --> P
```

---

## Phase 1: Bug Intake

Ask these questions **one at a time**. Do not ask the next question until the current one is answered.

1. "Describe the bug briefly — what's going wrong?"
2. "What's your environment? (OS, language/runtime version, any relevant config)"
3. "Walk me through the exact steps to reproduce this."
4. "What did you expect to happen?"
5. "What actually happened? Please paste any error message, exception, or stack trace."
6. "Which component, module, or endpoint do you think is affected?"
7. "How severe is this?
   - **critical** — system is down or data is lost
   - **high** — major feature is broken
   - **medium** — degraded behavior, workaround exists
   - **low** — minor visual or non-critical issue"
8. "Is there a known workaround?"

After all answers, summarize back to the user:
> "Here's what I have — does this look correct? [show filled bug report fields]"

If corrections are needed: update and re-confirm. Proceed to Phase 2 only when confirmed.

---

## Phase 2: Autonomous Fix

### STEP 1: Allocate ID & Create Doc

1. Read `docs/registry.md`, find highest BUG-NNN, allocate next
2. Add to registry: `| BUG-NNN | BUG | [title] | in-progress |`
3. Copy `docs/templates/bug-report-template.md` to `docs/bugs/BUG-NNN-kebab-title.md`
4. Fill all fields from Phase 1 answers
5. Add `## Progress` section: `Phase: investigating | Hypothesis: — | Last Updated: [date]`

### STEP 2: Create Fix Branch

```bash
git checkout main    # or master
git pull
git checkout -b fix/BUG-NNN-kebab-title
```

### STEP 3: Investigate

Read (max 5 files):
- Source files for the affected component
- Relevant test files
- Related feature spec (if the behavior is documented in `docs/features/`)

Document root cause hypothesis in the `## Progress` section of the bug report:
```
Hypothesis: [your hypothesis about root cause]
```

### STEP 4: Write Failing Reproduction Test

Write a test that:
- Reproduces the exact bug as described (must FAIL before the fix)
- Will pass after the fix is applied
- Is the smallest test that captures the defect

Run it. **It must fail.** If it passes without a fix, revise the test.

### STEP 5: Implement Fix

Write the minimum change to address the root cause. Do not refactor surrounding code or fix unrelated issues in the same commit.

### STEP 6: Verify

Run the full test suite. All tests must pass, including the new reproduction test.

If tests fail: fix the implementation, not the test (unless the test has a genuine error). Repeat until green.

### STEP 7: Commit

```
fix(BUG-NNN): [short description] — add reproduction test
```

### STEP 8: Update Bug Report

In `docs/bugs/BUG-NNN-*.md`:
- Fill `## Root Cause` with the actual root cause
- Fill `## Fix Summary` with what was changed and why
- Add test reference: file path and test name
- Remove `## Progress` section
- Update registry row status to `resolved`

Commit: `chore(BUG-NNN): finalize bug report`

### STEP 9: Doc Review

Briefly check (read only the immediately relevant docs — no more than 2 files):
- Does any feature spec describe behavior that this bug contradicts? If so, add a note to that spec.
- Should `docs/arch/architecture.md` note this as a known constraint or edge case?

Make only obvious, minimal improvements. Do not rewrite documentation.

### STEP 10: Opt-in Retrospective

Ask: "Would you like to add a retrospective for this bug fix?"

If yes:
1. Allocate RETRO-NNN from registry
2. Copy `docs/templates/retrospective-template.md`
3. Write `docs/retrospectives/RETRO-NNN-bugfix-BUG-NNN.md`
4. Commit: `chore(RETRO-NNN): bug fix retrospective for BUG-NNN`

### STEP 11: Offer PR

Ask: "Ready to create a pull request for `fix/BUG-NNN-*`?"

If yes: generate PR title (`fix(BUG-NNN): [short description]`) and body summarizing:
- What the bug was (from report summary)
- Root cause
- Fix approach
- Test added

Confirm the PR description with the user before creating.
