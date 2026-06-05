SET @add_workspace_type = (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = DATABASE()
              AND table_name = 'tb_sys_workspace'
              AND column_name = 'workspace_type'
        ),
        'SELECT 1',
        'ALTER TABLE tb_sys_workspace ADD COLUMN workspace_type VARCHAR(32) NOT NULL DEFAULT ''PROJECT'''
    )
);
PREPARE stmt FROM @add_workspace_type;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_owner_user_id = (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = DATABASE()
              AND table_name = 'tb_sys_workspace'
              AND column_name = 'owner_user_id'
        ),
        'SELECT 1',
        'ALTER TABLE tb_sys_workspace ADD COLUMN owner_user_id BIGINT NULL'
    )
);
PREPARE stmt FROM @add_owner_user_id;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
