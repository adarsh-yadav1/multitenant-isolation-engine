# ADR-004: HikariCP Pool-Per-Tenant Over Shared Connection Pool

**Status:** Accepted  
**Date:** 2026-05-26

---

## Context

With physical database isolation, each tenant has their own MySQL database. The question is how to manage database connections — specifically whether each tenant should have their own HikariCP connection pool, or whether connections should be shared.

---

## Options Considered

### Option 1 — Shared Connection Pool
A single HikariCP pool that opens connections to different databases as needed.

**Pros:**
- Lower total connection count
- Simpler configuration

**Cons:**
- HikariCP is designed for a single database URL — sharing across databases requires custom work
- Cannot size pools per tenant tier
- A tenant that exhausts connections starves all other tenants
- No isolation at the connection level

### Option 2 — Pool-Per-Tenant ✅ CHOSEN
Each tenant gets their own `HikariDataSource` instance with pool size determined by tier.

**Pros:**
- Complete connection isolation — tenant-a's queries cannot use tenant-b's connections
- Pool size is sized by tier:
  - FREE: 2 connections
  - STARTER: 5 connections
  - PROFESSIONAL: 10 connections
  - ENTERPRISE: 20 connections
- A tenant exhausting their pool does not affect other tenants
- Pools are registered at runtime — new tenants get a pool without restart
- HikariCP metrics per pool (`hikaricp_connections_active{pool="TenantPool-tenant-a"}`)

**Cons:**
- Higher total connection count across all tenants
- Each pool has minimum idle connections open even for inactive tenants
- Memory footprint grows with number of tenants

---

## Decision

**Pool-per-tenant** was chosen, with pool size determined by tier.

Pools are registered in `TenantAwareDataSourceRouter` at startup (for existing tenants) and at runtime (for newly provisioned tenants):

```java
public void addTargetDataSource(String tenantId, DataSource dataSource) {
    targetDataSourceMap.put(tenantId, dataSource);
    super.setTargetDataSources(targetDataSourceMap);
    afterPropertiesSet(); // re-initialise router with new pool
}
```

Pool configuration per tier:

| Tier | Max Pool Size | Min Idle |
|------|-------------|---------|
| FREE | 2 | 1 |
| STARTER | 5 | 2 |
| PROFESSIONAL | 10 | 2 |
| ENTERPRISE | 20 | 2 |

---

## Consequences

**Gained:**
- Resource isolation by tier — ENTERPRISE tenants get 10x more connections than FREE
- HikariCP metrics labeled per pool in Prometheus (`pool="TenantPool-tenant-a"`)
- A misbehaving tenant cannot exhaust connections for other tenants
- Graceful shutdown closes each pool cleanly via JVM shutdown hooks

**Given up:**
- Total connections = sum of all tenant pool sizes — can be large with many tenants
- Idle connections held open for inactive tenants (mitigatable with `minimumIdle=1`)

**Future mitigation:**
- Evict pools for tenants inactive for N hours
- Reduce `minimumIdle` to 0 for FREE tier — connections created on demand