-- docker/init-scripts/master-init.sql
-- Runs once when the mysql-master container is first created.
-- Flyway migrations handle schema; this seeds dev data only.

-- Nothing to seed automatically — tenants are registered via the admin API.
-- Flyway will create the tables on first application startup.

-- Tenant database users are created by the tenant database containers.
