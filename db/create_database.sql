-- =============================================================================
-- MeshLink – create_database.sql
-- =============================================================================
-- Purpose  : Create the per-user local MySQL database and application user.
-- Usage    : Run this script ONCE as a MySQL root / admin user before running
--            schema.sql.
--
--   mysql -u root -p < create_database.sql
--
-- Architecture note:
--   Every device (Alice's laptop, Bob's laptop, …) runs its OWN independent
--   instance of MySQL. There is NO shared / central / cloud database.
--   This script must be executed on EACH device separately.
-- =============================================================================

-- Drop and recreate the database (safe for development; remove DROP in prod).
DROP DATABASE IF EXISTS meshlink;
CREATE DATABASE meshlink
    CHARACTER SET  utf8mb4        -- full Unicode support (emoji, Devanagari, …)
    COLLATE        utf8mb4_unicode_ci;

-- Application-level user (local-only access).
-- In production, replace 'changeme' with a strong random password.
-- Only allow connections from localhost because this is a local-only DB.
DROP USER IF EXISTS 'meshlink_app'@'localhost';
CREATE USER 'meshlink_app'@'localhost'
    IDENTIFIED BY 'changeme';

-- Grant schema and data privileges on meshlink database for local execution
GRANT ALL PRIVILEGES
    ON meshlink.*
    TO 'meshlink_app'@'localhost';

FLUSH PRIVILEGES;

SELECT 'meshlink database created successfully.' AS status;
