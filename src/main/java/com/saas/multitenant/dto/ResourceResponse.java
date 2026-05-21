package com.saas.multitenant.dto;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;

@Data
@Builder
public class ResourceResponse {
    private Long id;
    private String name;
    private String data;
    private String tenantId;
    private LocalDateTime createdAt;
}