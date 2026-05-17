# Multi-Tenant Resource Isolation Engine

> **Production-grade SaaS backend** demonstrating per-tenant rate limiting, dynamic datasource routing, and physical database isolation.

[![CI](https://github.com/your-org/multi-tenant-engine/actions/workflows/ci.yml/badge.svg)](https://github.com/your-org/multi-tenant-engine/actions/workflows/ci.yml)
[![Docker](https://github.com/your-org/multi-tenant-engine/actions/workflows/docker-publish.yml/badge.svg)](https://github.com/your-org/multi-tenant-engine/actions/workflows/docker-publish.yml)
![Java](https://img.shields.io/badge/Java-17-orange)
![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-brightgreen)

---

## Architecture

```
HTTP Request
     │
     ▼
┌─────────────────────────┐
│  TenantIdentificationFilter │  ← Extracts X-Tenant-ID or JWT claim
└────────────┬────────────┘
             │  Sets TenantContext (ThreadLocal)
             ▼
┌─────────────────────────┐
│  RateLimitingInterceptor  │  ← Checks Redis token bucket (Bucket4j)
│                           │    Returns HTTP 429 if bucket empty
└────────────┬────────────┘
             │  Consumes 1 token
             ▼
┌─────────────────────────┐
│   Spring @RestController  │  ← Business logic (tenant-scoped)
└────────────┬────────────┘
             │  JPA/Hibernate calls
             ▼
┌─────────────────────────┐
│ TenantAwareDataSourceRouter│ ← Reads TenantContext → picks DataSource
└──────┬──────────┬───────┘
       │          │
  ┌────▼───┐  ┌───▼────┐
  │Tenant A│  │Tenant B│  ← Physically isolated MySQL schemas/DBs
  │  MySQL │  │  MySQL │
  └────────┘  └────────┘

Redis ← stores token buckets per tenant (rate_limit:{tenant_id})
```

---

## Tech Stack

| Component | Technology |
|-----------|-----------|
| Framework | Spring Boot 3.x |
| Multi-tenancy | Hibernate `AbstractRoutingDataSource` |
| Rate limiting | Bucket4j + Redis (Lettuce) |
| Database | MySQL 8 (per-tenant) + Master DB |
| Migrations | Flyway |
| Containerization | Docker + Docker Compose |
| Connection Pool | HikariCP (tier-based sizing) |
| Auth | JWT (tenant) + Admin JWT |

---

## Quick Start

### Prerequisites
- Docker & Docker Compose v2+
- Java 17+ (for local development)
- Maven 3.8+

### 1. Clone & Configure

```bash
git clone https://github.com/your-org/multi-tenant-engine.git
cd multi-tenant-engine

# Copy and fill in secrets
cp .env.example .env
# Edit .env with your values (see .env.example for required vars)
```

### 2. Start Infrastructure

```bash
docker compose up -d
```

This starts:
- `mysql-master` — tenant registry on port 3306
- `mysql-tenant-a` — Tenant A's isolated DB on port 3307
- `mysql-tenant-b` — Tenant B's isolated DB on port 3308
- `redis` — Bucket4j state store on port 6379
- `app` — Spring Boot API on port 8080
- `adminer` *(optional)* — DB admin UI on port 8090

### 3. Verify

```bash
# Health check
curl http://localhost:8080/actuator/health

# Register a tenant (admin)
curl -X POST http://localhost:8080/admin/tenants \
  -H "Authorization: Bearer $ADMIN_JWT" \
  -H "Content-Type: application/json" \
  -d '{
    "tenantId": "acme_corp",
    "companyName": "Acme Corporation",
    "tier": "PROFESSIONAL",
    "databaseUrl": "jdbc:mysql://mysql-tenant-a:3306/tenant_acme",
    "databaseUsername": "tenant_user",
    "databasePassword": "secret"
  }'

# Make a rate-limited API call as tenant
curl http://localhost:8080/api/resources \
  -H "X-Tenant-ID: acme_corp" \
  -H "Authorization: Bearer $TENANT_JWT"
```

---

## Rate Limit Tiers

| Tier | Requests/min | Burst Capacity |
|------|-------------|----------------|
| FREE | 60 | 60 |
| STARTER | 200 | 300 |
| PROFESSIONAL | 600 | 900 |
| ENTERPRISE | 2000 | 3000 |
| CUSTOM | Per-tenant override | Per-tenant override |

### Update a Tenant's Rate Limit (zero-downtime)

```bash
curl -X PATCH http://localhost:8080/admin/tenants/acme_corp/rate-limit \
  -H "Authorization: Bearer $ADMIN_JWT" \
  -H "Content-Type: application/json" \
  -d '{"requestsPerMinute": 1000, "bucketCapacity": 1500}'
```

---

## API Reference

### Tenant API (rate-limited)

| Method | Path | Description |
|--------|------|-------------|
| GET | `/api/resources` | List tenant resources |
| POST | `/api/resources` | Create resource |
| GET | `/api/resources/{id}` | Get resource |
| PUT | `/api/resources/{id}` | Update resource |
| DELETE | `/api/resources/{id}` | Delete resource |
| GET | `/api/health` | Tenant quota status |

### Admin API (not rate-limited)

| Method | Path | Description |
|--------|------|-------------|
| POST | `/admin/tenants` | Register new tenant |
| GET | `/admin/tenants` | List all tenants |
| PATCH | `/admin/tenants/{id}/status` | Suspend / reactivate |
| PATCH | `/admin/tenants/{id}/rate-limit` | Update rate limits |
| GET | `/admin/tenants/{id}/usage` | Usage statistics |
| DELETE | `/admin/tenants/{id}` | Deprovision tenant |

### Rate Limit Response Headers

```
HTTP/1.1 429 Too Many Requests
Retry-After: 12
X-RateLimit-Limit: 600
X-RateLimit-Remaining: 0
```

---

## Project Structure

```
multi-tenant-engine/
├── src/main/java/com/saas/multitenant/
│   ├── config/               # Spring @Configuration beans
│   ├── multitenancy/         # DataSource routing, TenantContext, Hibernate resolvers
│   ├── ratelimit/            # TenantBucketManager, RateLimitingInterceptor
│   ├── filter/               # TenantIdentificationFilter
│   ├── domain/
│   │   ├── tenant/           # Tenant entity, repository, service
│   │   └── resource/         # Business domain entities
│   ├── controller/           # REST controllers
│   ├── dto/                  # Request/response DTOs
│   ├── exception/            # Custom exceptions + GlobalExceptionHandler
│   ├── security/             # JWT parsing, security config
│   └── util/                 # Encryption, ID generation helpers
├── src/main/resources/
│   ├── application.yml
│   ├── application-docker.yml
│   └── db/migration/
│       ├── master/           # Flyway: tenant registry schema
│       └── tenant/           # Flyway: tenant business schema
├── src/test/
│   └── java/com/saas/multitenant/
│       ├── unit/             # JUnit 5 + Mockito unit tests
│       └── integration/      # Testcontainers integration tests
├── docker/
│   └── init-scripts/         # MySQL init SQL for seed data
├── .github/workflows/
│   ├── ci.yml                # Build + test on PR
│   └── docker-publish.yml    # Build & push image on main
├── docker-compose.yml
├── docker-compose.override.yml  # Dev overrides (adminer, debug port)
├── Dockerfile
├── .env.example
└── pom.xml
```

---

## Development

### Run locally (without Docker)

```bash
# Start only infrastructure
docker compose up -d redis mysql-master mysql-tenant-a mysql-tenant-b

# Run app with local profile
mvn spring-boot:run -Dspring-boot.run.profiles=local
```

### Run tests

```bash
# Unit tests
mvn test

# Integration tests (requires Docker for Testcontainers)
mvn verify -P integration-tests
```

### Load test (k6)

```bash
k6 run scripts/load-test.js
```

---

## Security Notes

- Tenant DB passwords are stored **AES-256 encrypted** in the master database
- `.env` is in `.gitignore` — never commit secrets
- Admin and tenant JWTs are signed with **separate keys**
- ThreadLocal is always cleared in `finally` to prevent cross-request contamination
- Cross-tenant data access is **physically impossible** at the connection pool level

---

## Production Deployment

See [`docs/deployment.md`](docs/deployment.md) for:
- Kubernetes Helm chart setup
- Redis Sentinel / Cluster configuration
- HikariCP pool sizing by tier
- Lazy datasource loading for 1000+ tenants
- Backup strategy per tenant

---

## License

MIT — see [LICENSE](LICENSE)
