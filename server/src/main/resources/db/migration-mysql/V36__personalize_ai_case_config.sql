SET @add_ai_provider_connection_owner_user_id = (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = DATABASE()
              AND table_name = 'tb_ai_provider_connection'
              AND column_name = 'owner_user_id'
        ),
        'SELECT 1',
        'ALTER TABLE tb_ai_provider_connection ADD COLUMN owner_user_id BIGINT NULL'
    )
);
PREPARE stmt FROM @add_ai_provider_connection_owner_user_id;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_ai_case_config_owner_user_id = (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = DATABASE()
              AND table_name = 'tb_ai_case_config'
              AND column_name = 'owner_user_id'
        ),
        'SELECT 1',
        'ALTER TABLE tb_ai_case_config ADD COLUMN owner_user_id BIGINT NULL'
    )
);
PREPARE stmt FROM @add_ai_case_config_owner_user_id;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @drop_legacy_ai_case_config_unique = (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM information_schema.statistics
            WHERE table_schema = DATABASE()
              AND table_name = 'tb_ai_case_config'
              AND index_name = 'uk_ai_case_config_workspace_role'
        ),
        'DROP INDEX uk_ai_case_config_workspace_role ON tb_ai_case_config',
        'SELECT 1'
    )
);
PREPARE stmt FROM @drop_legacy_ai_case_config_unique;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @create_ai_case_config_owner_role = (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM information_schema.statistics
            WHERE table_schema = DATABASE()
              AND table_name = 'tb_ai_case_config'
              AND index_name = 'uk_ai_case_config_owner_role'
        ),
        'SELECT 1',
        'CREATE UNIQUE INDEX uk_ai_case_config_owner_role ON tb_ai_case_config (owner_user_id, role_type)'
    )
);
PREPARE stmt FROM @create_ai_case_config_owner_role;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @create_ai_provider_connection_owner = (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM information_schema.statistics
            WHERE table_schema = DATABASE()
              AND table_name = 'tb_ai_provider_connection'
              AND index_name = 'idx_ai_provider_connection_owner'
        ),
        'SELECT 1',
        'CREATE INDEX idx_ai_provider_connection_owner ON tb_ai_provider_connection (owner_user_id)'
    )
);
PREPARE stmt FROM @create_ai_provider_connection_owner;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @create_ai_case_config_owner = (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM information_schema.statistics
            WHERE table_schema = DATABASE()
              AND table_name = 'tb_ai_case_config'
              AND index_name = 'idx_ai_case_config_owner'
        ),
        'SELECT 1',
        'CREATE INDEX idx_ai_case_config_owner ON tb_ai_case_config (owner_user_id)'
    )
);
PREPARE stmt FROM @create_ai_case_config_owner;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
