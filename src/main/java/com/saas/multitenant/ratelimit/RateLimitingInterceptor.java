package com.saas.multitenant.ratelimit;

import com.saas.multitenant.multitenancy.TenantContext;
import io.github.bucket4j.Bucket;
import io.github.bucket4j.ConsumptionProbe;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import java.io.IOException;
import java.util.concurrent.TimeUnit;


//  Spring HandlerInterceptor that enforces per-tenant rate limits
 
//  Runs in preHandle() — before any controller method executes
//  Uses non-blocking tryConsumeAndReturnRemaining(1)}so the thread
//  is never parked waiting for tokens; requests over the limit are rejected
//  immediately with HTTP 429
 
//  Registered in config.WebMvcConfig for
//  the  /api/** URL pattern only (admin and actuator endpoints are excluded)
 
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitingInterceptor implements HandlerInterceptor {

    private final TenantBucketManager bucketManager;

    @Override
    public boolean preHandle(HttpServletRequest request,
                             HttpServletResponse response,
                             Object handler) throws Exception {

        String tenantId = TenantContext.getCurrentTenant();
        if (tenantId == null) {
            // Filter should have already rejected the request; this is a safety net
            sendRateLimitError(response, 0, 0, "Unknown tenant");
            return false;
        }

        Bucket bucket = bucketManager.getBucketForTenant(tenantId);
        ConsumptionProbe probe = bucket.tryConsumeAndReturnRemaining(1);

        // Set informational headers on every response (allowed or rejected)
        long limit = bucketManager.getRateLimitForTenant(tenantId);
        response.setHeader("X-RateLimit-Limit", String.valueOf(limit));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(probe.getRemainingTokens()));

        if (probe.isConsumed()) {
            return true;   // Token consumed — request proceeds
        }

        // Throttled
        long retryAfterSeconds = TimeUnit.NANOSECONDS.toSeconds(probe.getNanosToWaitForRefill()) + 1;
        log.warn("Rate limit exceeded for tenant={} path={}", tenantId, request.getRequestURI());
        bucketManager.logRateLimitEvent(tenantId, request.getRequestURI());

        sendRateLimitError(response, retryAfterSeconds, limit,
                "Rate limit exceeded. Retry after " + retryAfterSeconds + " seconds.");
        return false;
    }

    private void sendRateLimitError(HttpServletResponse response,
                                    long retryAfterSeconds,
                                    long limit,
                                    String message) throws IOException {
        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("Retry-After", String.valueOf(retryAfterSeconds));
        response.getWriter().write("""
                {"error":"TOO_MANY_REQUESTS","message":"%s","retryAfterSeconds":%d,"rateLimitPerMinute":%d}
                """.formatted(message, retryAfterSeconds, limit).trim());
    }
}
