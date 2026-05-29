SET @add_ai_provider_selected_model_name = (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = DATABASE()
              AND table_name = 'tb_ai_provider_connection'
              AND column_name = 'selected_model_name'
        ),
        'SELECT 1',
        'ALTER TABLE tb_ai_provider_connection ADD COLUMN selected_model_name VARCHAR(255) NULL'
    )
);
PREPARE stmt FROM @add_ai_provider_selected_model_name;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

UPDATE tb_ai_provider_connection conn
LEFT JOIN (
    SELECT ranked.connection_id, ranked.model_name
    FROM (
        SELECT
            cache.connection_id,
            cache.model_name,
            ROW_NUMBER() OVER (
                PARTITION BY cache.connection_id
                ORDER BY cache.last_probed_at DESC, cache.model_name ASC
            ) AS row_num
        FROM tb_ai_provider_model_cache cache
    ) ranked
    WHERE ranked.row_num = 1
) preferred ON preferred.connection_id = conn.id
SET conn.selected_model_name = preferred.model_name
WHERE conn.selected_model_name IS NULL
  AND preferred.model_name IS NOT NULL;
