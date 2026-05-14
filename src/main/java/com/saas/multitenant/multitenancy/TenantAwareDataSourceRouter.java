package com.saas.multitenant.multitenancy;

import org.springframework.jdbc.datasource.lookup.AbstractRoutingDataSource;

/**
 * Routes Hibernate/JPA database connections to the correct tenant datasource.
 *
 * <p>Spring's {@link AbstractRoutingDataSource} maintains a map of
 * {@code lookupKey -> DataSource}. On every {@code getConnection()} call it
 * invokes {@link #determineCurrentLookupKey()} and returns a connection from
 * the matching pool.
 *
 * <p>The entire routing logic is a single line: read the tenant ID from
 * {@link TenantContext}. All complexity lives in how the map is built
 * ({@link com.saas.multitenant.config.TenantDataSourceConfig}) and how the
 * tenant ID gets into the ThreadLocal
 * ({@link com.saas.multitenant.filter.TenantIdentificationFilter}).
 */
public class TenantAwareDataSourceRouter extends AbstractRoutingDataSource {

    @Override
    protected Object determineCurrentLookupKey() {
        return TenantContext.getCurrentTenant();
    }
}
