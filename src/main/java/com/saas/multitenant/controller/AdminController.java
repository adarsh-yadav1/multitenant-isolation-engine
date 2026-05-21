package com.saas.multitenant.controller;

import com.saas.multitenant.domain.tenant.*;
import com.saas.multitenant.dto.CreateTenantRequest;
import com.saas.multitenant.dto.TenantResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/admin/tenants")
@RequiredArgsConstructor
@Tag(name = "Admin", description = "Tenant provisioning and management")
public class AdminController {

    private final TenantProvisioningService provisioningService;
    private final TenantRepository tenantRepository;

    @PostMapping
    @Operation(summary = "Register and provision a new tenant")
    public ResponseEntity<TenantResponse> registerTenant(
            @Valid @RequestBody CreateTenantRequest req) {
        Tenant tenant = provisioningService.provisionTenant(req);
        return ResponseEntity.status(201).body(toResponse(tenant));
    }

    @GetMapping
    @Operation(summary = "List all tenants")
    public List<TenantResponse> listTenants() {
        return tenantRepository.findAll()
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @PatchMapping("/{tenantId}/status")
    @Operation(summary = "Suspend or reactivate a tenant")
    public TenantResponse updateStatus(
            @PathVariable String tenantId,
            @RequestParam TenantStatus status) {
        return toResponse(provisioningService.updateStatus(tenantId, status));
    }

    @PatchMapping("/{tenantId}/rate-limit")
    @Operation(summary = "Override rate limit for a tenant")
    public TenantResponse updateRateLimit(
            @PathVariable String tenantId,
            @RequestParam int requestsPerMinute) {
        return toResponse(
                provisioningService.updateRateLimit(tenantId, requestsPerMinute));
    }

    private TenantResponse toResponse(Tenant tenant) {
        return TenantResponse.builder()
                .tenantId(tenant.getTenantId())
                .name(tenant.getCompanyName())
                .tier(tenant.getTier())
                .status(tenant.getStatus().name())
                .rateLimitPerMinute((int) tenant.getEffectiveRequestsPerMinute())
                .createdAt(tenant.getCreatedAt() != null
                        ? tenant.getCreatedAt().atZone(java.time.ZoneOffset.UTC)
                                .toLocalDateTime()
                        : null)
                .build();
    }
}