# RETRO-009: Feature Spec — FEAT-008

Date: 2026-03-16
Workflow: feature-spec
Related: FEAT-008
Duration: ~1 session (multi-turn brainstorm + spec + plan)

---

## What Went Well

- Structured feature scoping worked well — breaking candidate ideas into clearly bounded features
  with explicit non-goals prevented scope creep early
- Brainstorming the right next features (FEAT-008 / FEAT-009 / FEAT-010) as parallel drafts gave
  a useful overview of the roadmap before committing to any one spec

## What Was Difficult

- Understanding how all saga state transitions and race conditions would be handled required
  extended back-and-forth; there was no explicit checklist or prompt to verify coverage of all
  concurrent-event scenarios before closing the spec
- The FEAT-011 draft idea (Cancel In-Flight Orders) was identified and registered during the
  session but no draft document was written — leaving the registry entry without a corresponding
  spec file

## Suggested Improvements

### 1. Spec Clarity: Race Condition Coverage Checklist

**Description:** The spec session did not have an explicit step to enumerate concurrent-event
scenarios (e.g., cancel races risk rejection, duplicate settlement-failed events). These were
discovered reactively through user questions rather than proactively checked.

**Actionable Change:** Add a "Race Condition Coverage" sub-section to the feature-spec template
(`docs/templates/feature-spec-template.md`) under Edge Cases & Error Handling. Prompt: "For each
stateful transition, list the concurrent events that could arrive in the wrong order or twice, and
state the guard that handles each."

### 2. Documentation: Write Draft Specs Before Closing Registry Entries

**Description:** FEAT-011 was added to `docs/registry.md` as `draft` during the FEAT-008 spec
session but no corresponding `docs/features/FEAT-011-*.md` file was created. A registry entry
without a file is a dead link.

**Actionable Change:** Add a rule to the feature-spec workflow (`docs/workflows/feature-spec.md`)
Step 5 or Step 11: "Any new FEAT entries added to the registry during this session must have at
least a stub spec file created before the commit."

### 3. Architecture: Use Color-Coding in Flow Diagrams

**Description:** The architecture Mermaid diagrams show all services and message flows in a single
color, making it hard to distinguish which step in the saga lifecycle each arrow belongs to, or
which paths are compensation vs. happy-path.

**Actionable Change:** In `docs/arch/architecture.md`, extend the Mermaid sequence diagram with
`%%{init: {'theme': 'default'}}%%` and use `style` or `rect` grouping to visually separate
happy-path flows (green/blue) from compensation flows (orange/red) and terminal failure paths
(grey). Apply consistently to all future sequence diagrams added in new FEAT specs.
