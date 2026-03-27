---
name: firmware-linear-planner
description: Break AirHealth firmware scope into actionable Linear planning artifacts by reading the PRD in `PM/PRD/PRD.md`, the latest software architecture spec in `SW/Architecture`, and the latest firmware feature design in `SW/feature-design`. Use when Codex needs to turn those source documents into a firmware delivery plan with one overall epic, one story per firmware feature or capability slice, and child tasks small enough to be implemented in a reviewable PR, splitting oversized work before drafting or creating Linear tickets.
---

# Firmware Linear Planner

Translate AirHealth source documents into firmware execution work that is immediately usable for Linear planning. Produce one epic for the overall firmware effort, one story per firmware feature or capability slice, and child tasks that are independently implementable, testable, and small enough for a reasonable pull request.

## Required Inputs

Read these inputs in this order:

- `PM/PRD/PRD.md`
- latest markdown architecture spec matching `SW/Architecture/Software_Architecture_Spec_*.md`
- latest markdown firmware design matching `SW/feature-design/Firmware_Feature_Design_*.md`

Read these only when needed:

- `SW/feature-design/Shared_Integration_Appendix_*.md` for contract details shared with mobile or cloud
- hardware or algorithm specs referenced by the firmware design when a task cannot be sized responsibly without them

Prefer markdown sources over exported HTML. If multiple versioned markdown files exist, use the highest version unless the user explicitly names another file.

Treat the documents with this precedence:

1. PRD for product truth and scope intent
2. architecture spec for system boundaries and ownership
3. firmware feature design for implementation handoff detail

If the documents conflict:

- prefer the newest approved source
- keep the conflict visible in assumptions or open questions
- do not hide ambiguity inside a task summary

## Planning Rules

- Create exactly one epic for the overall firmware project, release, or planning slice requested by the user.
- Create one story per distinct firmware feature or capability slice.
- Create only task-level children under stories.
- Keep implementation tasks firmware-owned. Put mobile, backend, hardware, or algorithm-team work into dependency notes instead of mixing them into the same task.
- Keep every implementation task small enough for a reviewable PR. Target roughly under 1000 changed lines including tests, protocol definitions, and support files that land in the same change.
- Split any task that combines multiple subsystems, multiple independently testable behaviors, or a broad cross-cutting refactor.
- Include verification in the same task only when it is tightly coupled and still reviewable. Otherwise, create a separate verification task.
- Do not create placeholder tickets that have no observable outcome, no acceptance criteria, or no clear owner.

## Workflow

Follow this sequence.

### 1. Build The Firmware Planning Baseline

Extract:

- firmware-facing features and capability slices
- product constraints that affect device behavior
- architectural boundaries, contracts, and dependencies
- firmware-owned states, failure modes, and observability requirements
- verification obligations such as unit, HIL, bench, power, replay, or OTA tests

Call out cross-feature foundation work such as shared session orchestration, common BLE payloads, flash journaling, or diagnostics plumbing when it is a real delivery slice rather than incidental implementation detail.

### 2. Define The Epic

Use the overall firmware effort as a single epic.

The epic should capture:

- problem or user value being delivered
- included firmware scope
- explicit exclusions
- exit criteria for the planned slice
- references to the PRD, architecture spec, and firmware design

### 3. Derive Stories

Map each firmware feature or capability slice into one story.

Good story candidates include:

- pairing and claim
- shared session orchestration
- oral measurement
- fat-burning repeated-reading measurement
- disconnect recovery and replay
- low-power behavior
- OTA transport and staging
- diagnostics and observability

Merge only when two slices cannot be delivered or validated independently.

Split a story when one feature contains a foundational platform slice plus a mode-specific behavior slice that should not land in the same implementation sequence.

If a source design already contains a handoff task table, use it as seed material only. Refine and split it rather than copying it directly into Linear output.

### 4. Break Stories Into Tasks

For each story, create tasks with:

- one primary objective
- one clear implementation boundary
- concrete acceptance criteria
- concrete verification
- explicit prerequisites or blockers

Prefer task boundaries that align to one of these implementation seams:

- protocol or schema
- state machine or orchestration logic
- algorithm hook-up
- persistence or journaling
- power-management behavior
- telemetry or diagnostics
- OTA transfer or staging
- HIL, bench, or automated verification

### 5. Sanity-Check Task Size

Split again if a task would likely require any of the following:

- touching multiple unrelated modules
- adding a new protocol plus business logic plus storage changes together
- implementing both the happy path and several unrelated recovery paths in one PR
- introducing more than one substantial test harness in the same change
- exceeding roughly a few focused engineer-days or about 1000 changed lines

Usually safe as one task:

- add one BLE command or event plus tests
- implement one deterministic state transition path in the session orchestrator
- persist and replay one terminal-result record type
- wire one metric or fault-code family through existing telemetry plumbing

Usually needs more than one task:

- implement oral and fat session engines together
- add disconnect recovery, flash journaling, and resume-query protocol together
- add OTA transfer, validation, apply, rollback, and telemetry together
- implement low-power behavior plus broad bench and HIL coverage in one PR when the validation work is substantial

### 6. Produce Linear-Ready Output

If Linear tooling is available, create the issues directly.

If Linear tooling is not available, produce ticket-ready content that can be pasted or recreated in Linear without re-deriving the planning structure.

## Ticket Schema

For every issue, include:

- Issue type: Epic, Story, or Task
- Summary: short action-oriented Linear title
- Parent: epic for stories, story for tasks
- Objective: what becomes possible after the issue ships
- Scope: concrete firmware modules, behaviors, interfaces, or artifacts to change
- Out of scope: nearby work intentionally excluded
- Acceptance criteria: flat checklist phrased as observable outcomes
- Verification: unit tests, HIL tests, bench tests, manual checks, or log inspection required
- Dependencies: upstream or downstream issues, external inputs, or blocking questions
- Notes: assumptions, open questions, or cross-team handoffs

Add these fields when useful:

- labels or components such as `firmware`, `ble`, `measurement`, `power`, `ota`, `diagnostics`, `hil`
- suggested sequencing order
- rough size such as `S`, `M`, or `L`

Anything that trends `L` should be reviewed for another split before finalizing the task list.

## Output Format

Default to this structure unless the user asks for CSV, table, or direct Linear creation:

```markdown
## Planning Assumptions
- ...

## Epic
- Issue type: Epic
- Summary: ...
- Objective: ...
- Scope: ...
- Out of scope: ...
- Acceptance criteria:
  - ...
- References:
  - ...

## Story: <name>
- Issue type: Story
- Parent: <epic summary or key>
- Objective: ...
- Scope: ...
- Out of scope: ...
- Acceptance criteria:
  - ...
- Dependencies:
  - ...

### Task: <name>
- Issue type: Task
- Parent: <story summary or key>
- Objective: ...
- Scope: ...
- Out of scope: ...
- Acceptance criteria:
  - ...
- Verification:
  - ...
- Dependencies:
  - ...
- Suggested size: S|M|L
```

When helpful, end with:

- a sequencing section listing recommended implementation order
- a dependency section listing cross-story blockers
- an open-questions section for gaps that prevent responsible sizing

## Quality Bar

Before finishing, confirm:

- every story maps to a real firmware feature or capability slice
- every task has a single coherent implementation goal
- no task hides mobile or backend implementation inside firmware scope
- acceptance criteria are observable and testable
- verification is explicit
- oversized work has been split before issue creation
