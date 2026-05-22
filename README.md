# Multi-Tenant Resource Isolation Engine

> **Production-grade SaaS backend** demonstrating per-tenant rate limiting, dynamic datasource routing, and physical database isolation — where tenant data separation is enforced at the connection pool level, not just the query level.

![Java](https://img.shields.io/badge/Java-17-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.2-brightgreen)
![MySQL](https://img.shields.io/badge/MySQL-8.0-blue)
![Redis](https://img.shields.io/badge/Redis-7-red)

---

## What This Demonstrates

Most multi-tenant systems isolate data with a `WHERE tenant_id = ?` clause. This engine takes a stronger approach: **each tenant gets a physically separate MySQL database and HikariCP connection pool**. A bug in the application layer cannot leak cross-tenant data because the wrong database simply does not contain the other tenant's rows.

---

## Architecture

```
HTTP Request
     │
     ▼
┌──────────────────────────────┐
│   TenantIdentificationFilter  │  ← Extracts X-Tenant-ID header or JWT claim
│                               │    Returns 401 if missing, 403 if suspended
└───────────────┬──────────────┘
                │ Sets TenantContext (ThreadLocal)
                ▼
┌──────────────────────────────┐
│    RateLimitingInterceptor    │  ← Checks Redis token bucket (Bucket4j)
│                               │    Returns HTTP 429 + Retry-After if exhausted
└───────────────┬──────────────┘
                │ Consumes 1 token
                ▼
┌──────────────────────────────┐
│      Spring @RestController   │  ← Business logic (tenant-scoped)
└───────────────┬──────────────┘
                │ JPA/Hibernate calls
                ▼
┌──────────────────────────────┐
│  TenantAwareDataSourceRouter  │  ← Reads TenantContext → picks DataSource
│  (AbstractRoutingDataSource)  │    Falls back to master when context is empty
└───────┬───────────┬──────────┘
        │           │
   ┌────▼───┐  ┌────▼───┐  ┌──────────┐
   │Tenant A│  │Tenant B│  │  Master  │
   │ MySQL  │  │ MySQL  │  │  MySQL   │
   └────────┘  └────────┘  └──────────┘
                                 ↑
                         Tenant registry,
                         admin operations

Redis ← Bucket4j token buckets per tenant (key: rate_limit:{tenantId})
```

---

## Tech Stack

| Component | Technology | Purpose |
|-----------|-----------|---------|
| Framework | Spring Boot 3.2 | Core application |
| Multi-tenancy | `AbstractRoutingDataSource` | Per-request DB routing |
| Rate limiting | Bucket4j + Redis | Distributed token buckets |
| Database | MySQL 8 | Physical isolation per tenant |
| Migrations | Flyway | Auto schema on tenant provisioning |
| Connection Pool | HikariCP | Tier-based pool sizing |
| Auth | JJWT | Separate tenant + admin JWT keys |
| Containerization | Docker Compose | Full local environment |

---

## Quick Start

### Prerequisites
- Docker & Docker Compose v2+
- Java 17+
- Maven 3.8+

### 1. Clone & Configure

```bash
git clone https://github.com/adarsh-yadav1/multitenant-isolation-engine.git
cd multitenant-isolation-engine
cp .env.example .env
# Edit .env — see required variables below
```

**Required `.env` variables:**
```
MASTER_DB_NAME=saas_master
MASTER_DB_USER=master_user
MASTER_DB_PASSWORD=local_master_pass
MASTER_DB_ROOT_PASSWORD=local_root_pass
TENANT_DB_USER=tenant_user
TENANT_DB_PASSWORD=local_tenant_pass
TENANT_DB_ROOT_PASSWORD=local_root_pass
REDIS_HOST=redis
REDIS_PORT=6379
JWT_SECRET=dev-jwt-secret-minimum-32-characters-long
ADMIN_JWT_SECRET=dev-admin-secret-minimum-32-characters-long
DB_ENCRYPTION_KEY=dev-encryption-key-32-characters
```

### 2. Start

```bash
docker compose up -d
```

Starts:
- `mysql-master` — tenant registry (port 3306)
- `mysql-tenant-a` — Tenant A's isolated DB (port 3307)
- `mysql-tenant-b` — Tenant B's isolated DB (port 3308)
- `redis` — Bucket4j state (port 6379)
- `app` — Spring Boot API (port 8080)
- `adminer` — DB UI (port 8090)

### 3. Get an admin token

```bash
curl -X POST http://localhost:8080/auth/admin/token \
  -H "Content-Type: application/json" \
  -d '{"username": "admin", "password": "admin-secret"}'
```

```json
{
  "token": "eyJhbGci...",
  "username": "admin",
  "type": "Bearer"
}
```

### 4. Register a tenant

```bash
curl -X POST http://localhost:8080/admin/tenants \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <admin-token>" \
  -d '{
    "tenantId": "tenant-a",
    "name": "Tenant A Corp",
    "tier": "STARTER",
    "contactEmail": "admin@tenant-a.com",
    "databaseUrl": "jdbc:mysql://mysql-tenant-a:3306/tenant_a?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC"
  }'
```

Flyway automatically runs `V1__create_tenant_business_schema.sql` on the tenant's database at registration time — no manual setup needed.

### 5. Make tenant API calls

```bash
# Using X-Tenant-ID header
curl http://localhost:8080/api/resources \
  -H "X-Tenant-ID: tenant-a"

# Or using JWT Bearer token
curl -X POST http://localhost:8080/auth/token \
  -H "Content-Type: application/json" \
  -d '{"tenantId": "tenant-a"}'

curl http://localhost:8080/api/resources \
  -H "Authorization: Bearer <tenant-token>"
```

---

## Tenant Isolation Proof

This is the core guarantee of the engine. The following commands were run against a live instance with two registered tenants.

### Setup — create one resource per tenant

```bash
curl -X POST http://localhost:8080/api/resources \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: tenant-a" \
  -d '{"name": "Tenant A Secret", "data": "confidential data for A"}'
# → {"id":"2c3836b3-424a-418d-b519-de7b89eb0043", ...}

curl -X POST http://localhost:8080/api/resources \
  -H "Content-Type: application/json" \
  -H "X-Tenant-ID: tenant-b" \
  -d '{"name": "Tenant B Secret", "data": "confidential data for B"}'
# → {"id":"6a7ef419-80a0-4512-9b70-67f0da572a73", ...}
```

### Test 1 — Each tenant sees only their own data

```bash
curl http://localhost:8080/api/resources -H "X-Tenant-ID: tenant-a"
```
```json
[
  {"id":"2c3836b3-424a-418d-b519-de7b89eb0043","name":"Tenant A Secret","data":"confidential data for A"},
  {"id":"600e48e2-2078-4fda-a959-85758b41a93a","name":"My First Resource","data":"some data"}
]
```

```bash
curl http://localhost:8080/api/resources -H "X-Tenant-ID: tenant-b"
```
```json
[
  {"id":"6a7ef419-80a0-4512-9b70-67f0da572a73","name":"Tenant B Secret","data":"confidential data for B"},
  {"id":"ca5c6fd9-aafb-4add-ac79-83d9b3cbeebc","name":"Tenant B Resource","data":"tenant b data"}
]
```

✅ **Each tenant sees only their own resources.**

### Test 2 — Cross-tenant access by UUID returns 404

Even when tenant-b knows tenant-a's exact resource UUID, they cannot access it:

```bash
curl http://localhost:8080/api/resources/2c3836b3-424a-418d-b519-de7b89eb0043 \
  -H "X-Tenant-ID: tenant-b"
```
```json
{
  "type": "https://errors.saas.example/resource-not-found",
  "title": "Resource Not Found",
  "status": 404,
  "detail": "Resource not found: 2c3836b3-424a-418d-b519-de7b89eb0043"
}
```

```bash
curl http://localhost:8080/api/resources/6a7ef419-80a0-4512-9b70-67f0da572a73 \
  -H "X-Tenant-ID: tenant-a"
```
```json
{
  "type": "https://errors.saas.example/resource-not-found",
  "title": "Resource Not Found",
  "status": 404,
  "detail": "Resource not found: 6a7ef419-80a0-4512-9b70-67f0da572a73"
}
```

✅ **Cross-tenant access is impossible — the UUID does not exist in the other tenant's database.**

### Why this is stronger than row-level isolation

With a shared database and `WHERE tenant_id = ?`, a missing filter clause leaks all tenants' data. With physical isolation:

- tenant-a's connection pool **only has connections to `tenant_a` database**
- tenant-b's UUID `6a7ef419...` does not exist in `tenant_a` database at all
- There is no SQL filter to accidentally omit

---

## Rate Limiting

Each tenant tier has a token bucket in Redis. Limits are enforced before any controller logic runs.

| Tier | Requests/min | Burst |
|------|-------------|-------|
| FREE | 60 | 60 |
| STARTER | 200 | 300 |
| PROFESSIONAL | 600 | 900 |
| ENTERPRISE | 2000 | 3000 |

**Response headers on every request:**
```
X-RateLimit-Limit: 200
X-RateLimit-Remaining: 199
```

**When exhausted:**
```
HTTP/1.1 429 Too Many Requests
Retry-After: 12
```

---

## API Reference

### Auth
| Method | Path | Description |
|--------|------|-------------|
| POST | `/auth/token` | Issue tenant JWT |
| POST | `/auth/admin/token` | Issue admin JWT |

### Admin (requires admin JWT)
| Method | Path | Description |
|--------|------|-------------|
| POST | `/admin/tenants` | Register + provision tenant |
| GET | `/admin/tenants` | List all tenants |
| PATCH | `/admin/tenants/{id}/status` | Suspend / reactivate |
| PATCH | `/admin/tenants/{id}/rate-limit` | Override rate limit |

### Resources (requires X-Tenant-ID or tenant JWT)
| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/resources` | List tenant's resources |
| POST | `/api/resources` | Create resource |
| GET | `/api/resources/{id}` | Get by ID |
| PUT | `/api/resources/{id}` | Update |
| DELETE | `/api/resources/{id}` | Delete |

Full interactive docs at `http://localhost:8080/swagger-ui/index.html`

---

## Error Responses (RFC 7807)

All errors follow the Problem Detail standard:

```json
{
  "type": "https://errors.saas.example/tenant-not-found",
  "title": "Tenant Not Found",
  "status": 404,
  "detail": "Tenant 'acme' not found",
  "instance": "/api/resources"
}
```

| Scenario | Status |
|----------|--------|
| Missing tenant identifier | 401 |
| Tenant suspended | 403 |
| Admin endpoint without token | 403 |
| Resource not found | 404 |
| Tenant already exists | 409 |
| Validation failure | 400 |
| Rate limit exceeded | 429 |

---

## Project Structure

```
src/main/java/com/saas/multitenant/
├── config/               # DataSource routing, Security, WebMvc, Redis
├── multitenancy/         # TenantContext, TenantAwareDataSourceRouter
├── ratelimit/            # TenantBucketManager, RateLimitingInterceptor
├── filter/               # TenantIdentificationFilter
├── domain/
│   ├── tenant/           # Tenant entity, TenantService, TenantProvisioningService
│   └── resource/         # Resource entity, ResourceService
├── controller/           # AdminController, ResourceController, AuthController
├── dto/                  # Request/response DTOs
├── exception/            # GlobalExceptionHandler, custom exceptions
└── security/             # JwtTokenProvider, JwtAuthFilter

src/main/resources/
├── application.yml
├── application-docker.yml
└── db/migration/
    ├── master/           # V1__create_tenant_registry.sql
    └── tenant/           # V1__create_tenant_business_schema.sql
```

---

## Security Design

- Admin and tenant JWTs signed with **separate secret keys**
- `TenantContext` ThreadLocal always cleared in `finally` block
- Flyway migrations run on a **direct datasource** (not the router) during provisioning
- `TenantService` queries master DB via **plain JDBC** to avoid routing to tenant DB
- `.env` is in `.gitignore` — secrets never committed

---

## License

MIT