---
name: backend-linear-planner
description: Break AirHealth backend scope into actionable Linear planning artifacts by reading the PRD in `PM/PRD/PRD.md`, the latest software architecture spec in `SW/Architecture`, and the latest backend feature design in `SW/feature-design`. Use when Codex needs to turn those source documents into a backend delivery plan with one overall epic, one story per backend feature or capability slice, and task sub-tickets under each story that are small enough to fit in a reviewable sub-1000-line pull request, splitting oversized implementation into multiple tasks before drafting or creating Linear tickets.
---

# Backend Linear Planner

Translate AirHealth source documents into backend execution work that is immediately usable for Linear planning. Produce one epic for the overall backend effort, one story per backend feature or capability slice, and task sub-tickets under each story that are independently implementable, testable, and small enough for a reasonable pull request.

## Required Inputs

Read these inputs in this order:

- `PM/PRD/PRD.md`
- latest markdown architecture spec matching `SW/Architecture/Software_Architecture_Spec_*.md`
- latest markdown backend design matching `SW/feature-design/Backend_Feature_Design_*.md`

Read these only when needed:

- `SW/feature-design/Shared_Integration_Appendix_*.md` for contracts shared with firmware, mobile, factory tooling, or support tooling
- referenced specs or docs when a backend task cannot be sized responsibly without them

Prefer markdown sources over exported HTML. If multiple versioned markdown files exist, use the highest version unless the user explicitly names another file.

Treat the documents with this precedence:

1. PRD for product truth and scope intent
2. architecture spec for system boundaries and ownership
3. backend feature design for implementation handoff detail

If the documents conflict:

- prefer the newest approved source
- keep the conflict visible in assumptions or open questions
- do not hide ambiguity inside a task summary

## Planning Rules

- Create exactly one epic for the overall backend project, release, or planning slice requested by the user.
- Create one story per distinct backend feature or capability slice.
- Create only task-level sub-tickets under stories.
- Keep implementation tasks backend-owned. Put firmware, mobile, analytics, support-ops, factory-tool, or content-ops work into dependency notes instead of mixing them into the same task.
- Keep every implementation task small enough for a reviewable PR. Target under 1000 changed lines including tests, migrations, schemas, fixtures, and support files that land in the same change.
- Split any task that combines multiple services, multiple independently testable behaviors, or a broad cross-cutting refactor.
- If a story includes both foundation work and feature-specific behavior, split them into separate tasks when they should not land in the same PR.
- Include verification in the same task only when it is tightly coupled and still reviewable. Otherwise, create a separate verification task.
- Do not create placeholder tickets that have no observable outcome, no acceptance criteria, or no clear backend owner.

## Workflow

Follow this sequence.

### 1. Build The Backend Planning Baseline

Extract:

- backend-facing features and capability slices
- product constraints that affect contracts, data ownership, or service behavior
- architectural boundaries, interfaces, and dependencies
- backend-owned states, failure modes, observability, and compliance guardrails
- verification obligations such as unit, integration, contract, migration, replay, idempotency, or operational tests

Call out cross-feature foundation work such as shared auth middleware, event schemas, profile normalization, audit plumbing, or storage abstractions when it is a real delivery slice rather than incidental implementation detail.

### 2. Define The Epic

Use the overall backend effort as a single epic.

The epic should capture:

- problem or user value being delivered
- included backend scope
- explicit exclusions
- exit criteria for the planned slice
- references to the PRD, architecture spec, and backend design

### 3. Derive Stories

Map each backend feature or capability slice into one story.

Good story candidates include:

- device claim and registry
- session summary ingestion and history queries
- goals and recommendation support
- entitlement snapshot and gating contracts
- support directory content service
- factory record ingestion and diagnostics persistence
- device-profile normalization and routing
- analytics, audit, and support views
- OTA release metadata service

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

- API or contract surface
- persistence model or migration
- normalization or routing logic
- entitlement or policy evaluation
- event emission or audit instrumentation
- background job or reconciliation flow
- diagnostics or support/admin exposure
- contract, integration, or operational verification

### 5. Sanity-Check Task Size

Split again if a task would likely require any of the following:

- touching multiple unrelated services or domains
- adding a new API plus business logic plus storage changes together when those pieces can be staged
- implementing both the happy path and several unrelated recovery paths in one PR
- introducing more than one substantial migration, job, or test harness in the same change
- exceeding roughly a few focused engineer-days or about 1000 changed lines

Usually safe as one task:

- add one API endpoint on top of an existing service and persistence model plus tests
- add one idempotent write path for an already-defined entity
- add one normalization rule family to an existing mapper plus verification
- wire one audit or analytics event family through existing observability plumbing

Usually needs more than one task:

- create a new service, persistence layer, and public API contract together when they can be sequenced
- implement session ingestion, history queries, and analytics emission together
- add entitlement evaluation, cache-freshness policy, and fallback behavior together
- add factory ingestion, diagnostics bundle handling, and support lookup surfaces together

When a task is too large, split by function or implementation seam rather than by vague phases. Prefer slices such as schema first, write path second, read path third, or core logic first and recovery handling second.

### 6. Produce Linear-Ready Output

If Linear tooling is available, create the issues directly.

Create them in this order:

1. epic
2. stories parented to the epic
3. tasks parented to their story

If Linear tooling is not available, produce ticket-ready content that can be pasted or recreated in Linear without re-deriving the planning structure.

If team, project, workflow state, or labels are ambiguous, infer them from existing related Linear issues when possible. Ask the user only if the ambiguity blocks correct ticket creation.

## Ticket Schema

For every issue, include:

- Issue type: Epic, Story, or Task
- Summary: short action-oriented Linear title
- Parent: epic for stories, story for tasks
- Objective: what becomes possible after the issue ships
- Scope: concrete backend modules, services, contracts, data models, or operational behaviors to change
- Out of scope: nearby work intentionally excluded
- Acceptance criteria: flat checklist phrased as observable outcomes
- Verification: unit, integration, contract, migration, replay, or manual checks required
- Dependencies: upstream or downstream issues, external inputs, or blocking questions
- Notes: assumptions, open questions, or cross-team handoffs

Add these fields when useful:

- labels or components such as `backend`, `api`, `storage`, `auth`, `history`, `entitlement`, `factory`, `analytics`, `ota`, `device-profile`
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

- every story maps to a real backend feature or capability slice
- every task has a single coherent implementation goal
- no task hides firmware or mobile implementation inside backend scope
- acceptance criteria are observable and testable
- verification is explicit
- oversized work has been split before issue creation
- no task is likely to require a PR materially larger than about 1000 changed lines
