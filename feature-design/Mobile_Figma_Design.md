# AirHealth Mobile Figma Design Package

## 1. Overview

- Purpose: translate the latest AirHealth mobile feature design into a Figma-ready mobile design package for consumer app flows.
- Primary source of truth: `SW/feature-design/Mobile_Feature_Design_v0.2.md`
- Supporting source used: `SW/feature-design/Shared_Integration_Appendix_v0.2.md`
- Output intent: another designer should be able to create frames, component variants, and prototype links in Figma without re-deriving product behavior.
- Scope boundary: consumer mobile app only. Do not include firmware internals, factory tooling UI, backend admin UI, or internal support tooling.

## 2. Input Documents Used

- [`Mobile_Feature_Design_v0.2.md`](/Users/haohua/coding/AirHealth/SW/feature-design/Mobile_Feature_Design_v0.2.md)
- [`Shared_Integration_Appendix_v0.2.md`](/Users/haohua/coding/AirHealth/SW/feature-design/Shared_Integration_Appendix_v0.2.md)

## 3. Design Principles And Assumptions

- Mobile is the primary instructional and status surface for consumer flows.
- One user action can be active at a time across measure, goals, suggestion, history mutation, and support entry.
- Device-authoritative events drive measurement progress and completion; the app must never invent successful completion.
- Internal-only data such as `HW-ID`, detected VOC type, factory logs, or Factory mode controls must not appear in consumer-facing frames.
- Low-power, reconnect, entitlement, and export states must feel recoverable rather than broken.
- The system should feel more like a guided wellness product than a diagnostics console.
- iOS and Android can share the same core frame set, with annotation callouts for platform-specific permission sheets and health-export destinations.

## 4. Navigation And Information Architecture Impact

- Primary navigation assumption: `Home`, `History`, `Goals`, `Sharing`, `Account`.
- Home is the main operational surface. It shows device readiness, entitlement state, and feature entry points.
- Feature detail functions as a task hub, not a generic information page.
- Result, support, export, and recovery routes should preserve feature context so the user can return to the same feature path.
- Read-only and temporary-access states should not remove history visibility. They should only constrain creation of new actions.

## 5. Screen Inventory

Build these frame families in Figma.

| Frame ID | Frame name | Purpose | Key variants |
| --- | --- | --- | --- |
| 01 | Onboarding / Welcome | Introduce product, trial, and setup | default, returning-user shortcut |
| 02 | Pairing / Permissions | Request Bluetooth access and explain why | default, denied, limited |
| 03 | Pairing / Device Discovery | Find and select device | scanning, device found, none found, timeout |
| 04 | Pairing / Claim Result | Confirm ownership binding and compatibility | success, incompatible, retry |
| 05 | Home / Ready | Main dashboard with device status and feature cards | ready, disconnected, low-power-ready, temporary-access, read-only |
| 06 | Feature Hub / Oral | Oral feature task hub | ready, blocked-action, action-locked |
| 07 | Feature Hub / Fat | Fat feature task hub | ready, blocked-action, action-locked |
| 08 | Goals / Edit | Create or revise feature goal | create, edit, suggestion-applied, validation-error |
| 09 | Suggestion / Result | Show optional goal or next-step suggestions | default, unavailable, dismissed |
| 10 | Measure / Oral Prep | Instruction screen before oral measurement | ready, blocked, disconnected |
| 11 | Measure / Oral Live | Live guided oral measurement | warming, measuring, low-power-interrupted, disconnecting |
| 12 | Measure / Fat Prep | Instruction screen before fat session | ready, blocked, disconnected |
| 13 | Measure / Fat Live | Repeated-reading coach | first-reading, current-reading, best-delta, target-hit, interrupted |
| 14 | Result / Oral Summary | Show oral score and progress | default, synced, pending-sync |
| 15 | Result / Fat Summary | Show fat session summary | default, target-met, pending-sync |
| 16 | History / Overview | List sessions and trend snapshots | populated, empty, read-only |
| 17 | History / Detail | Show selected session detail | oral, fat, pending-sync |
| 18 | Support / Directory | Curated support resources | oral-context, fat-context, no-local-results |
| 19 | Support / External Handoff | Confirm leaving app to external destination | web, phone, unavailable |
| 20 | Entitlement / Status | Explain active, temporary, or read-only access | active, temporary, read-only, restore-failed |
| 21 | Recovery / Reconnect | Recover after disconnect or app relaunch | reconnecting, replay-success, replay-failed |
| 22 | Sharing / Settings | Configure health-export destinations | iOS connected, iOS denied, Android connected, Android denied |
| 23 | Sharing / Export Result | Confirm export outcome | success, failed, unsupported |
| 24 | Global / Blocking Modal | Reusable blocking explanation layer | entitlement-block, action-lock, incompatible-device, not-ready |

## 6. Screen Specifications

### 01. Onboarding / Welcome

- Purpose: introduce value, explain trial framing, and move users into setup.
- Entry points: first launch, signed-out return with no paired device.
- Preconditions: app installed, no completed pairing flow.
- Layout hierarchy: hero statement, two-mode summary, setup steps, trial note, primary CTA.
- Key content: product promise, device requirement, brief mention of Oral and Fat modes, `Get started`.
- Controls and states: `Get started`, `Sign in`, `Learn more`.
- Variants: default, returning-user shortcut.
- Transitions: primary CTA to permissions; sign-in to account flow outside this package.
- Figma annotation notes: call out that legal/trial microcopy can be shorter in UI than in spec docs.

### 02 to 04. Pairing Flow

- Purpose: get from permission request to paired-and-claimed readiness.
- Entry points: onboarding, `Add device`, reconnect with missing claim.
- Preconditions: powered device nearby.
- Layout hierarchy: progress stepper, device status card, help copy, primary action.
- Key content:
  - permission rationale before OS prompt
  - nearby device list using consumer-safe labels only
  - claim success and compatibility confirmation
- Controls and states: allow Bluetooth, retry scan, select device, cancel, retry claim.
- Error variants: permission denied, no device found, timeout, incompatible device, claim failed.
- Transitions:
  - permissions granted -> discovery
  - device selected -> claim
  - claim success -> Home / Ready
- Figma annotation notes: use one pairing shell with content swaps rather than separate unrelated layouts.

### 05. Home / Ready

- Purpose: give a single operational landing surface for status and feature entry.
- Entry points: post-pairing, app relaunch, back from any feature flow.
- Preconditions: account available, app can derive device and entitlement state.
- Layout hierarchy: top status region, feature cards, recent result preview, secondary account or sharing entry.
- Key content:
  - device state banner
  - entitlement banner
  - Oral and Fat feature cards
  - optional recent-result preview
- Controls and states:
  - tap feature card
  - manage subscription
  - open history, goals, sharing, account via nav
- Variants:
  - ready
  - disconnected
  - low-power-ready
  - temporary-access
  - read-only
- Transitions:
  - feature card -> matching feature hub
  - manage subscription -> Entitlement / Status
- Figma annotation notes: blocked states should explain what changes next rather than just disabling the whole page.

### 06 to 07. Feature Hub / Oral and Fat

- Purpose: let users pick one allowed action from a consistent task hub.
- Entry points: tap feature card from Home, return from result or history.
- Preconditions: feature selected.
- Layout hierarchy: feature header, action row, status banner, lightweight context content.
- Key content:
  - action row with `set goals`, `view history`, `measure`, `get suggestion`, `consult professionals`
  - feature-specific status and last-known progress
- Controls and states:
  - one action enabled at a time
  - other actions disabled when another flow is active
- Variants:
  - ready
  - action-locked
  - entitlement-blocked
  - disconnected
  - low-power-ready
- Transitions: each action goes to its owning screen family.
- Figma annotation notes: keep identical action order across Oral and Fat; difference should come from context copy and progress content, not layout structure.

### 08. Goals / Edit

- Purpose: set or adjust a goal for the selected feature.
- Entry points: `set goals`, suggestion acceptance, first-use setup.
- Preconditions: selected feature context.
- Layout hierarchy: goal explanation, input area, suggestion chip if available, save CTA.
- Key content:
  - current goal or empty state
  - editable target
  - optional AI suggestion
- Controls and states: edit, accept suggestion, save, cancel.
- Variants: create, edit, suggestion-applied, validation-error.
- Transitions: save -> feature hub with updated goal summary; cancel -> previous feature hub.
- Figma annotation notes: show numeric entry treatment and helper text, but avoid overdesigning copy specifics until PM finalizes wording.

### 09. Suggestion / Result

- Purpose: show optional guidance without making it look diagnostic.
- Entry points: `get suggestion`, post-result secondary action.
- Preconditions: feature context available.
- Layout hierarchy: context header, suggestion cards, rationale, dismiss CTA.
- Key content: recommended next step, optional goal adjustment, contextual disclaimer.
- Controls and states: accept, dismiss, open goals.
- Variants: default, unavailable, dismissed.
- Transitions: accept -> goals or feature hub depending on outcome; dismiss -> feature hub.
- Figma annotation notes: visual language should distinguish suggestions from measured results.

### 10 to 13. Measurement Screens

- Purpose: coach the user through preparation and live measurement.
- Entry points: `measure` from feature hub.
- Preconditions: feature selected, action unlocked, entitlement allows start, device sufficiently ready.
- Shared layout hierarchy: top status banner, step card, instructional illustration or animation zone, progress region, sticky action area.
- Oral prep key content:
  - breathing prep
  - mouth placement instruction
  - start CTA
- Oral live variants:
  - warming
  - measuring
  - low-power-interrupted
  - disconnecting
- Fat prep key content:
  - hold-and-blow guidance
  - repeated-reading explanation
  - start CTA
- Fat live variants:
  - first-reading
  - current-reading
  - best-delta
  - target-hit
  - interrupted
- Controls and states: start, cancel, finish where applicable, retry after recoverable interruption.
- Transitions:
  - prep start -> live measurement
  - valid completion -> matching result summary
  - disconnect -> Recovery / Reconnect
  - blocked readiness -> Blocking Modal
- Figma annotation notes: no live UI should imply final success before receipt of device-confirmed terminal result.

### 14 to 15. Result Summaries

- Purpose: show completed outcome, progress, and next actions.
- Entry points: successful oral or fat measurement, replay success.
- Preconditions: confirmed completed summary payload available.
- Layout hierarchy: primary metric card, baseline or delta explanation, trend snippet, next actions.
- Key content:
  - Oral: score, baseline reference, progress direction
  - Fat: session delta, best delta, target state
  - sync status label when pending
- Controls and states: share, view history, `get suggestion`, `consult professionals`, retake if allowed.
- Variants:
  - oral default
  - oral pending-sync
  - fat default
  - fat target-met
  - fat pending-sync
- Transitions: share -> Sharing / Export Result or Sharing / Settings; history -> History / Detail; support -> Support / Directory.
- Figma annotation notes: use the same frame skeleton for Oral and Fat so the metric treatment can differ without changing the whole page structure.

### 16 to 17. History

- Purpose: show durable session history and selected-session detail.
- Entry points: `view history`, result summary, bottom navigation.
- Preconditions: local or synced history available, or empty history state.
- Layout hierarchy: filter controls, trend summary, session list or detail content, secondary actions.
- Key content:
  - trend overview by feature
  - pending or synced state labels
  - detail screen with summary plus export and support entry where allowed
- Controls and states: change range, open session, export completed session, `consult professionals`.
- Variants: populated, empty, read-only, oral detail, fat detail, pending-sync detail.
- Transitions: select session -> History / Detail; export -> Sharing flow; support -> Support / Directory.
- Figma annotation notes: read-only mode should change available CTAs, not erase the user’s past data.

### 18 to 19. Support

- Purpose: offer curated external educational and professional resources without implying data sharing or in-app booking.
- Entry points: feature hub, result summary, history detail.
- Preconditions: no conflicting in-progress action.
- Layout hierarchy: safety notice, recommended resources, resource metadata, external handoff confirmation.
- Key content:
  - feature-specific directory
  - `no health data shared` notice
  - destination type and hours where available
- Controls and states: open resource, return, change resource.
- Variants:
  - oral-context
  - fat-context
  - no-local-results
  - external web handoff
  - external phone handoff
  - unavailable
- Transitions: open resource -> external app/browser after handoff confirmation; dismiss -> feature context.
- Figma annotation notes: visually separate this flow from measurement and results. It should feel like a supported exit, not a core product action.

### 20. Entitlement / Status

- Purpose: explain current access state and recovery options.
- Entry points: blocked measurement, account or billing entry, Home banner CTA.
- Preconditions: entitlement state known or cached.
- Layout hierarchy: state headline, what is available now, what is blocked, recovery CTA.
- Key content:
  - active
  - temporary access
  - read-only mode
  - restore failure
- Controls and states: renew, restore, refresh, dismiss.
- Transitions: successful recovery -> Home / Ready or feature hub; dismiss -> previous screen.
- Figma annotation notes: make temporary access visually distinct from both active and expired states.

### 21. Recovery / Reconnect

- Purpose: recover from disconnect, relaunch, or replay.
- Entry points: measurement disconnect, app relaunch with in-flight session data, BLE reconnect requirement.
- Preconditions: prior session exists or reconnect attempt active.
- Layout hierarchy: status headline, session recovery explanation, progress or outcome card, primary CTA.
- Key content: reconnecting status, replay result, failure explanation, next best action.
- Controls and states: retry reconnect, return to feature hub, continue to recovered result.
- Variants: reconnecting, replay-success, replay-failed.
- Transitions:
  - replay-success -> result summary
  - replay-failed -> feature hub
- Figma annotation notes: preserve feature context and session language so the user knows what was recovered.

### 22 to 23. Sharing

- Purpose: configure and complete summary export to Apple Health or Health Connect.
- Entry points: result summary, Sharing tab, history detail.
- Preconditions: completed summary exists for export action.
- Layout hierarchy: destination status, what is shared, permission CTA, export outcome.
- Key content:
  - connected or denied state
  - completed summary only disclosure
  - unsupported-field omission note
- Controls and states: connect, disconnect, retry export.
- Variants:
  - iOS connected
  - iOS denied
  - Android connected
  - Android denied
  - export success
  - export failed
  - unsupported
- Transitions: permission grant -> export outcome; dismiss -> prior screen.
- Figma annotation notes: keep destination-specific legal copy in annotations, not fully hardcoded in frame text.

### 24. Global / Blocking Modal

- Purpose: provide one reusable explanation pattern for blocked actions.
- Entry points: measure blocked, action lock conflict, incompatible device, not-ready state.
- Preconditions: blocking reason available.
- Layout hierarchy: reason icon, short headline, one-sentence explanation, recovery CTA.
- Key content: normalized reason code mapped to user-safe message.
- Controls and states: dismiss, go to subscription, retry, return to feature hub.
- Variants: entitlement-block, action-lock, incompatible-device, not-ready.
- Figma annotation notes: one component-driven modal is preferable to custom blockers scattered across screens.

## 7. States And Variants

Global variants that need explicit frame or component treatment:

- device connected
- device disconnected
- low-power-ready
- action locked
- active entitlement
- temporary access
- read-only mode
- sync pending
- empty history
- export denied
- export failed
- reconnecting
- replay success
- replay failure
- incompatible device
- device not ready

States that must never appear in consumer Figma outputs:

- Factory mode entry
- Factory pass or fail
- `HW-ID`
- detected VOC type
- factory logs
- manufacturing-only controls

## 8. Components And Variants

| Component | Variants | Notes |
| --- | --- | --- |
| Feature card | oral, fat, ready, blocked, selected | includes status pill and last-result preview |
| Action row | default, action-locked, blocked-by-entitlement, blocked-by-device | same ordering across features |
| Status banner | ready, disconnected, low-power-ready, temporary-access, read-only | top-of-screen reusable system state pattern |
| Measurement step card | oral-prep, oral-live, fat-prep, fat-live, recovery | central guidance module |
| Result card | oral, fat, pending-sync, target-met | supports summary metric and helper explanation |
| Trend snippet | improving, neutral, declining, no-data | compact history preview and result follow-up |
| Entitlement banner | active, temporary, read-only | CTA varies by state |
| Blocking modal | entitlement, action-lock, incompatible, not-ready | uses shared recovery action slot |
| Resource row | website, phone, unavailable | used in support directory |
| Export destination row | Apple Health connected/denied, Health Connect connected/denied | icon and CTA vary by platform |
| Recovery card | reconnecting, recovered, unrecoverable | used in replay and resume flows |

Component annotations required in Figma:

- which props are driven by feature context
- which labels are dynamic
- which variants are platform-specific
- which status colors require text support for accessibility

## 9. Prototype Flows

Wire these prototype flows in Figma:

1. Happy path oral measurement:
   `Home / Ready` -> `Feature Hub / Oral` -> `Measure / Oral Prep` -> `Measure / Oral Live` -> `Result / Oral Summary`
2. Happy path fat session:
   `Home / Ready` -> `Feature Hub / Fat` -> `Measure / Fat Prep` -> `Measure / Fat Live` -> `Result / Fat Summary`
3. Interrupted measurement recovery:
   `Measure / Oral Live` or `Measure / Fat Live` -> `Recovery / Reconnect` -> `Result Summary` or `Feature Hub`
4. Entitlement block:
   `Feature Hub` -> `Global / Blocking Modal` -> `Entitlement / Status`
5. Support handoff:
   `Feature Hub` or `Result Summary` -> `Support / Directory` -> `Support / External Handoff`
6. Export flow:
   `Result Summary` -> `Sharing / Settings` -> `Sharing / Export Result`

For each prototype connection, annotate:

- trigger action
- transition type
- whether the source screen preserves scroll position
- whether the user returns to feature context or Home

## 10. Accessibility And Content Rules

- Do not rely on color alone for device, entitlement, or export state.
- Every blocked or warning state needs a plain-language title and action-oriented recovery message.
- Support links must identify destination type for screen readers.
- Measurement screens should support reduced motion alternatives for instructional animation.
- Numeric results need adjacent text labels describing meaning, not chart-only communication.
- Temporary access and read-only states must use unambiguous labels; avoid vague paywall language.
- Keep wording consumer-safe and wellness-oriented. Do not imply diagnosis or internal hardware diagnostics.

## 11. Open Questions And Risks

- The mobile feature design leaves open whether the app should show a generic `device not ready` state for units that are still factory-eligible but not properly provisioned. Use a consumer-safe placeholder variant until product finalizes this.
- If backend compatibility responses are too technical, the UI could accidentally leak internal hardware distinctions. Review final API copy before design freeze.
- Shared summaries and export copy still need final platform-specific legal wording.
- The reconnect and replay UX depends on the final BLE replay contract being stable enough to distinguish recoverable from unrecoverable sessions.
- If one-action locking is not visually consistent between Home, feature hub, and result screens, the product may feel randomly blocked.
