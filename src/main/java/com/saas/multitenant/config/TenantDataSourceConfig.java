package com.saas.multitenant.config;

import com.saas.multitenant.domain.tenant.Tenant;
import com.saas.multitenant.domain.tenant.TenantRepository;
import com.saas.multitenant.multitenancy.TenantAwareDataSourceRouter;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

@Slf4j
@Configuration
@RequiredArgsConstructor
public class TenantDataSourceConfig {

    @Value("${datasource.master.url}")
    private String masterUrl;

    @Value("${datasource.master.username}")
    private String masterUser;

    @Value("${datasource.master.password}")
    private String masterPassword;

    
    // The primary DataSource seen by Spring / Hibernate.
    // It is a routing proxy that delegates to per-tenant HikariCP pools.
    // The master DB is the default target (used for system-level queries
    // when no tenant is in context, e.g. during startup).
    
    @Primary
    @Bean
    public DataSource dataSource(TenantRepository tenantRepository) {
        DataSource masterDs = buildHikari("MasterPool", masterUrl, masterUser, masterPassword, 10);

        Map<Object, Object> targetDataSources = new HashMap<>();
        targetDataSources.put("master", masterDs);

      
        tenantRepository.findAll().forEach(tenant -> {
            try {
                DataSource tenantDs = buildTenantDataSource(tenant);
                targetDataSources.put(tenant.getTenantId(), tenantDs);
                log.info("Datasource registered for tenant={}", tenant.getTenantId());
            } catch (Exception e) {
                log.warn("Failed to initialise datasource for tenant={}: {}",
                        tenant.getTenantId(), e.getMessage());
            }
        });

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
                poolSize
        );
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
