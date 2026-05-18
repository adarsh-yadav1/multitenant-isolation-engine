-- docker/init-scripts/master-init.sql
-- Runs once when the mysql-master container is first created.
-- Flyway migrations handle schema; this seeds dev data only.

-- Nothing to seed automatically — tenants are registered via the admin API.
-- Flyway will create the tables on first application startup.

-- Grant tenant_user permissions on all databases (needed for runtime provisioning)
-- In production, use more restrictive per-database grants.
GRANT ALL PRIVILEGES ON `tenant_%`.* TO '${TENANT_DB_USER}'@'%';
FLUSH PRIVILEGES;
