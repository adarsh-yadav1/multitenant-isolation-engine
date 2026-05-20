-- V1__create_tenant_registry.sql
-- Master database: tenant registry schema
-- Managed by Flyway — do NOT edit; create a new migration for changes.

CREATE TABLE IF NOT EXISTS tenants (
    id               VARCHAR(36)      NOT NULL PRIMARY KEY COMMENT 'UUID primary key',
    tenant_id        VARCHAR(100)  NOT NULL UNIQUE COMMENT 'Human-readable slug used in requests and Redis keys',
    company_name     VARCHAR(255)  NOT NULL,
    status           ENUM('ACTIVE','SUSPENDED','DEPROVISIONED') NOT NULL DEFAULT 'ACTIVE',
    tier             ENUM('FREE','STARTER','PROFESSIONAL','ENTERPRISE','CUSTOM') NOT NULL DEFAULT 'FREE',
    database_url     VARCHAR(500)  NOT NULL COMMENT 'JDBC connection URL for tenant-isolated database',
    database_username VARCHAR(100) NOT NULL,
    database_password VARCHAR(500) NOT NULL COMMENT 'AES-256 encrypted password',
    requests_per_minute INT        NULL COMMENT 'Override — NULL means use tier default',
    bucket_capacity   INT          NULL COMMENT 'Override burst capacity — NULL means use tier default',
    created_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at        TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_tenant_status (status),
    INDEX idx_tenant_tier (tier)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


CREATE TABLE IF NOT EXISTS api_keys (
    id          CHAR(36)     NOT NULL PRIMARY KEY,
    tenant_id   CHAR(36)     NOT NULL,
    key_hash    VARCHAR(64)  NOT NULL COMMENT 'SHA-256 hash of the raw API key',
    description VARCHAR(255) NULL,
    expires_at  TIMESTAMP    NULL COMMENT 'NULL = non-expiring',
    is_active   BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_api_keys_tenant FOREIGN KEY (tenant_id) REFERENCES tenants(id) ON DELETE CASCADE,
    INDEX idx_key_hash (key_hash),
    INDEX idx_key_tenant (tenant_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


CREATE TABLE IF NOT EXISTS rate_limit_events (
    id          BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    tenant_id   VARCHAR(100)  NOT NULL COMMENT 'Denormalized slug for query performance',
    endpoint    VARCHAR(255)  NOT NULL,
    occurred_at TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_rle_tenant (tenant_id),
    INDEX idx_rle_occurred (occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
