# ADR-002: Bucket4j Over Spring Rate Limiter for Rate Limiting

**Status:** Accepted  
**Date:** 2026-05-26

---

## Context

The system needs per-tenant rate limiting — each tenant has a token bucket with a limit determined by their tier (FREE=60/min, ENTERPRISE=2000/min). The rate limiter must:

- Be enforced before any controller logic runs
- Work correctly across multiple application instances (distributed)
- Support per-tenant configuration that can change at runtime
- Return meaningful headers (`X-RateLimit-Remaining`, `Retry-After`)

---

## Options Considered

### Option 1 — Spring Cloud Gateway Rate Limiter
Built into Spring Cloud Gateway using Redis. Configured via application properties.

**Pros:**
- Zero code — just configuration
- Built-in Redis integration

**Cons:**
- Requires Spring Cloud Gateway as an API gateway layer
- Adds significant infrastructure complexity
- Less control over per-tenant configuration
- Not suitable for embedding directly in a Spring Boot app

### Option 2 — Resilience4j Rate Limiter
Part of the Resilience4j library, designed for circuit breaking and rate limiting.

**Pros:**
- Good Spring Boot integration
- Part of a broader resilience library

**Cons:**
- In-memory only by default — does not work across multiple instances
- Distributed rate limiting requires custom implementation
- Configuration is per-named-instance, not dynamically per-tenant

### Option 3 — Custom Redis INCR + TTL
Manual implementation using Redis `INCR` with a TTL key per tenant per time window.

**Pros:**
- Full control
- Simple to understand

**Cons:**
- Race conditions at window boundaries (thundering herd problem)
- Doesn't support burst capacity
- Significant custom code to maintain
- No token refill algorithm

### Option 4 — Bucket4j with Redis (Lettuce) ✅ CHOSEN
Bucket4j is a Java rate limiting library implementing the token bucket algorithm. When backed by Redis via Lettuce, it provides distributed atomic operations via Lua scripts.

**Pros:**
- Token bucket algorithm supports burst capacity (STARTER: 200/min, burst 300)
- Atomic check-and-consume via Redis Lua scripts — no race conditions
- Works across multiple application instances sharing the same Redis
- Per-tenant configuration loaded dynamically from DB
- Zero-downtime rate limit changes — delete Redis key, next request rebuilds with new config
- Returns remaining tokens for `X-RateLimit-Remaining` header

**Cons:**
- Additional dependency
- Requires Redis (already needed for other features)
- Slightly more complex setup than simple INCR

---

## Decision

**Bucket4j with Redis (Lettuce)** was chosen.

Each tenant's bucket is stored in Redis at key `rate_limit:{tenantId}`. The `ProxyManager` handles serialization and atomic operations. Bucket configuration is built from the tenant's tier on first access and cached in Redis.

```java
Bucket bucket = bucketProxyManager.builder()
    .build("rate_limit:tenant-a", () -> buildConfiguration("tenant-a"));
ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);
```

---

## Consequences

**Gained:**
- Distributed rate limiting that works correctly with multiple app instances
- Burst capacity support — tenants can absorb short spikes above their average limit
- Zero-downtime limit changes — reset Redis key after updating DB
- Accurate `X-RateLimit-Remaining` headers on every response

**Given up:**
- Redis becomes a required dependency (acceptable — already used for other features)
- Slightly higher per-request latency (~1ms) for the Redis round-trip