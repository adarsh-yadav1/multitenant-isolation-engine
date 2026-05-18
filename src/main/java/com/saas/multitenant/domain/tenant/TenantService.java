package com.saas.multitenant.domain.tenant;

import com.saas.multitenant.exception.TenantNotFoundException;
import com.saas.multitenant.exception.TenantSuspendedException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


// Business logic for tenant lifecycle management

// All reads use the master datasource (the default/fallback datasource
// registered in TenantDataSourceConfig). Results are cached to avoid
// hitting the DB on every request — the filter and rate limiter call
// these methods per-request.

@Slf4j
@Service
@RequiredArgsConstructor
public class TenantService {

    private final TenantRepository tenantRepository;
    private final RateLimitEventRepository rateLimitEventRepository;

 
    //  Returns the tenant if ACTIVE, throws otherwise.
    //  Used by TenantBucketManager and TenantDataSourceConfig.
  
    @Cacheable(value = "activeTenants", key = "#tenantId")
    @Transactional(readOnly = true)
    public Tenant getActiveTenant(String tenantId) {
        Tenant tenant = tenantRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new TenantNotFoundException(tenantId));

        if (tenant.getStatus() == TenantStatus.SUSPENDED) {
            throw new TenantSuspendedException(tenantId);
        }
        if (tenant.getStatus() == TenantStatus.DEPROVISIONED) {
            throw new TenantNotFoundException(tenantId);
        }

        return tenant;
    }

    
    //  Quick active-check used by TenantIdentificationFilter.
    //  Returns false rather than throwing so the filter can return the right HTTP status.
     
    @Cacheable(value = "tenantActiveStatus", key = "#tenantId")
    @Transactional(readOnly = true)
    public boolean isActiveTenant(String tenantId) {
        return tenantRepository.findByTenantId(tenantId)
                .map(t -> t.getStatus() == TenantStatus.ACTIVE)
                .orElse(false);
    }

    
    //   Persists a rate-limit-exceeded event asynchronously so it doesn't add
      latency to the request that was already rejected with HTTP 429.
    @Async
    public void recordRateLimitEvent(String tenantId, String endpoint) {
        try {
            RateLimitEvent event = RateLimitEvent.builder()
                    .tenantId(tenantId)
                    .endpoint(endpoint)
                    .build();
            rateLimitEventRepository.save(event);
        } 
        catch (Exception e) {
            log.warn("Failed to record rate-limit event for tenant={} endpoint={}: {}",
                    tenantId, endpoint, e.getMessage());
        }
    }
}
