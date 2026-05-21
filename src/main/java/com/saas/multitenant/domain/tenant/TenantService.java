package com.saas.multitenant.domain.tenant;

import com.saas.multitenant.exception.TenantNotFoundException;
import com.saas.multitenant.exception.TenantSuspendedException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
public class TenantService {

    private final JdbcTemplate masterJdbc;
    private final RateLimitEventRepository rateLimitEventRepository;

    // Inject the raw master DataSource — NOT the routing proxy
    public TenantService(@Qualifier("masterDataSource") DataSource masterDataSource,
            RateLimitEventRepository rateLimitEventRepository) {
        this.masterJdbc = new JdbcTemplate(masterDataSource);
        this.rateLimitEventRepository = rateLimitEventRepository;
    }

    @Cacheable(value = "activeTenants", key = "#tenantId")
    public Tenant getActiveTenant(String tenantId) {
        List<Map<String, Object>> rows = masterJdbc.queryForList(
                "SELECT * FROM tenants WHERE tenant_id = ?", tenantId);

        if (rows.isEmpty())
            throw new TenantNotFoundException(tenantId);

        Map<String, Object> row = rows.get(0);
        String status = (String) row.get("status");

        if ("SUSPENDED".equals(status))
            throw new TenantSuspendedException(tenantId);
        if ("DEPROVISIONED".equals(status))
            throw new TenantNotFoundException(tenantId);

        return mapRowToTenant(row);
    }

    @Cacheable(value = "tenantActiveStatus", key = "#tenantId")
    public boolean isActiveTenant(String tenantId) {
        List<Map<String, Object>> rows = masterJdbc.queryForList(
                "SELECT status FROM tenants WHERE tenant_id = ?", tenantId);
        return !rows.isEmpty() && "ACTIVE".equals(rows.get(0).get("status"));
    }

    @Async
    public void recordRateLimitEvent(String tenantId, String endpoint) {
        try {
            RateLimitEvent event = RateLimitEvent.builder()
                    .tenantId(tenantId)
                    .endpoint(endpoint)
                    .build();
            rateLimitEventRepository.save(event);
        } catch (Exception e) {
            log.warn("Failed to record rate-limit event for tenant={} endpoint={}: {}",
                    tenantId, endpoint, e.getMessage());
        }
    }

    private Tenant mapRowToTenant(Map<String, Object> row) {
        return Tenant.builder()
                .id((String) row.get("id"))
                .tenantId((String) row.get("tenant_id"))
                .companyName((String) row.get("company_name"))
                .status(TenantStatus.valueOf((String) row.get("status")))
                .tier(TenantTier.valueOf((String) row.get("tier")))
                .databaseUrl((String) row.get("database_url"))
                .databaseUsername((String) row.get("database_username"))
                .databasePassword((String) row.get("database_password"))
                .requestsPerMinute(row.get("requests_per_minute") != null
                        ? ((Number) row.get("requests_per_minute")).intValue()
                        : null)
                .bucketCapacity(row.get("bucket_capacity") != null
                        ? ((Number) row.get("bucket_capacity")).intValue()
                        : null)
                .build();
    }
}