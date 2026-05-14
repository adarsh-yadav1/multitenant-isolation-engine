package com.saas.multitenant.multitenancy;

/**
 * ThreadLocal carrier for the current tenant identifier.
 *
 * <p>Set once per request by {@link com.saas.multitenant.filter.TenantIdentificationFilter}
 * and cleared in its {@code finally} block to prevent cross-request contamination
 * in thread-pool environments.
 *
 * <p>Read by:
 * <ul>
 *   <li>{@link TenantAwareDataSourceRouter} — picks the correct HikariCP pool</li>
 *   <li>{@link com.saas.multitenant.ratelimit.RateLimitingInterceptor} — selects the tenant bucket</li>
 * </ul>
 */
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
