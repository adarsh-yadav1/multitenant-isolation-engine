-- V1__create_tenant_business_schema.sql
-- Applied to EACH tenant's isolated database when provisioned.
-- Managed by Flyway — do NOT edit; create V2__ for changes.

CREATE TABLE IF NOT EXISTS users (
    id           CHAR(36)     NOT NULL PRIMARY KEY,
    email        VARCHAR(255) NOT NULL UNIQUE,
    display_name VARCHAR(255) NULL,
    role         ENUM('ADMIN','MEMBER','VIEWER') NOT NULL DEFAULT 'MEMBER',
    is_active    BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at   TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


CREATE TABLE IF NOT EXISTS resources (
    id          CHAR(36)      NOT NULL PRIMARY KEY,
    name        VARCHAR(255)  NOT NULL,
    description TEXT          NULL,
    owner_id    CHAR(36)      NOT NULL COMMENT 'References users.id within this tenant',
    metadata    JSON          NULL,
    created_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at  TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    INDEX idx_resource_owner (owner_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


CREATE TABLE IF NOT EXISTS audit_logs (
    id          BIGINT        NOT NULL AUTO_INCREMENT PRIMARY KEY,
    user_id     CHAR(36)      NULL COMMENT 'NULL for system actions',
    action      VARCHAR(100)  NOT NULL,
    entity_type VARCHAR(100)  NOT NULL,
    entity_id   VARCHAR(100)  NOT NULL,
    details     JSON          NULL,
    occurred_at TIMESTAMP     NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_audit_user (user_id),
    INDEX idx_audit_occurred (occurred_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;


CREATE TABLE IF NOT EXISTS settings (
    setting_key   VARCHAR(100) NOT NULL PRIMARY KEY,
    setting_value TEXT         NULL,
    updated_at    TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;
