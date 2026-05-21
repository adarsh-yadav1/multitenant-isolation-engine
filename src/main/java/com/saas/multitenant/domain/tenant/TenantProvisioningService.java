package com.saas.multitenant.domain.tenant;

import com.saas.multitenant.config.TenantDataSourceConfig;
import com.saas.multitenant.dto.CreateTenantRequest;
import com.saas.multitenant.multitenancy.TenantAwareDataSourceRouter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;

@Slf4j
@Service
@RequiredArgsConstructor
public class TenantProvisioningService {

    private final TenantRepository tenantRepository;
    private final TenantDataSourceConfig tenantDataSourceConfig;
    private final DataSource dataSource; // the router bean

    @Value("${tenant.datasource.default-user:tenant_user}")
    private String defaultTenantUser;

    @Value("${tenant.datasource.default-password:}")
    private String defaultTenantPassword;

    @Transactional
    public Tenant provisionTenant(CreateTenantRequest req) {
        // 1. Guard: don't allow duplicate tenantId
        if (tenantRepository.findByTenantId(req.getTenantId()).isPresent()) {
            throw new IllegalArgumentException(
                    "Tenant already exists: " + req.getTenantId());
        }

        // 2. Build DB URL pointing at the tenant's MySQL container
        // Convention: mysql-<tenantId>:3306/<tenantId>_db
        // For pre-provisioned DBs (tenant-a, tenant-b) use their host directly
        String dbUrl = "jdbc:mysql://mysql-" + req.getTenantId()
                + ":3306/" + req.getTenantId() + "_db"
                + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";

        // 3. Persist tenant record in master DB
        Tenant tenant = Tenant.builder()
                .tenantId(req.getTenantId())
                .companyName(req.getName())
                .tier(req.getTier())
                .status(TenantStatus.ACTIVE)
                .databaseUrl(req.getDatabaseUrl())
                .databaseUsername(defaultTenantUser)
                .databasePassword(defaultTenantPassword)
                .build();

        tenant = tenantRepository.save(tenant);
        log.info("Tenant persisted: {}", req.getTenantId());

        // 4. Register datasource at runtime so this tenant's requests
        // are routed immediately without a restart
        try {
            TenantAwareDataSourceRouter router = (TenantAwareDataSourceRouter) dataSource;
            tenantDataSourceConfig.addTenantDataSource(router, tenant);
        } catch (Exception e) {
            log.warn("Could not register datasource for tenant={}: {}",
                    req.getTenantId(), e.getMessage());
            // Tenant is saved — datasource will be picked up on next restart
        }

        return tenant;
    }

    @Transactional
    @CacheEvict(value = { "activeTenants", "tenantActiveStatus" }, key = "#tenantId")
    public Tenant updateStatus(String tenantId, TenantStatus newStatus) {
        Tenant tenant = tenantRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Tenant not found: " + tenantId));
        tenant.setStatus(newStatus);
        return tenantRepository.save(tenant);
    }

    @Transactional
    @CacheEvict(value = { "activeTenants", "tenantActiveStatus" }, key = "#tenantId")
    public Tenant updateRateLimit(String tenantId, int requestsPerMinute) {
        Tenant tenant = tenantRepository.findByTenantId(tenantId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Tenant not found: " + tenantId));
        tenant.setRequestsPerMinute(requestsPerMinute);
        return tenantRepository.save(tenant);
    }
}