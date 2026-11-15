# Architecture

Diagrams for the current implementation and the proposed variants. Solid green boxes are implemented, grey boxes are shared components from `auth-support`, and dashed amber boxes are proposals for M2 that the team still has to confirm.

## 1. Modules

Every variant implements the same `auth-api` contract, so the same tests and benchmarks can run against all of them.

```mermaid
flowchart LR
    classDef done fill:#E6F4F1,stroke:#0E7C66,color:#132033
    classDef planned fill:#FFF7ED,stroke:#B45309,stroke-dasharray:5 4,color:#132033

    api["<b>auth-api</b><br/>AuthService, MfaCapableAuthService,<br/>AuthorizationService, DTOs, AuthFailure"]:::done
    support["<b>auth-support</b><br/>hashing and token strategies,<br/>event bus, RBAC"]:::done
    layered["<b>auth-layered</b><br/>layered variant + demo"]:::done
    god["<b>god-class variant</b>"]:::planned
    hex["<b>hexagonal variant</b>"]:::planned
    evt["<b>event-driven variant</b>"]:::planned
    tests["<b>contract tests</b><br/>written against auth-api,<br/>run on every variant"]:::planned
    bench["<b>JMH benchmarks</b><br/>same workload<br/>for every variant"]:::planned

    support --> api
    layered --> support
    god --> api
    hex --> support
    evt --> support
    tests --> api
    bench --> api
```

Arrows point from a module to the module it depends on. Tests and benchmarks are written only against `auth-api`; each variant provides a factory for its configured service, so the same suite runs unchanged on every variant. The god-class variant is drawn without `auth-support` because its purpose is to concentrate the logic in one place; it should still use the same hashing and token algorithms so that performance stays comparable.

## 2. Layered variant (implemented)

```mermaid
flowchart TB
    classDef done fill:#E6F4F1,stroke:#0E7C66,color:#132033
    classDef shared fill:#F2F5F7,stroke:#5B6675,color:#132033

    subgraph presentation["Presentation"]
        demo["Demo<br/>console UI and composition root:<br/>creates and wires all objects"]:::done
    end
    subgraph service["Service layer"]
        svc["LayeredAuthService<br/>implements MfaCapableAuthService"]:::done
    end
    subgraph data["Data layer"]
        repo["UserRepository<br/>InMemoryUserRepository"]:::done
    end
    subgraph supportmod["auth-support (shared)"]
        hasher["PasswordHasher<br/>Sha256SaltedHasher"]:::shared
        tokens["TokenService<br/>HmacTokenService"]:::shared
        bus["AuthEventPublisher<br/>SimpleEventBus"]:::shared
        assigner["DefaultRoleAssigner<br/>event listener"]:::shared
        assignments["RoleAssignmentRepository<br/>InMemoryRoleAssignmentRepository"]:::shared
        roles["RoleRepository<br/>InMemoryRoleRepository"]:::shared
        rbac["RbacAuthorizationService<br/>implements AuthorizationService"]:::shared
    end

    demo -->|"register, login, completeMfa,<br/>resendMfa, verify, refresh, logout"| svc
    demo -->|"isAllowed"| rbac
    svc --> repo
    svc --> hasher
    svc --> tokens
    svc -->|"publish events"| bus
    bus -->|"UserRegistered"| assigner
    assigner -->|"assign USER role"| assignments
    rbac -->|"rolesOf"| assignments
    rbac -->|"findByName"| roles
```

The demo also wraps the event publisher so that every event is appended to `auth-events.log`, and it supplies the MFA code delivery callback: the callback stores the code and the demo prints it.

## 3. Design patterns in the current code

```mermaid
classDiagram
    direction LR
    class MfaCapableAuthService {
        <<interface>>
        +completeMfa(request) AuthSession
        +resendMfa(challengeToken) MfaRequired
    }
    class LayeredAuthService
    class UserRepository {
        <<interface>>
        +findByUsername(username) Optional~User~
        +saveIfAbsent(user) boolean
    }
    class InMemoryUserRepository
    class PasswordHasher {
        <<interface>>
        +hash(password) String
        +verify(password, storedHash) boolean
    }
    class Sha256SaltedHasher
    class TokenService {
        <<interface>>
        +issue(username) AuthToken
        +verify(token) Identity
    }
    class HmacTokenService
    class AuthEventPublisher {
        <<interface>>
        +publish(event)
    }
    class SimpleEventBus {
        +subscribe(listener)
    }
    class AuthEventListener {
        <<interface>>
        +onEvent(event)
    }
    class DefaultRoleAssigner

    MfaCapableAuthService <|.. LayeredAuthService : Service Layer
    LayeredAuthService --> UserRepository : Repository
    UserRepository <|.. InMemoryUserRepository
    LayeredAuthService --> PasswordHasher : Strategy
    PasswordHasher <|.. Sha256SaltedHasher
    LayeredAuthService --> TokenService : Strategy
    TokenService <|.. HmacTokenService
    LayeredAuthService --> AuthEventPublisher : Observer
    AuthEventPublisher <|.. SimpleEventBus
    SimpleEventBus o-- AuthEventListener : subscribers
    AuthEventListener <|.. DefaultRoleAssigner
```

All collaborators are passed to `LayeredAuthService` through its constructor (dependency injection); `Demo` is the composition root.

## 4. Login with MFA (layered variant)

```mermaid
sequenceDiagram
    autonumber
    actor C as Client (Demo)
    participant S as LayeredAuthService
    participant R as UserRepository
    participant H as PasswordHasher
    participant T as TokenService
    participant B as AuthEventPublisher

    C->>S: login(username, password)
    S->>R: findByUsername(username)
    opt user exists
        S->>H: verify(password, passwordHash)
    end
    alt invalid credentials
        S->>B: publish(LoginFailed)
        S-->>C: AuthException(INVALID_CREDENTIALS)
    else valid credentials
        S->>B: publish(MfaChallengeIssued)
        S-->>C: MfaRequired(challengeToken, expiresAt)
        Note over C,S: 6-digit code delivered out of band, valid for 15 s
        C->>S: completeMfa(challengeToken, code)
        Note over S: checks expiry and code,<br/>then removes the challenge (single use)
        S->>B: publish(LoginSucceeded)
        S->>T: issue(username)
        S-->>C: AuthSession(access token, refresh token)
        C->>S: verify(access token)
        S->>T: verify(token)
        S-->>C: Identity
    end
```

If a challenge issued at registration is still valid, `login` returns that challenge instead of issuing a new one (no new `MfaChallengeIssued` event).

## 5. Proposed variants (to be confirmed for M2)

The diagrams show the target state. Each new variant first implements the base `AuthService` and gets MFA (change request CR2) in a separate pull request, so that the cost of the change can be measured.

### 5.1 God class (anti-pattern baseline)

As described in the original project plan: a single `AuthManager` that still implements the shared contract, so the same tests and benchmarks apply.

```mermaid
flowchart LR
    classDef planned fill:#FFF7ED,stroke:#B45309,stroke-dasharray:5 4,color:#132033
    client["Demo, tests, benchmarks"] --> god["<b>AuthManager</b> (god class)<br/>implements MfaCapableAuthService<br/><br/>hard-coded algorithms<br/>singleton / global state<br/>one large if/else<br/>role checks scattered in the code"]:::planned
```

### 5.2 Hexagonal (ports and adapters)

The outbound ports belong to the core: the core defines them and depends only on them. Each dotted arrow points from an adapter to the port it implements.

```mermaid
flowchart LR
    classDef planned fill:#FFF7ED,stroke:#B45309,stroke-dasharray:5 4,color:#132033

    subgraph driving["Driving adapters"]
        cli["Demo"]:::planned
        tst["Contract tests"]:::planned
        jmh["Benchmarks"]:::planned
    end
    subgraph core["Application core"]
        inport["Inbound port<br/>MfaCapableAuthService"]:::planned
        usecases["Use cases<br/>register, login, MFA,<br/>refresh, logout"]:::planned
        subgraph outports["Outbound ports"]
            store["UserStore"]:::planned
            hashp["PasswordHashing"]:::planned
            tokp["TokenIssuer"]:::planned
            mfap["MfaCodeSender"]:::planned
            evp["EventSink"]:::planned
        end
    end
    subgraph driven["Driven adapters"]
        mem["In-memory store"]:::planned
        sha["SHA-256 hasher"]:::planned
        hmac["HMAC tokens"]:::planned
        con["Console sender"]:::planned
        ebus["Event bus"]:::planned
    end

    cli --> inport
    tst --> inport
    jmh --> inport
    inport --> usecases
    usecases --> store & hashp & tokp & mfap & evp
    store ~~~ mem
    hashp ~~~ sha
    tokp ~~~ hmac
    mfap ~~~ con
    evp ~~~ ebus
    mem -.-> store
    sha -.-> hashp
    hmac -.-> tokp
    con -.-> mfap
    ebus -.-> evp
```

### 5.3 Event-driven

```mermaid
flowchart LR
    classDef planned fill:#FFF7ED,stroke:#B45309,stroke-dasharray:5 4,color:#132033

    client["Demo, tests, benchmarks"] --> handlers["<b>Auth handlers</b><br/>register, login, MFA,<br/>refresh, logout"]:::planned
    handlers -->|"UserRegistered, MfaChallengeIssued,<br/>LoginSucceeded, SessionLoggedOut, ..."| bus(("Event bus")):::planned
    bus --> roles["Role assignment"]:::planned
    bus --> mfa["MFA code delivery"]:::planned
    bus --> audit["Audit log"]:::planned
```

Whether events are delivered synchronously or asynchronously still has to be decided; it directly affects the latency measurements. If the MFA code travels inside an event, the audit consumer must not record it.
