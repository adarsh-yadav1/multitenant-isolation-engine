package com.saas.multitenant.domain.tenant;

import com.saas.multitenant.config.TenantDataSourceConfig;
import com.saas.multitenant.dto.CreateTenantRequest;
import com.saas.multitenant.multitenancy.TenantAwareDataSourceRouter;
import com.saas.multitenant.security.AesEncryptionService;
import com.zaxxer.hikari.HikariDataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.sql.DataSource;

@Slf4j
@Service
@RequiredArgsConstructor
public class TenantProvisioningService {

    private final AesEncryptionService encryptionService;
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

        // 2. Persist tenant record in master DB
        Tenant tenant = Tenant.builder()
                .tenantId(req.getTenantId())
                .companyName(req.getName())
                .tier(req.getTier())
                .status(TenantStatus.ACTIVE)
                .databaseUrl(req.getDatabaseUrl())
                .databaseUsername(defaultTenantUser)
                .databasePassword(encryptionService.encrypt(defaultTenantPassword))
                .build();

        tenant = tenantRepository.save(tenant);
        log.info("Tenant persisted: {}", req.getTenantId());

        // 3. Run Flyway migrations on the tenant DB
        runTenantMigrations(req.getDatabaseUrl());

        // 4. Register datasource at runtime so requests route immediately
        try {
            TenantAwareDataSourceRouter router = (TenantAwareDataSourceRouter) dataSource;
            tenantDataSourceConfig.addTenantDataSource(router, tenant);
        } catch (Exception e) {
            log.warn("Could not register datasource for tenant={}: {}",
                    req.getTenantId(), e.getMessage());
        }

        return tenant;
    }

    private void runTenantMigrations(String dbUrl) {
        // Build a direct datasource pointing at the tenant DB — not the router
        HikariDataSource tenantDs = new HikariDataSource();
        tenantDs.setJdbcUrl(dbUrl);
        tenantDs.setUsername(defaultTenantUser);
        tenantDs.setPassword(defaultTenantPassword);
        tenantDs.setMaximumPoolSize(2);
        tenantDs.setConnectionTimeout(30_000);

        try {
            Flyway flyway = Flyway.configure()
                    .dataSource(tenantDs)
                    .locations("classpath:db/migration/tenant")
                    .baselineOnMigrate(true)
                    .load();
            flyway.migrate();
            log.info("Flyway migration completed for db: {}", dbUrl);
        } catch (Exception e) {
            log.error("Flyway migration failed for db={}: {}", dbUrl, e.getMessage());
            throw new RuntimeException("Failed to run tenant migrations: " + e.getMessage(), e);
        } finally {
            tenantDs.close(); // always release the temp pool
        }
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