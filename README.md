# Architecture Styles for an Authentication & Authorization Service

SASPS course project: a comparative study of software architectures and design patterns using functionally equivalent authentication and authorization implementations.

## Problem and objectives

The project investigates how architectural choices affect a small authentication and authorization service. The same use cases will be implemented in multiple variants, including a baseline without the investigated patterns and structured alternatives. The goal is to measure trade-offs objectively, not to assume that a pattern is always better.

The comparison focuses on:

- **Performance:** authentication latency and throughput.
- **Changeability:** implementation effort and code impact for fixed change requests, such as adding MFA, changing storage, adding audit logging, or introducing a role/permission.
- **Structure and testability:** coupling, complexity, test coverage, and the effort needed to set up tests.

Results may be positive, neutral, or negative. Conclusions will be based on the measurements and their limitations.

## Research questions and hypotheses

The initial research questions are:

1. How do the selected architectures compare in authentication latency and throughput?
2. How much implementation change is required to add the same feature to each variant?
3. How do their structure and testability differ?

Working hypotheses to verify (or reject) are that more modular variants reduce change impact and improve test isolation, while adding some implementation complexity or runtime overhead. These are hypotheses, not expected outcomes.

## Variants and architecture

All variants should expose the same `auth-api` contract and implement equivalent use cases so that measurements are comparable.

| Variant | Status | Description |
|---|---|---|
| Layered | In progress | Presentation/service/data separation; current runnable baseline. |
| God-class baseline | Planned | Intentionally concentrated logic and dependencies, used as the less-structured comparison. |
| Hexagonal | Planned | Ports and adapters isolate application logic from external systems. |
| Event-driven | Planned | Events decouple selected workflows and reactions. |

Current Maven modules:

| Module | Purpose |
|---|---|
| `auth-api` | Shared service interfaces, DTOs, and typed failures. |
| `auth-support` | Shared hashing/token strategies, event plumbing, and RBAC components. |
| `auth-layered` | Layered implementation and demo composition root. |

## Design patterns and where they are used

These are the patterns present in the current code, without the patterns planned for future variants.

| Pattern / style | How it is used |
|----|-----|
| **Layered architecture** | The demo is the presentation/composition entry point; the service handles authentication use cases; the data package owns user storage abstractions and the in-memory implementation. |
| **Service Layer** | Centralizes registration, login, MFA, token/session lifecycle, and event publication behind the API service contract. |
| **Repository** | Hides in-memory persistence behind interfaces, allowing services to depend on storage contracts rather than map implementations. |
| **Strategy** | The service receives hashing and token behavior through interfaces. There is currently one concrete implementation of each, so alternative strategies can be substituted but are not yet compared. |
| **Observer / publish-subscribe** | The service publishes registration, login, MFA, refresh, and logout events. Listeners subscribe independently; the default-role listener assigns the USER role after registration. |
| **Constructor / dependency injection** | Dependencies are created and wired at the composition root, then passed into consumers through constructors. |


## Implemented so far

- [x] Created a multi-module Maven project targeting Java 17+.
- [x] Defined a shared authentication/authorization API, request/response types, and typed failures.
- [x] Defined registration, login, token verification, refresh-token rotation, logout, and authorization operations in the shared API.
- [x] Defined shared authentication/session operations and an optional MFA capability.
- [x] Added shared password-hashing and token-service abstractions with implementations.
- [x] Added repository abstractions and in-memory user, role, and role-assignment repositories.
- [x] Added RBAC authorization and default-role assignment through an observer/event-bus flow.
- [x] Implemented the layered registration, login, token verification, and authorization demo.
- [x] Added random six-digit MFA challenges that expire after 15 seconds and can be resent.
- [ ] Implement and connect the God-class
- [ ] Implement and connect the hexagonal and event-driven variants.
- [ ] Add automated tests covering equivalent behavior in each variant.
- [ ] Add the planned change requests and measurement scripts/results.
- [] Create the required `M1`, `M2`, and `M3` tags.
- [ ] Produce the proposed architecture and diagrams.
- [ ] Produce the milestone 1 presentation.

## Experimental methodology

### Fair comparison

- Keep use cases, inputs, validation rules, token behavior, and error behavior functionally equivalent across variants.
- Run all variants with the same JDK, machine, JVM settings, dataset, and measurement procedure.
- Separate setup and warm-up from timed measurements; repeat measurements where possible and report the number of runs.
- Record environment details and commands so another team can reproduce the results.
- Do not compare results from different configurations as if they were directly equivalent.

### Measures

| Concern | Measures to report |
|---|---|
| Runtime performance | Authentication latency distribution and throughput under a stated workload. |
| Changeability | Files and lines changed, implementation time, and tests added/changed for each predefined change request. |
| Structure | Module/class dependencies, coupling indicators, and complexity measures, with the selected tools and definitions stated. |
| Testability | Test coverage and the setup/dependency work needed to test core behavior. |

Use a benchmark harness such as JMH for JVM performance measurements where practical. Include raw data alongside summaries and graphs; do not report unmeasured estimates as results. Discuss measurement noise, small-project effects, implementation differences, and other threats to validity.

## Milestones and deliverables

Update the checklists as work progresses. Each presentation must include team roles and contributions, planned versus completed sprint work, difficulties, and next steps.

### Milestone 1 — Week 8: Design and foundation

- [ ] Present the problem, objectives, and proposed application.
- [ ] Explain the investigated patterns/styles, selection rationale, and anticipated benefits.
- [ ] State experimental hypotheses, metrics, and comparison methodology.
- [ ] Present the proposed architecture, diagrams, and implementation variants.
- [ ] Show implementation status and, if available, a demonstration.
- [ ] Present the team, roles, sprint progress, and plan through M2.

### Milestone 2 — Week 12: Implementation and preliminary evaluation

- [ ] Demonstrate the implemented functionality and explain differences between variants.
- [ ] Give a functional demonstration.
- [ ] Run initial experiments and collect comparative measurements.
- [ ] Add preliminary graphs/tables, interpretations, and limitations to the repository.
- [ ] Describe encountered problems and any planning changes.
- [ ] Present member contributions, sprint progress, and plan through M3.

### Milestone 3 — Week 14: Final evaluation and documentation

- [ ] Present the final solution, architecture, and functional demonstration.
- [ ] Document the final experimental methodology and reproduction conditions.
- [ ] Present complete comparative results, graphs, tables, and interpretations.
- [ ] Analyze advantages, disadvantages, trade-offs, and threats to validity.
- [ ] State conclusions, whether hypotheses were supported, contributions, and future work.
- [ ] Summarize final member contributions, planned versus completed work, and lessons learned.
- [ ] Include Abstract, Introduction, Related Work, Methodology, Implementation, Results, Discussion, Threats to Validity, Conclusions, and References in the PDF.

## Sprint and Git practices

- Keep the repository updated throughout the project.
- At each milestone, record team roles, individual contributions, planned versus completed work, difficulties, and next-stage activities.
- Create and maintain Git tags named `M1`, `M2`, and `M3` for the corresponding milestone states.
- The final repository should contain every implementation variant, tests, experiment scripts, raw and summarized results, presentation/documentation deliverables, and reproduction instructions.
- Keep experiment conditions comparable and repeat measurements where practical; document deviations.

## Run the current implementation

Requires JDK 17+ and Maven.

```bash
mvn -q install
mvn -q -pl auth-layered exec:java
```
