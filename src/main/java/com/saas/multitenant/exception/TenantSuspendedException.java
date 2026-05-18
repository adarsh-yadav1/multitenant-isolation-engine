package com.saas.multitenant.exception;

public class TenantSuspendedException extends RuntimeException {
    public TenantSuspendedException(String tenantId) {
        super("Tenant '" + tenantId + "' is suspended. Contact support to reinstate access.");
    }
}
