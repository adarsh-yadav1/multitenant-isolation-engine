package com.saas.multitenant.ratelimit;

import com.saas.multitenant.domain.tenant.Tenant;
import com.saas.multitenant.domain.tenant.TenantService;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Central authority for Bucket4j token bucket lifecycle.
 *
 * <p>Each tenant's bucket is stored in this application instance at key
 * {@code rate_limit:{tenantId}}. This keeps the local development setup runnable;
 * production deployments should replace the map with a distributed Bucket4j backend.
 *
 * <p>Key behaviours:
 * <ul>
 *   <li>Buckets are created lazily on first request</li>
 *   <li>Bucket configuration is loaded from the tenant record (supports per-tenant overrides)</li>
 *   <li>Calling {@link #resetBucketForTenant(String)} deletes the Redis key so the
 *       next request re-creates it with updated limits — enabling zero-downtime rate limit changes</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TenantBucketManager {

    private static final String BUCKET_KEY_PREFIX = "rate_limit:";

    private final Map<String, Bucket> buckets = new ConcurrentHashMap<>();
    private final TenantService tenantService;

    /**
     * Returns (or lazily creates) the Bucket4j bucket for the given tenant.
     * Backed by an in-memory map for the local development baseline.
     */
    public Bucket getBucketForTenant(String tenantId) {
        return buckets.computeIfAbsent(BUCKET_KEY_PREFIX + tenantId, key -> Bucket.builder()
                .addLimit(buildLimit(tenantId))
                .build());
    }

    /**
     * Deletes the bucket key so the next request re-creates it with the
     * current (updated) rate limit configuration. Call after modifying a tenant's
     * rate limit in the database.
     */
    @CacheEvict(value = "tenantRateLimitConfig", key = "#tenantId")
    public void resetBucketForTenant(String tenantId) {
        buckets.remove(BUCKET_KEY_PREFIX + tenantId);
        log.info("Rate limit bucket reset for tenant={}", tenantId);
    }

    /** Returns the configured requests-per-minute for response headers. */
    @Cacheable(value = "tenantRateLimitConfig", key = "#tenantId")
    public long getRateLimitForTenant(String tenantId) {
        Tenant tenant = tenantService.getActiveTenant(tenantId);
        return tenant.getEffectiveRequestsPerMinute();
    }

    /** Asynchronously persist a rate-limit-exceeded event for analytics. */
    public void logRateLimitEvent(String tenantId, String endpoint) {
        tenantService.recordRateLimitEvent(tenantId, endpoint);
    }

    // ─── Private helpers ────────────────────────────────────────────────────────

    private Bandwidth buildLimit(String tenantId) {
        Tenant tenant = tenantService.getActiveTenant(tenantId);

        long requestsPerMinute = tenant.getEffectiveRequestsPerMinute();
        long burstCapacity = tenant.getEffectiveBucketCapacity();

        log.debug("Building bucket for tenant={} rpm={} burst={}",
                tenantId, requestsPerMinute, burstCapacity);

        return Bandwidth.builder()
                .capacity(burstCapacity)
                .refillGreedy(requestsPerMinute, Duration.ofMinutes(1))
                .build();
    }
}
