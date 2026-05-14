package com.saas.multitenant.domain.tenant;

import com.saas.multitenant.exception.TenantNotFoundException;
import com.saas.multitenant.exception.TenantSuspendedException;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class TenantService {

    private final TenantRepository tenantRepository;
    private final JdbcTemplate jdbcTemplate;

    @Cacheable(value = "activeTenants", key = "#tenantId")
    @Transactional(readOnly = true)
    public boolean isActiveTenant(String tenantId) {
        return tenantRepository.findByTenantId(tenantId)
                .map(Tenant::isActive)
                .orElse(false);
    }

    @Transactional(readOnly = true)
    public Tenant getActiveTenant(String tenantId) {
        Tenant tenant = tenantRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new TenantNotFoundException(tenantId));
        if (!tenant.isActive()) {
            throw new TenantSuspendedException(tenantId);
        }
        return tenant;
    }

    @Async
    public void recordRateLimitEvent(String tenantId, String endpoint) {
        jdbcTemplate.update(
                "INSERT INTO rate_limit_events (tenant_id, endpoint) VALUES (?, ?)",
                tenantId,
                endpoint);
    }
}
