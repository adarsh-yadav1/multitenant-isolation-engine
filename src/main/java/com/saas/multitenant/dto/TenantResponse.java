package com.saas.multitenant.dto;

import com.saas.multitenant.domain.tenant.TenantTier;
import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class TenantResponse {
    private String tenantId;
    private String name;
    private TenantTier tier;
    private String status;
    private int rateLimitPerMinute;
    private LocalDateTime createdAt;
}