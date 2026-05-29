ALTER TABLE tb_ai_provider_connection
    ADD COLUMN IF NOT EXISTS selected_model_name VARCHAR(255);

UPDATE tb_ai_provider_connection conn
SET selected_model_name = (
    SELECT cache.model_name
    FROM tb_ai_provider_model_cache cache
    WHERE cache.connection_id = conn.id
    ORDER BY cache.last_probed_at DESC NULLS LAST, cache.model_name ASC
    LIMIT 1
)
WHERE selected_model_name IS NULL
  AND EXISTS (
      SELECT 1
      FROM tb_ai_provider_model_cache cache
      WHERE cache.connection_id = conn.id
  );
