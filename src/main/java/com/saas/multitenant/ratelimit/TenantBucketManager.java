package com.saas.multitenant.ratelimit;

import com.saas.multitenant.domain.tenant.Tenant;
import com.saas.multitenant.domain.tenant.TenantService;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.BucketConfiguration;
import io.github.bucket4j.distributed.proxy.ProxyManager;
import io.github.bucket4j.Bucket;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.function.Supplier;


// Central authority for Bucket4j token bucket lifecycle
// Each tenant's bucket is stored in Redis at key rate_limit:{tenantId}
// The ProxyManager handles serialization, atomic operations via Lua scripts
// and ensures distributed consistency across multiple application instances

// Key behaviours:
//   Buckets are created lazily on first request
//   Bucket configuration is loaded from the tenant record (supports per-tenant overrides)
//   Calling  #resetBucketForTenant(String) deletes the Redis key so the
//   next request re-creates it with updated limits — enabling zero-downtime rate limit changes


@Slf4j
@Service
@RequiredArgsConstructor
public class TenantBucketManager {

    private static final String BUCKET_KEY_PREFIX = "rate_limit:";

    private final ProxyManager<String> bucketProxyManager;
    private final TenantService tenantService;

    
    // Returns (or lazily creates) the Bucket4j bucket for the given tenant
    // Backed by Redis — safe to call from multiple application instances concurrently
    
    public Bucket getBucketForTenant(String tenantId) {
        Supplier<BucketConfiguration> configSupplier = () -> buildConfiguration(tenantId);
        return bucketProxyManager.builder().build(BUCKET_KEY_PREFIX + tenantId, configSupplier);
    }

    
    // Deletes the Redis bucket key so the next request re-creates it with the
    // current (updated) rate limit configuration. Call after modifying a tenant's
    // rate limit in the database
    
    @CacheEvict(value = "tenantRateLimitConfig", key = "#tenantId")
    public void resetBucketForTenant(String tenantId) {
        bucketProxyManager.removeProxy(BUCKET_KEY_PREFIX + tenantId);
        log.info("Rate limit bucket reset for tenant={}", tenantId);
    }

    Returns the configured requests-per-minute for response headers.
    @Cacheable(value = "tenantRateLimitConfig", key = "#tenantId")
    public long getRateLimitForTenant(String tenantId) {
        Tenant tenant = tenantService.getActiveTenant(tenantId);
        return tenant.getEffectiveRequestsPerMinute();
    }

    //  Asynchronously persist a rate-limit-exceeded event for analytics
    public void logRateLimitEvent(String tenantId, String endpoint) {
        tenantService.recordRateLimitEvent(tenantId, endpoint);
    }


    private BucketConfiguration buildConfiguration(String tenantId) {
        Tenant tenant = tenantService.getActiveTenant(tenantId);

        long requestsPerMinute = tenant.getEffectiveRequestsPerMinute();
        long burstCapacity = tenant.getEffectiveBucketCapacity();

        log.debug("Building bucket for tenant={} rpm={} burst={}",
                tenantId, requestsPerMinute, burstCapacity);

        Bandwidth limit = Bandwidth.builder()
                .capacity(burstCapacity)
                .refillGreedy(requestsPerMinute, Duration.ofMinutes(1))
                .build();

        return BucketConfiguration.builder()
                .addLimit(limit)
                .build();
    }
}
