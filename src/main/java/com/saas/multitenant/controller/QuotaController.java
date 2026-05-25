package com.saas.multitenant.controller;

import com.saas.multitenant.dto.QuotaResponse;
import com.saas.multitenant.multitenancy.TenantContext;
import com.saas.multitenant.ratelimit.TenantBucketManager;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/quota")
@RequiredArgsConstructor
@Tag(name = "Quota", description = "Tenant rate limit usage")
public class QuotaController {

    private final TenantBucketManager bucketManager;

    @GetMapping
    @Operation(summary = "Get current tenant's rate limit quota")
    public QuotaResponse getQuota() {
        String tenantId = TenantContext.getCurrentTenant();
        return bucketManager.getQuota(tenantId);
    }
}