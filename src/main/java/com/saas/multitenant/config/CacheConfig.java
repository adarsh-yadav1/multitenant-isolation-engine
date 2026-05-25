package com.saas.multitenant.config;

import com.github.benmanes.caffeine.cache.Caffeine;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.caffeine.CaffeineCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.concurrent.TimeUnit;

@Configuration
@EnableCaching
public class CacheConfig {

    @Bean
    public CacheManager cacheManager() {
        CaffeineCacheManager manager = new CaffeineCacheManager();

        // activeTenants — used by TenantService.getActiveTenant()
        // TTL: 5 minutes — balance between performance and stale data
        // If a tenant is suspended, it takes max 5 min to take effect
        manager.registerCustomCache("activeTenants",
                Caffeine.newBuilder()
                        .expireAfterWrite(5, TimeUnit.MINUTES)
                        .maximumSize(1000)
                        .recordStats()
                        .build());

        // tenantActiveStatus — used by TenantService.isActiveTenant()
        // TTL: 2 minutes — shorter because this gates every request
        manager.registerCustomCache("tenantActiveStatus",
                Caffeine.newBuilder()
                        .expireAfterWrite(2, TimeUnit.MINUTES)
                        .maximumSize(1000)
                        .recordStats()
                        .build());

        // tenantRateLimitConfig — used by TenantBucketManager.getRateLimitForTenant()
        // TTL: 10 minutes — rate limit changes are rare
        manager.registerCustomCache("tenantRateLimitConfig",
                Caffeine.newBuilder()
                        .expireAfterWrite(10, TimeUnit.MINUTES)
                        .maximumSize(1000)
                        .recordStats()
                        .build());

        return manager;
    }
}