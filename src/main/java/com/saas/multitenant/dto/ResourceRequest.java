package com.saas.multitenant.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class ResourceRequest {
    @NotBlank
    private String name;
    private String data;
}