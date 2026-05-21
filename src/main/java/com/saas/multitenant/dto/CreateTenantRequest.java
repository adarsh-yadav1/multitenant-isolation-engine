package com.saas.multitenant.dto;

import com.saas.multitenant.domain.tenant.TenantTier;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

@Data
public class CreateTenantRequest {
    @NotBlank
    private String tenantId;
    @NotBlank
    private String name;
    @NotNull
    private TenantTier tier;
    @Email
    private String contactEmail;
    @NotBlank
    private String databaseUrl;
}