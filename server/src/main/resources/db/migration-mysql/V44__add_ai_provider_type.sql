SET @add_ai_provider_connection_provider_type = (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = DATABASE()
              AND table_name = 'tb_ai_provider_connection'
              AND column_name = 'provider_type'
        ),
        'SELECT 1',
        'ALTER TABLE tb_ai_provider_connection ADD COLUMN provider_type VARCHAR(64) NULL'
    )
);
PREPARE stmt FROM @add_ai_provider_connection_provider_type;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE tb_ai_provider_connection
SET provider_type = 'custom'
WHERE provider_type IS NULL
   OR TRIM(provider_type) = '';
