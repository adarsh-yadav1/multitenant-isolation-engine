package com.saas.multitenant.domain.tenant;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UuidGenerator;

import java.time.Instant;

/**
 * Central registry record for a tenant in the master database.
 *
 * <p>This entity is always read from the MASTER datasource (the default
 * fallback in {@link com.saas.multitenant.config.TenantDataSourceConfig}).
 * It is never stored in a tenant's isolated database.
 */
@Entity
@Table(name = "tenants")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Tenant {

    @Id
    @UuidGenerator
    @Column(columnDefinition = "CHAR(36)", updatable = false, nullable = false)
    private String id;

    @Column(name = "tenant_id", length = 100, nullable = false, unique = true)
    private String tenantId;

    @Column(name = "company_name", nullable = false)
    private String companyName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TenantStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private TenantTier tier;

    @Column(name = "database_url", nullable = false)
    private String databaseUrl;

    @Column(name = "database_username", nullable = false)
    private String databaseUsername;

    /** Stored AES-256 encrypted. Never log or expose in API responses. */
    @Column(name = "database_password", nullable = false)
    private String databasePassword;

    /** Nullable — NULL means use tier default. */
    @Column(name = "requests_per_minute")
    private Integer requestsPerMinute;

    @Column(name = "bucket_capacity")
    private Integer bucketCapacity;

    @Column(name = "created_at", updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = Instant.now();
        updatedAt = Instant.now();
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = Instant.now();
    }

    // ─── Computed helpers ──────────────────────────────────────────────────────

    public long getEffectiveRequestsPerMinute() {
        if (requestsPerMinute != null) return requestsPerMinute;
        return switch (tier) {
            case FREE -> 60L;
            case STARTER -> 200L;
            case PROFESSIONAL -> 600L;
            case ENTERPRISE -> 2000L;
            case CUSTOM -> 100L;  // fallback for mis-configured CUSTOM tenants
        };
    }

    public long getEffectiveBucketCapacity() {
        if (bucketCapacity != null) return bucketCapacity;
        return switch (tier) {
            case FREE -> 60L;
            case STARTER -> 300L;
            case PROFESSIONAL -> 900L;
            case ENTERPRISE -> 3000L;
            case CUSTOM -> 150L;
        };
    }

    public boolean isActive() {
        return status == TenantStatus.ACTIVE;
    }
}
