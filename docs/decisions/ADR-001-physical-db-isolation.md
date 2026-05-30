# ADR-001: Physical Database Isolation Over Schema-Per-Tenant

**Status:** Accepted  
**Date:** 2026-05-26

---

## Context

A multi-tenant SaaS system must ensure that one tenant cannot access another tenant's data. There are three common approaches to multi-tenancy in relational databases, each with different isolation guarantees and operational tradeoffs.

The system needed to handle tenants with strict data isolation requirements — where a data leak between tenants would be a critical failure, not just a bug.

---

## Options Considered

### Option 1 — Shared Table (Row-Level Isolation)
All tenants share the same tables. Every row has a `tenant_id` column. Queries use `WHERE tenant_id = ?`.

**Pros:**
- Simplest to implement
- Lowest infrastructure cost
- Easy to add new tenants

**Cons:**
- A single missing `WHERE tenant_id = ?` clause leaks all tenants' data
- One tenant's heavy queries affect all others (noisy neighbor)
- Harder to give tenants their own backup/restore
- Compliance requirements (GDPR, SOC2) are harder to satisfy

### Option 2 — Schema-Per-Tenant (Logical Isolation)
All tenants share the same MySQL instance but each has their own schema. Hibernate's `SCHEMA` multi-tenancy mode switches schemas per request.

**Pros:**
- Better isolation than row-level
- Easier to manage than separate instances
- Single MySQL instance to operate

**Cons:**
- Still on the same MySQL process — one tenant's runaway query can degrade others
- Schema switching adds latency per request
- Connection pooling is more complex

### Option 3 — Database-Per-Tenant (Physical Isolation) ✅ CHOSEN
Each tenant gets their own MySQL instance with a dedicated HikariCP connection pool.

**Pros:**
- Complete physical isolation — a bug cannot leak data across tenants
- Tenants can have different DB configurations and backup schedules
- One tenant's heavy load does not affect others
- Compliance is straightforward — tenant data is literally in a separate database
- Connection pools are sized per tier (FREE=2, ENTERPRISE=20)

**Cons:**
- Higher infrastructure cost — one MySQL instance per tenant
- More complex startup — must load all tenant datasources
- Harder to scale to thousands of tenants without lazy loading

---

## Decision

**Physical database isolation (database-per-tenant)** was chosen.

The implementation uses Spring's `AbstractRoutingDataSource` to maintain a map of `tenantId → HikariDataSource`. Each `HikariDataSource` only has connections to that tenant's database. The routing key is read from a `ThreadLocal` (`TenantContext`) that is set at the start of each request and cleared in a `finally` block.

```
Request → TenantIdentificationFilter → TenantContext.set("tenant-a")
       → TenantAwareDataSourceRouter.determineCurrentLookupKey() → "tenant-a"
       → HikariCP TenantPool-tenant-a → tenant_a MySQL database
```

---

## Consequences

**Gained:**
- Cross-tenant data access is structurally impossible at the connection pool level
- Per-tenant pool sizing enables resource isolation by tier
- Each tenant's schema can be migrated independently via Flyway

**Given up:**
- Cannot easily scale to 10,000+ tenants without lazy datasource loading (future work)
- Each new tenant requires a MySQL database to already exist
- Higher memory footprint — each HikariCP pool holds open connections

**Future mitigation:**
- Lazy datasource initialization — only load pools for tenants active in the last N minutes
- Shared MySQL instance with separate schemas as a middle ground for lower tiers