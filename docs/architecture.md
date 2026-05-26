# Architecture

This document describes the system design of the Multi-Tenant Resource Isolation Engine — the key components, how they interact, and the reasoning behind the major structural decisions.

---

## Table of Contents

1. [System Overview](#system-overview)
2. [Request Lifecycle](#request-lifecycle)
3. [Tenant Provisioning Flow](#tenant-provisioning-flow)
4. [JWT Authentication Flow](#jwt-authentication-flow)
5. [Datasource Routing](#datasource-routing)
6. [Rate Limiting](#rate-limiting)
7. [Database Layout](#database-layout)
8. [Security Layers](#security-layers)
9. [Observability](#observability)

---

## System Overview

```mermaid
graph TB
    Client["Client / Service"]

    subgraph App ["Spring Boot Application (port 8080)"]
        TIF["TenantIdentificationFilter<br/>Extract tenant from header or JWT"]
        RLI["RateLimitingInterceptor<br/>Token bucket check"]
        JWF["JwtAuthFilter<br/>Admin endpoint guard"]
        CTL["Controllers<br/>AdminController / ResourceController / QuotaController"]
        SVC["Services<br/>TenantService / ResourceService / TenantProvisioningService"]
        DSR["TenantAwareDataSourceRouter<br/>AbstractRoutingDataSource"]
    end

    subgraph Storage ["Storage Layer"]
        MASTER[("MySQL Master<br/>saas_master<br/>tenant registry")]
        TA[("MySQL Tenant A<br/>tenant_a<br/>isolated data")]
        TB[("MySQL Tenant B<br/>tenant_b<br/>isolated data")]
        REDIS[("Redis<br/>Bucket4j token buckets<br/>rate_limit:{tenantId}")]
    end

    subgraph Observability ["Observability"]
        PROM["Prometheus<br/>scrapes /actuator/prometheus"]
        GRAF["Grafana<br/>dashboards"]
    end

    Client -->|"X-Tenant-ID or Bearer token"| TIF
    TIF --> RLI
    RLI --> JWF
    JWF --> CTL
    CTL --> SVC
    SVC --> DSR
    DSR -->|"TenantContext = null (master ops)"| MASTER
    DSR -->|"TenantContext = tenant-a"| TA
    DSR -->|"TenantContext = tenant-b"| TB
    RLI <-->|"atomic token consume"| REDIS
    PROM -->|"scrape every 15s"| App
    GRAF -->|"query"| PROM
```

---

## Request Lifecycle

Every HTTP request passes through this pipeline before reaching a controller:

```mermaid
sequenceDiagram
    participant C as Client
    participant RLF as RequestLoggingFilter
    participant TIF as TenantIdentificationFilter
    participant RLI as RateLimitingInterceptor
    participant CTL as Controller
    participant SVC as Service
    participant DSR as DataSourceRouter
    participant DB as Tenant DB

    C->>RLF: HTTP Request
    RLF->>RLF: Generate requestId, record startTime
    RLF->>TIF: forward

    TIF->>TIF: Extract X-Tenant-ID header or JWT claim
    alt Missing tenant identifier
        TIF-->>C: 401 Unauthorized
    end

    TIF->>TIF: isActiveTenant() via master JDBC (cached 2min)
    alt Tenant suspended or not found
        TIF-->>C: 403 Forbidden
    end

    TIF->>TIF: TenantContext.setCurrentTenant(tenantId)
    TIF->>RLI: forward

    RLI->>RLI: getBucketForTenant(tenantId) from Redis
    RLI->>RLI: bucket.tryConsumeAndReturnRemaining(1)
    alt Bucket empty
        RLI-->>C: 429 Too Many Requests + Retry-After header
    end

    RLI->>CTL: forward (token consumed)
    CTL->>SVC: business logic
    SVC->>DSR: JPA/JDBC call
    DSR->>DSR: TenantContext.getCurrentTenant()
    DSR->>DB: route to correct HikariCP pool
    DB-->>SVC: result
    SVC-->>CTL: response
    CTL-->>C: 200 OK

    Note over RLF: finally block logs:<br/>METHOD URI STATUS durationMs
    Note over TIF: finally block clears TenantContext
```

---

## Tenant Provisioning Flow

When a new tenant is registered via `POST /admin/tenants`:

```mermaid
sequenceDiagram
    participant A as Admin Client
    participant AC as AdminController
    participant TPS as TenantProvisioningService
    participant ENC as AesEncryptionService
    participant MASTER as Master DB
    participant FLYWAY as Flyway
    participant TENANTDB as Tenant DB
    participant DSR as DataSourceRouter

    A->>AC: POST /admin/tenants {tenantId, tier, databaseUrl}
    AC->>TPS: provisionTenant(req)

    TPS->>MASTER: findByTenantId() — check duplicate
    alt Tenant already exists
        TPS-->>A: 409 Conflict
    end

    TPS->>ENC: encrypt(defaultTenantPassword)
    ENC-->>TPS: AES-256-GCM ciphertext

    TPS->>MASTER: INSERT INTO tenants (encrypted password)
    MASTER-->>TPS: saved

    TPS->>FLYWAY: configure(tenantDbUrl, plainPassword)
    FLYWAY->>TENANTDB: CREATE TABLE users, resources, audit_logs, settings
    TENANTDB-->>FLYWAY: migrations applied
    FLYWAY-->>TPS: done

    TPS->>DSR: addTargetDataSource(tenantId, HikariDataSource)
    DSR->>DSR: afterPropertiesSet() — register new pool
    DSR-->>TPS: pool registered

    TPS-->>A: 201 Created {tenantId, tier, status: ACTIVE, rateLimitPerMinute}

    Note over TPS: Tenant is immediately routable<br/>No restart required
```

---

## JWT Authentication Flow

The system uses two separate JWT keys — one for tenants, one for admins:

```mermaid
sequenceDiagram
    participant C as Client
    participant AC as AuthController
    participant JWT as JwtTokenProvider
    participant TS as TenantService
    participant TIF as TenantIdentificationFilter
    participant JAF as JwtAuthFilter

    Note over C,JAF: Tenant JWT Flow

    C->>AC: POST /auth/token {tenantId}
    AC->>TS: isActiveTenant(tenantId)
    TS-->>AC: true
    AC->>JWT: generateTenantToken(tenantId)
    JWT->>JWT: sign with TENANT_SECRET key<br/>embed tenantId claim
    JWT-->>AC: signed JWT
    AC-->>C: {token, tenantId, type: Bearer}

    C->>TIF: GET /api/resources<br/>Authorization: Bearer <token>
    TIF->>JWT: extractTenantId(token)
    JWT->>JWT: verify with TENANT_SECRET key
    JWT-->>TIF: tenantId
    TIF->>TIF: setCurrentTenant(tenantId)

    Note over C,JAF: Admin JWT Flow

    C->>AC: POST /auth/admin/token {username, password}
    AC->>AC: verify credentials
    AC->>JWT: generateAdminToken(username)
    JWT->>JWT: sign with ADMIN_SECRET key<br/>embed role: ADMIN claim
    JWT-->>C: {token, username, type: Bearer}

    C->>JAF: GET /admin/tenants<br/>Authorization: Bearer <admin-token>
    JAF->>JWT: validateAdminToken(token)
    JWT->>JWT: verify with ADMIN_SECRET key<br/>check role = ADMIN
    JWT-->>JAF: valid
    JAF->>JAF: set ROLE_ADMIN in SecurityContext
    Note over JAF: Spring Security allows /admin/** for ROLE_ADMIN
```

---

## Datasource Routing

The core of the multi-tenancy engine — how a single JPA layer routes to the correct database:

```mermaid
flowchart TD
    REQ["Incoming Request"] --> TIF

    TIF["TenantIdentificationFilter<br/>TenantContext.setCurrentTenant('tenant-a')"]
    TIF --> JPA

    JPA["JPA / Hibernate<br/>repository.findAll()"]
    JPA --> DSR

    DSR["TenantAwareDataSourceRouter<br/>determineCurrentLookupKey()"]
    DSR --> CHECK

    CHECK{"TenantContext<br/>getCurrentTenant()"}
    CHECK -->|"null (admin/system ops)"| MASTER
    CHECK -->|"'tenant-a'"| POOL_A
    CHECK -->|"'tenant-b'"| POOL_B

    MASTER["HikariCP MasterPool<br/>→ saas_master DB<br/>tenants table"]
    POOL_A["HikariCP TenantPool-tenant-a<br/>→ tenant_a DB<br/>resources table"]
    POOL_B["HikariCP TenantPool-tenant-b<br/>→ tenant_b DB<br/>resources table"]

    style MASTER fill:#f5a623,color:#000
    style POOL_A fill:#7ed321,color:#000
    style POOL_B fill:#4a90e2,color:#000
```

**Key design decision:** `TenantService` deliberately bypasses the router by using a direct `JdbcTemplate` bound to the master datasource. This prevents the chicken-and-egg problem where validating a tenant requires a DB lookup, which requires knowing the tenant, which requires validation.

---

## Rate Limiting

Per-tenant token bucket stored in Redis, enforced before any controller logic runs:

```mermaid
flowchart TD
    REQ["Request arrives<br/>TenantContext = 'tenant-a'"] --> RLI

    RLI["RateLimitingInterceptor.preHandle()"]
    RLI --> GET_BUCKET

    GET_BUCKET["TenantBucketManager.getBucketForTenant('tenant-a')<br/>key: rate_limit:tenant-a"]
    GET_BUCKET --> EXISTS

    EXISTS{"Bucket exists<br/>in Redis?"}
    EXISTS -->|"No (first request)"| CREATE
    EXISTS -->|"Yes"| CONSUME

    CREATE["Build BucketConfiguration<br/>from tenant tier<br/>STARTER: 200 req/min, burst 300"]
    CREATE --> CONSUME

    CONSUME["bucket.tryConsumeAndReturnRemaining(1)<br/>atomic Lua script in Redis"]
    CONSUME --> RESULT

    RESULT{"Tokens<br/>remaining?"}
    RESULT -->|"> 0"| ALLOW
    RESULT -->|"= 0"| REJECT

    ALLOW["Set X-RateLimit-Remaining header<br/>forward to controller"]
    REJECT["429 Too Many Requests<br/>Retry-After: N seconds<br/>log rate limit event async"]

    style ALLOW fill:#7ed321,color:#000
    style REJECT fill:#d0021b,color:#000
```

**Tier configuration:**

| Tier | Requests/min | Burst |
|------|-------------|-------|
| FREE | 60 | 60 |
| STARTER | 200 | 300 |
| PROFESSIONAL | 600 | 900 |
| ENTERPRISE | 2000 | 3000 |

---

## Database Layout

```mermaid
erDiagram
    MASTER_DB {
        string id PK
        string tenant_id UK
        string company_name
        enum status
        enum tier
        string database_url
        string database_username
        string database_password "AES-256-GCM encrypted"
        int requests_per_minute
        int bucket_capacity
        timestamp created_at
        timestamp updated_at
    }

    TENANT_DB_RESOURCES {
        char id PK
        string name
        text description
        char owner_id
        json metadata
        timestamp created_at
        timestamp updated_at
    }

    TENANT_DB_USERS {
        char id PK
        string email UK
        string display_name
        enum role
        boolean is_active
        timestamp created_at
        timestamp updated_at
    }

    TENANT_DB_AUDIT_LOGS {
        bigint id PK
        char user_id
        string action
        string entity_type
        string entity_id
        json details
        timestamp occurred_at
    }

    MASTER_DB ||--o{ TENANT_DB_RESOURCES : "routes to"
    MASTER_DB ||--o{ TENANT_DB_USERS : "routes to"
    MASTER_DB ||--o{ TENANT_DB_AUDIT_LOGS : "routes to"
```

Every tenant gets their own copy of the tenant schema — `resources`, `users`, `audit_logs`, `settings` — in a physically separate MySQL database. The master DB only holds the tenant registry.

---

## Security Layers

Requests pass through multiple independent security checks:

```
Layer 1 — Network
  └── Docker network isolation — containers communicate internally only

Layer 2 — Spring Security (JwtAuthFilter)
  └── /admin/** requires ROLE_ADMIN (validated admin JWT)
  └── /auth/** is public
  └── /swagger-ui/** is public

Layer 3 — TenantIdentificationFilter
  └── /api/** requires X-Tenant-ID header or valid tenant JWT
  └── Tenant must exist in master DB and be ACTIVE

Layer 4 — RateLimitingInterceptor
  └── /api/** token bucket check per tenant
  └── 429 if bucket exhausted

Layer 5 — Physical DB Isolation
  └── Each tenant's HikariCP pool only has connections to their database
  └── Cross-tenant data access is structurally impossible
  └── A missing WHERE clause cannot leak data across tenants

Layer 6 — Encryption at Rest
  └── Tenant DB passwords stored as AES-256-GCM ciphertext in master DB
  └── Decrypted only at datasource initialization time
```

---

## Observability

```mermaid
graph LR
    APP["Spring Boot App<br/>/actuator/prometheus"]
    PROM["Prometheus<br/>:9090"]
    GRAF["Grafana<br/>:3000"]
    LOGS["Structured JSON Logs<br/>via logback-spring.xml"]

    APP -->|"scrape every 15s"| PROM
    PROM -->|"PromQL queries"| GRAF
    APP -->|"stdout JSON"| LOGS

    subgraph Metrics Captured
        M1["http_server_requests_seconds — request rate, latency, status codes"]
        M2["hikaricp_connections — pool size and active connections per tenant pool"]
        M3["jvm_memory_used_bytes — heap and non-heap breakdown"]
        M4["jvm_threads_live_threads — thread count"]
        M5["process_uptime_seconds — app uptime"]
    end
```

Every log line includes:
- `timestamp` — ISO-8601
- `level` — INFO/WARN/ERROR
- `message` — human-readable summary
- `tenantId` — which tenant made the request (via MDC)
- `requestId` — unique per request for distributed tracing
- `durationMs` — total request time
- `app` and `env` — for log aggregator filtering