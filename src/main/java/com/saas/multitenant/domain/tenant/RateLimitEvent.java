package com.saas.multitenant.domain.tenant;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "rate_limit_events")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RateLimitEvent {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tenant_id", nullable = false, length = 100)
    private String tenantId;

    @Column(name = "endpoint", nullable = false)
    private String endpoint;

    @Column(name = "occurred_at", updatable = false)
    @Builder.Default
    private Instant occurredAt = Instant.now();

    @PrePersist
    protected void onCreate() {
        if (occurredAt == null) occurredAt = Instant.now();
    }
}
