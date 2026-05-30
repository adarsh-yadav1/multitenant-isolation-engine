# ADR-003: Lettuce Over Jedis as Redis Client

**Status:** Accepted  
**Date:** 2026-05-26

---

## Context

The system uses Redis for two purposes:
1. Bucket4j token buckets for rate limiting
2. Spring Cache (`@Cacheable`) for tenant lookups

A Redis client library is needed. The two main options in the Java ecosystem are Lettuce and Jedis.

---

## Options Considered

### Option 1 — Jedis
The older, more established Redis client for Java.

**Pros:**
- Simple, synchronous API
- Well-documented
- Large community

**Cons:**
- Synchronous, blocking I/O — one thread per connection
- Connection pool required for concurrent access
- Not reactive — doesn't integrate well with Spring WebFlux
- No native async support
- Connection pool management adds complexity

### Option 2 — Lettuce ✅ CHOSEN
The modern Redis client for Java, built on Netty.

**Pros:**
- Asynchronous, non-blocking I/O via Netty
- Single connection shared across multiple threads (no pool needed for most use cases)
- Native async and reactive API
- Spring Boot's default Redis client since Spring Boot 2.x
- Better performance under high concurrency
- Built-in support for Redis Cluster and Sentinel

**Cons:**
- Slightly more complex API for simple use cases
- Netty dependency adds to classpath size

---

## Decision

**Lettuce** was chosen.

Spring Boot auto-configures Lettuce when `spring-boot-starter-data-redis` is on the classpath. The Bucket4j Redis integration (`bucket4j-redis`) also uses Lettuce via `LettuceBasedProxyManager`.

```java
return LettuceBasedProxyManager.builderFor(redisConnection).build();
```

This means both rate limiting (Bucket4j) and caching (Spring Cache) share the same Lettuce connection infrastructure.

---

## Consequences

**Gained:**
- Non-blocking Redis operations — rate limit checks don't block request threads
- Single shared connection handles both Bucket4j and Spring Cache
- Spring Boot auto-configuration works out of the box
- Future-proof for reactive stack if needed

**Given up:**
- Slightly steeper learning curve than Jedis for custom Redis operations
- Netty on the classpath (negligible — Spring Boot already uses Netty)