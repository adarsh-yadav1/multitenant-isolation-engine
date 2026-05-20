package com.saas.multitenant.config;

import com.saas.multitenant.domain.tenant.Tenant;
import com.saas.multitenant.domain.tenant.TenantTier;
import com.saas.multitenant.multitenancy.TenantAwareDataSourceRouter;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Configuration
public class TenantDataSourceConfig {

    @Value("${datasource.master.url}")
    private String masterUrl;

    @Value("${datasource.master.username}")
    private String masterUser;

    @Value("${datasource.master.password}")
    private String masterPassword;

    @Primary
    @Bean
    public DataSource dataSource() {
        // Build master datasource directly — no JPA, no circular dependency
        HikariDataSource masterDs = (HikariDataSource) buildHikari(
                "MasterPool", masterUrl, masterUser, masterPassword, 10);

        Map<Object, Object> targetDataSources = new HashMap<>();
        targetDataSources.put("master", masterDs);

        // Load existing tenants via plain JDBC — TenantRepository not available yet
        try {
            JdbcTemplate jdbc = new JdbcTemplate(masterDs);
            List<Map<String, Object>> rows = jdbc.queryForList(
                    "SELECT tenant_id, database_url, database_username, database_password, tier " +
                            "FROM tenants WHERE status = 'ACTIVE'");
            for (Map<String, Object> row : rows) {
                String tenantId = (String) row.get("tenant_id");
                try {
                    DataSource tenantDs = buildHikariFromRow(row);
                    targetDataSources.put(tenantId, tenantDs);
                    log.info("Datasource registered for tenant={}", tenantId);
                } catch (Exception e) {
                    log.warn("Failed to initialise datasource for tenant={}: {}", tenantId, e.getMessage());
                }
            }
        } catch (Exception e) {
            // Normal on first boot — tenants table doesn't exist yet
            // Flyway will create it; tenants are registered later via API
            log.info("Could not load tenants at startup (first boot?): {}", e.getMessage());
        }

        TenantAwareDataSourceRouter router = new TenantAwareDataSourceRouter();
        router.setTargetDataSources(targetDataSources);
        router.setDefaultTargetDataSource(masterDs);
        router.afterPropertiesSet();
        return router;
    }

    public void addTenantDataSource(TenantAwareDataSourceRouter router, Tenant tenant) {
        DataSource ds = buildTenantDataSource(tenant);
        router.addTargetDataSource(tenant.getTenantId(), ds);
        router.afterPropertiesSet();
        log.info("Runtime datasource added for tenant={}", tenant.getTenantId());
    }

    private DataSource buildTenantDataSource(Tenant tenant) {
        int poolSize = switch (tenant.getTier()) {
            case FREE -> 2;
            case STARTER -> 5;
            case PROFESSIONAL -> 10;
            case ENTERPRISE -> 20;
            case CUSTOM -> 5;
        };
        return buildHikari(
                "TenantPool-" + tenant.getTenantId(),
                tenant.getDatabaseUrl(),
                tenant.getDatabaseUsername(),
                tenant.getDatabasePassword(),
                poolSize);
    }

    private DataSource buildHikariFromRow(Map<String, Object> row) {
        String tenantId = (String) row.get("tenant_id");
        String tier = (String) row.get("tier");
        int poolSize = switch (TenantTier.valueOf(tier)) {
            case FREE -> 2;
            case STARTER -> 5;
            case PROFESSIONAL -> 10;
            case ENTERPRISE -> 20;
            case CUSTOM -> 5;
        };
        return buildHikari(
                "TenantPool-" + tenantId,
                (String) row.get("database_url"),
                (String) row.get("database_username"),
                (String) row.get("database_password"),
                poolSize);
    }

    private DataSource buildHikari(String poolName, String url, String user,
            String password, int maxPoolSize) {
        HikariConfig cfg = new HikariConfig();
        cfg.setPoolName(poolName);
        cfg.setJdbcUrl(url);
        cfg.setUsername(user);
        cfg.setPassword(password);
        cfg.setMaximumPoolSize(maxPoolSize);
        cfg.setMinimumIdle(Math.min(2, maxPoolSize));
        cfg.setConnectionTimeout(30_000);
        cfg.setIdleTimeout(600_000);
        cfg.setMaxLifetime(1_800_000);
        cfg.setConnectionTestQuery("SELECT 1");
        return new HikariDataSource(cfg);
    }
}