# Architecture Styles for an Authentication & Authorization Service

SASPS course project: comparative study of design patterns and software architectures versus their associated anti-patterns.

## Goal

Build the same login, token and authorization service in several architectures (layered, hexagonal, event-driven, plus a naive God-class baseline) behind one shared contract. Then compare them on:

- **Performance:** authentication latency and throughput.
- **Changeability:** how much code must change for each change request (new hashing algorithm, MFA, file to database storage, audit logging, new role/permission).
- **Structure:** coupling, complexity and test setup cost.

Cryptography is kept simple on purpose. The study is about architecture, not security engineering.

## Packages

| Module | Purpose |
|---|---|
| `auth-api` | The contract every architecture implements: service interfaces, DTOs, typed failures. No logic. |
| `auth-support` | Shared building blocks so they are not a variable in the comparison: hashing and token strategies, event plumbing, RBAC. |
| `auth-layered` | First architecture (Presentation, Service, Data) plus a `Demo` composition root. |

Dependencies point one way: `auth-layered` → `auth-support` → `auth-api`.

## Patterns implemented

- **Strategy:** password hashing, token scheme, authorization policy.
- **Repository:** users, roles and role assignments behind interfaces.
- **Dependency Injection:** constructor injection only; `Demo` is the single place that knows concrete classes.
- **Observer:** event bus for registration and login events; default role assignment reacts to them.

## Anti-patterns

None implemented yet. The God-class baseline (God `AuthManager`, hard-coded algorithms, singleton/global state, one giant if/else, scattered role checks) is the next module to build, so every metric has a "before".

## Possible next steps

- Unit tests for the token, service and RBAC classes.
- File-backed repositories (the storage baseline for the database change request).
- Chain of Responsibility for the login pipeline (rate limit, credentials, MFA, permission).
- Decorators for logging, rate limiting and caching.
- Adapter for storage backends.
- God-class, hexagonal and event-driven versions.
- Shared HTTP adapter and minimal UI.
- TOTP multi-factor authentication.
- Benchmarks (JMH) and change-impact measurements.

## Run

Requires JDK 17+ and Maven.

```bash
mvn -q install
mvn -q -pl auth-layered exec:java
```