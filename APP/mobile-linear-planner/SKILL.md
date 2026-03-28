---
name: mobile-linear-planner
description: Break AirHealth mobile app scope into actionable Linear planning artifacts by reading the PRD in `PM/PRD/PRD.md`, the latest software architecture spec in `SW/Architecture`, and the latest mobile app feature design in `SW/feature-design`. Use when Codex needs to turn those source documents into a mobile delivery plan with one overall epic, one story per mobile feature or capability slice, and task sub-tickets under each story that are small enough to fit in a reviewable sub-1000-line pull request, splitting oversized implementation into multiple tasks before drafting or creating Linear tickets.
---

# Mobile Linear Planner

Translate AirHealth source documents into mobile-app execution work that is immediately usable for Linear planning. Produce one epic for the overall mobile effort, one story per mobile feature or capability slice, and task sub-tickets under each story that are independently implementable, testable, and small enough for a reasonable pull request.

## Required Inputs

Read these inputs in this order:

- `PM/PRD/PRD.md`
- latest markdown architecture spec matching `SW/Architecture/Software_Architecture_Spec_*.md`
- latest markdown mobile design matching `SW/feature-design/Mobile_Feature_Design_*.md`

Read these only when needed:

- `SW/feature-design/Shared_Integration_Appendix_*.md` for contracts shared with firmware, backend, factory tooling, or support tooling
- referenced specs or docs when a mobile task cannot be sized responsibly without them

Prefer markdown sources over exported HTML. If multiple versioned markdown files exist, use the highest version unless the user explicitly names another file.

Treat the documents with this precedence:

1. PRD for product truth and scope intent
2. architecture spec for system boundaries and ownership
3. mobile feature design for implementation handoff detail

If the documents conflict:

- prefer the newest approved source
- keep the conflict visible in assumptions or open questions
- do not hide ambiguity inside a task summary

## Planning Rules

- Create exactly one epic for the overall mobile project, release, or planning slice requested by the user.
- Create one story per distinct mobile feature or capability slice.
- Create only task-level sub-tickets under stories.
- Keep implementation tasks mobile-owned. Put firmware, backend, analytics-pipeline, support-ops, content-ops, or factory-tool work into dependency notes or blocking links instead of mixing them into the same task.
- Keep every implementation task small enough for a reviewable PR. Target under 1000 changed lines including tests, navigation updates, UI state, persistence, mocks, fixtures, and support files that land in the same change.
- Split any task that combines multiple screens, multiple data layers, or multiple independently testable behaviors.
- If a story includes both foundation work and feature-specific behavior, split them into separate tasks when they should not land in the same PR.
- Include verification in the same task only when it is tightly coupled and still reviewable. Otherwise, create a separate verification task.
- Do not create placeholder tickets that have no observable outcome, no acceptance criteria, or no clear mobile owner.
- Every task must be a sub-issue of its corresponding story.
- Encode real prerequisites with explicit Linear blocking relationships. Do not leave true blockers only in prose.

## Workflow

Follow this sequence.

### 1. Build The Mobile Planning Baseline

Extract:

- mobile-facing features and capability slices
- product constraints that affect UX, entitlement, privacy, compatibility, or offline behavior
- architectural boundaries, interfaces, and dependencies
- mobile-owned states, failure modes, observability, and platform constraints
- verification obligations such as UI, reducer, BLE-driven integration, queue replay, export permission, or privacy-sanitization tests

Call out cross-feature foundation work such as action-lock routing, sync queue durability, entitlement caching, analytics schema wiring, or metadata sanitization when it is a real delivery slice rather than incidental implementation detail.

### 2. Define The Epic

Use the overall mobile effort as a single epic.

The epic should capture:

- problem or user value being delivered
- included mobile scope
- explicit exclusions
- exit criteria for the planned slice
- references to the PRD, architecture spec, and mobile design

### 3. Derive Stories

Map each mobile feature or capability slice into one story.

Good story candidates include:

- app shell and one-action-at-a-time route gating
- onboarding, permissions, pairing, and claim flow
- measurement experience and session coordination
- local history, progress views, and sync queue behavior
- entitlement caching and read-only gating
- consult professionals and health export flows
- metadata sanitization and consumer-safe rendering
- compatibility, interruption recovery, and device-readiness handling
- goals and recommendation UX if included in the active source docs

Merge only when two slices cannot be delivered or validated independently.

Split a story when one feature contains a foundational platform slice plus a feature-specific behavior slice that should not land in the same implementation sequence.

If a source design already contains a handoff task table, use it as seed material only. Refine and split it rather than copying it directly into Linear output.

### 4. Break Stories Into Tasks

For each story, create task sub-tickets with:

- one primary objective
- one clear implementation boundary
- concrete acceptance criteria
- concrete verification
- explicit prerequisites or blockers

Prefer task boundaries that align to one of these implementation seams:

- route or navigation surface
- local persistence model or migration
- BLE/session coordinator logic
- entitlement or policy evaluation
- UI screen family or render-state slice
- sync or replay behavior
- privacy-sanitization or export adapter boundary
- analytics instrumentation or verification harness

### 5. Sanity-Check Task Size

Split again if a task would likely require any of the following:

- touching multiple unrelated screens and reducers together
- adding new UI, business logic, persistence, and sync behavior together when those pieces can be staged
- implementing both the happy path and several unrelated recovery paths in one PR
- introducing more than one substantial persistence migration, queue contract, or test harness in the same change
- exceeding roughly a few focused engineer-days or about 1000 changed lines

Usually safe as one task:

- add one route or screen flow on top of existing state and service wiring plus tests
- add one reducer or store slice with focused UI bindings and verification
- add one sync queue behavior to an existing persistence model plus replay tests
- add one sanitization layer or export adapter path with focused platform checks

Usually needs more than one task:

- create a new multi-screen flow, persistence schema, BLE/session logic, and analytics instrumentation together
- implement onboarding, claim, compatibility handling, and retry UX together
- add history rendering, sync reconciliation, and charting/trend logic together
- add entitlement evaluation, stale-cache fallback, and read-only UI gating together

When a task is too large, split by function or implementation seam rather than by vague phases. Prefer slices such as navigation shell first, reducer/store second, screen rendering third, recovery handling fourth.

### 6. Produce Linear-Ready Output

If Linear tooling is available, create the issues directly.

Create them in this order:

1. epic
2. stories parented to the epic
3. tasks parented to their story
4. blocking relationships between issues that cannot proceed independently

When creating dependencies in Linear:

- use parent/sub-issue links for epic -> story and story -> task hierarchy
- use `blocks` relationships for true sequencing dependencies between sibling stories or tasks
- prefer `A blocks B` when B cannot be implemented, verified, or merged responsibly before A ships
- do not create noisy blocking links for soft coordination or optional follow-up work
- if an external blocker does not yet have a Linear issue, keep it in notes and call it out as an open dependency

If Linear tooling is not available, produce ticket-ready content that can be pasted or recreated in Linear without re-deriving the planning structure.

If team, project, workflow state, or labels are ambiguous, infer them from existing related Linear issues when possible. Ask the user only if the ambiguity blocks correct ticket creation.

## Ticket Schema

For every issue, include:

- Issue type: Epic, Story, or Task
- Summary: short action-oriented Linear title
- Parent: epic for stories, story for tasks
- Objective: what becomes possible after the issue ships
- Scope: concrete mobile modules, screens, navigation surfaces, state models, contracts, or platform behaviors to change
- Out of scope: nearby work intentionally excluded
- Acceptance criteria: flat checklist phrased as observable outcomes
- Verification: UI, reducer, integration, replay, export, or manual checks required
- Blocked by: upstream issues or external inputs that must land first
- Blocks: downstream issues that should explicitly depend on this issue
- Notes: assumptions, open questions, or cross-team handoffs

Add these fields when useful:

- labels or components such as `mobile`, `ios`, `android`, `ble`, `pairing`, `measurement`, `history`, `entitlement`, `sync`, `privacy`, `export`
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
- Blocked by:
  - ...
- Blocks:
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
- Blocked by:
  - ...
- Blocks:
  - ...
- Suggested size: S|M|L
```

When helpful, end with:

- a sequencing section listing recommended implementation order
- a dependency section listing cross-story blockers
- an open-questions section for gaps that prevent responsible sizing

## Quality Bar

Before finishing, confirm:

- every story maps to a real mobile feature or capability slice
- every task has a single coherent implementation goal
- no task hides backend or firmware implementation inside mobile scope
- acceptance criteria are observable and testable
- verification is explicit
- oversized work has been split before issue creation
- every task is nested under the correct story
- true prerequisites are represented with explicit blocking relationships
- no task is likely to require a PR materially larger than about 1000 changed lines
