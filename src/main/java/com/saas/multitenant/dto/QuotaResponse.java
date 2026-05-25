package com.saas.multitenant.dto;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class QuotaResponse {
    private String tenantId;
    private long limitPerMinute;
    private long burstCapacity;
    private long remainingTokens;
    private boolean throttled;
}