---
name: feature-design
description: design implementation-ready software features from a prd and architecture specification. use when a user wants per-feature design packages for firmware, mobile app, and backend, including feature scope, responsibilities, state machines, sequence diagrams, data flow, contracts, error handling, verification, and task breakdowns that can be handed to planning or coding skills.
---

# Feature Design

Translate a PRD plus software architecture specification into implementation-ready feature designs for firmware, mobile app, backend, and supporting systems.

## When To Use This Skill

Use this skill when the user wants one or more software features designed in enough detail that:

- another skill can break the work into plans, tickets, or coding tasks
- firmware, mobile, and backend responsibilities are explicitly separated
- feature behavior is documented with diagrams instead of only prose
- dependencies on APIs, events, storage, and shared components are clear

Do not use this skill for full-system architecture from scratch. Use `software-architecture` first when the system-level architecture is still missing or unstable.

## Expected Inputs

Read these before drafting:

- PRD
- software architecture specification
- optional supporting docs such as API notes, UX flows, hardware constraints, or existing service contracts

Treat the PRD and architecture spec as the primary sources of truth. Separate confirmed facts from inferred feature-design choices.

## Default Deliverable

Produce either:

- one cohesive feature design package with a section per feature, or
- one feature design package per requested feature

Default section structure:

1. Feature summary and inputs reviewed
2. Scope, goals, and non-goals for the feature
3. Requirements baseline
4. Assumptions, open questions, and dependencies
5. Feature decomposition by firmware, mobile, backend, and supporting systems
6. Component responsibilities and interfaces touched
7. Feature state machine
8. Sequence diagrams for primary and failure flows
9. Data flow and persistence model
10. API, BLE, event, and storage contract impacts
11. Error handling, recovery, and observability
12. Verification strategy
13. Delivery breakdown for planning and coding handoff

Prefer Mermaid for diagrams unless the user requests another format.

## Workflow

Follow this sequence.

### 1. Build The Feature Baseline

For each feature, extract:

- user goal and user-visible outcome
- triggering actions and entry points
- state constraints from the PRD
- architecture components already assigned to the feature
- domain-specific constraints for firmware, mobile, backend, and external systems

Create a short baseline with:

- must-have behavior
- should-have behavior
- explicit constraints
- unresolved questions

### 2. Slice The Feature Across Domains

For each feature, partition work into:

- firmware
- mobile app
- backend
- supporting systems such as analytics, admin, notifications, exports, or recommendation services

For each slice, define:

- purpose
- owned logic
- owned state
- inbound and outbound interfaces
- dependencies
- failure boundary

Call out when a feature is:

- firmware-led
- mobile-led
- backend-led
- cross-cutting

### 3. Define The Behavioral Model

Always include:

- a feature state machine using PRD language where possible
- at least one happy-path sequence diagram
- at least one failure or recovery sequence diagram when the feature has interruption risk
- a data flow diagram when the feature touches storage, sync, telemetry, or exports

State machine requirements:

- states
- entry conditions
- exit conditions
- triggers
- guards
- side effects
- persistence expectations
- illegal transitions

Sequence diagram requirements:

- include only the participants relevant to the feature
- label key messages, APIs, or events
- show async behavior, retries, and failure branches when they matter

### 4. Define Contracts And Data Impacts

For every feature, specify the interfaces it changes or depends on:

- firmware-mobile contracts such as BLE commands, events, or result payloads
- mobile-backend APIs
- backend internal events
- local storage entities
- cloud persistence entities
- analytics and audit events

For each contract, include:

- purpose
- producer and consumer
- transport
- schema shape
- validation rules
- versioning and compatibility expectations
- retry and idempotency expectations
- privacy or security notes

### 5. Make The Design Implementation-Ready

End each feature design with a handoff section that another planning or coding skill can consume directly.

Break work down into domain-specific implementation slices with:

- task name
- domain owner: firmware, mobile, backend, or shared
- objective
- files, modules, or services likely to change if known
- dependencies and prerequisites
- acceptance criteria
- test requirements
- sequencing notes

Keep tasks small enough that a downstream skill can turn them into tickets or code changes without having to re-derive the design.

Preferred grouping:

- foundational shared contract work
- firmware tasks
- mobile tasks
- backend tasks
- integration and verification tasks

## Output Guidance

Optimize for engineering handoff, not PRD restatement.

Good outputs:

- use concrete feature names
- map every design choice back to the architecture and PRD
- distinguish existing shared infrastructure from new feature-specific work
- identify cross-team dependencies early
- make state, data, and contract changes explicit

Avoid:

- vague “implement UI” or “build backend” tasks
- diagrams with unlabeled arrows
- feature designs that ignore error paths or recovery behavior
- leaving the task breakdown too coarse for planning or coding follow-up

## Suggested Output Pattern

```markdown
# Feature Design: [Feature Name]

## 1. Summary
## 2. Inputs Reviewed
## 3. Requirements Baseline
## 4. Assumptions And Dependencies
## 5. Domain Decomposition
### 5.1 Firmware
### 5.2 Mobile App
### 5.3 Backend
### 5.4 Supporting Systems
## 6. Behavioral Design
### 6.1 State Machine
### 6.2 Sequence Diagrams
### 6.3 Data Flow
## 7. Contracts And Data Model
## 8. Failure Handling And Observability
## 9. Verification Strategy
## 10. Planning And Coding Handoff
```

## Quality Bar

A good result must:

- be feature-scoped rather than system-scoped
- cover firmware, mobile, and backend when they are relevant
- include diagrams and contract detail, not just prose
- make dependencies and open questions visible
- end in implementation-ready task breakdowns
- be directly usable by a planning or coding skill in the next step
