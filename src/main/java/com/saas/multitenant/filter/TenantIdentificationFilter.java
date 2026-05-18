package com.saas.multitenant.filter;

import com.saas.multitenant.domain.tenant.TenantService;
import com.saas.multitenant.multitenancy.TenantContext;
import com.saas.multitenant.security.JwtTokenProvider;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

 
//  Extracts the tenant identifier from every HTTP request and stores it in
//  TenantContext for the duration of the request thread
// 
//  Supports two identification strategies:
//    Header-based: X-Tenant-ID: company_a — for internal services
//    JWT-based: Authorization: Bearer <token> containing a tenantId claim — for external clients

//  Header takes precedence over JWT when both are present
//  Unknown or SUSPENDED tenants receive 401/403 before reaching any controller
//  TenantContext#clear() is always called in finally to prevent memory leaks in Tomcat's thread pool.
 
@Slf4j
@Component
@RequiredArgsConstructor
public class TenantIdentificationFilter extends OncePerRequestFilter {

    public static final String TENANT_HEADER = "X-Tenant-ID";
    public static final String BEARER_PREFIX = "Bearer ";

    private final TenantService tenantService;
    private final JwtTokenProvider jwtTokenProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        try 
        {
            String tenantId = resolveTenantId(request);

            if (!StringUtils.hasText(tenantId)) 
            {
                sendError(response, HttpServletResponse.SC_UNAUTHORIZED,
                        "Missing tenant identifier. Provide X-Tenant-ID header or a valid Bearer token.");
                return;
            }

            // Validate tenant exists and is active (cached to avoid per-request DB hit)
            if (!tenantService.isActiveTenant(tenantId)) {
                sendError(response, HttpServletResponse.SC_FORBIDDEN,
                        "Tenant '" + tenantId + "' is not active or does not exist.");
                return;
            }

            TenantContext.setCurrentTenant(tenantId);
            MDC.put("tenantId", tenantId);  

            filterChain.doFilter(request, response);

        } 
        finally 
        {
            TenantContext.clear();
            MDC.remove("tenantId");
        }
    }

    //  Skip the filter for actuator / admin endpoints that don't carry a tenant ID.
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        return path.startsWith("/actuator")
                || path.startsWith("/admin/")
                || path.equals("/error");
    }

    private String resolveTenantId(HttpServletRequest request) 
    {
        // 1. Explicit header (internal service-to-service calls)
        String header = request.getHeader(TENANT_HEADER);
        if (StringUtils.hasText(header)) 
        {
            return header.trim().toLowerCase();
        }

        // 2. JWT Bearer token
        String authHeader = request.getHeader("Authorization");
        if (StringUtils.hasText(authHeader) && authHeader.startsWith(BEARER_PREFIX)) 
        {
            String token = authHeader.substring(BEARER_PREFIX.length());
            return jwtTokenProvider.extractTenantId(token);
        }

        return null;
    }

    private void sendError(HttpServletResponse response, int status, String message)
            throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"error\":\"" + message + "\"}");
    }
}
