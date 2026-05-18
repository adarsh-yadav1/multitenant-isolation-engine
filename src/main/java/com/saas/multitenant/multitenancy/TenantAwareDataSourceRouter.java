package com.saas.multitenant.multitenancy;

import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;


//   Routes Hibernate/JPA database connections to the correct tenant datasource.
 
//   Spring's AbstractRoutingDataSource maintains a map of
//   lookupKey -> DataSource. On every getConnection() call it
//   invokes #determineCurrentLookupKey() and returns a connection from
//   the matching pool.
 
//   The entire routing logic is a single line: read the tenant ID from
//   TenantContext. All complexity lives in how the map is built
//   (config.TenantDataSourceConfig) and how the
//   tenant ID gets into the ThreadLocal

public class TenantAwareDataSourceRouter extends AbstractRoutingDataSource {

    // Mutable copy so we can add datasources at runtime without restarting
    private final java.util.Map<Object, Object> targetDataSourceMap = new java.util.HashMap<>();

    @Override
    public void setTargetDataSources(java.util.Map<Object, Object> targetDataSources) {
        targetDataSourceMap.putAll(targetDataSources);
        super.setTargetDataSources(targetDataSourceMap);
    }

    //  Adds a new tenant datasource at runtime without restarting the application.
    public void addTargetDataSource(String tenantId, javax.sql.DataSource dataSource) {
        targetDataSourceMap.put(tenantId, dataSource);
        super.setTargetDataSources(targetDataSourceMap);
    }

    @Override
    protected Object determineCurrentLookupKey() {
        return TenantContext.getCurrentTenant();
    }
}
