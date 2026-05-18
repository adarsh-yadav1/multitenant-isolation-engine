package com.saas.multitenant.multitenancy;


// ThreadLocal carrier for the current tenant identifier
// Set once per request by filter.TenantIdentificationFilter
// and cleared in its finally block to prevent cross-request contamination
// in thread-pool environments
// Read by:
//   TenantAwareDataSourceRouter— picks the correct HikariCP pool
//   ratelimit.RateLimitingInterceptor — selects Redis bucket
//   TenantIdentifierResolver— tells Hibernate which schema to use


public final class TenantContext {

    private static final ThreadLocal<String> CURRENT_TENANT = new ThreadLocal<>();

    private TenantContext() {}

    public static void setCurrentTenant(String tenantId) {
        CURRENT_TENANT.set(tenantId);
    }

    public static String getCurrentTenant() {
        return CURRENT_TENANT.get();
    }

    public static void clear() {
        CURRENT_TENANT.remove();
    }
}
