package com.saas.multitenant.controller;

import com.saas.multitenant.domain.tenant.TenantService;
import com.saas.multitenant.security.JwtTokenProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Tag(name = "Auth", description = "Issue JWT tokens for tenants and admins")
public class AuthController {

    private final JwtTokenProvider jwtTokenProvider;
    private final TenantService tenantService;

    @Value("${app.security.admin-username:admin}")
    private String adminUsername;

    @Value("${app.security.admin-password:admin-secret}")
    private String adminPassword;

    // Issues a tenant JWT — used by tenant clients instead of X-Tenant-ID header
    @PostMapping("/token")
    @Operation(summary = "Issue a tenant JWT token")
    public ResponseEntity<?> issueTenantToken(@RequestBody TenantTokenRequest req) {
        // Verify tenant exists and is active
        if (!tenantService.isActiveTenant(req.getTenantId())) {
            return ResponseEntity.status(403)
                    .body(Map.of("error", "Tenant not found or not active"));
        }

        String token = jwtTokenProvider.generateTenantToken(req.getTenantId());
        return ResponseEntity.ok(Map.of(
                "token", token,
                "tenantId", req.getTenantId(),
                "type", "Bearer"));
    }

    // Issues an admin JWT — used to call /admin/** endpoints
    @PostMapping("/admin/token")
    @Operation(summary = "Issue an admin JWT token")
    public ResponseEntity<?> issueAdminToken(@RequestBody AdminTokenRequest req) {
        if (!adminUsername.equals(req.getUsername()) ||
                !adminPassword.equals(req.getPassword())) {
            return ResponseEntity.status(401)
                    .body(Map.of("error", "Invalid admin credentials"));
        }

        String token = jwtTokenProvider.generateAdminToken(req.getUsername());
        return ResponseEntity.ok(Map.of(
                "token", token,
                "username", req.getUsername(),
                "type", "Bearer"));
    }

    @Data
    static class TenantTokenRequest {
        private String tenantId;
    }

    @Data
    static class AdminTokenRequest {
        private String username;
        private String password;
    }
}