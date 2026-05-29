SET @add_ai_provider_request_timeout_seconds = (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = DATABASE()
              AND table_name = 'tb_ai_provider_connection'
              AND column_name = 'request_timeout_seconds'
        ),
        'SELECT 1',
        'ALTER TABLE tb_ai_provider_connection ADD COLUMN request_timeout_seconds INT NULL'
    )
);
PREPARE stmt FROM @add_ai_provider_request_timeout_seconds;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;
