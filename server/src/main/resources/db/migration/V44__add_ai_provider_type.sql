ALTER TABLE tb_ai_provider_connection
    ADD COLUMN IF NOT EXISTS provider_type VARCHAR(64);

UPDATE tb_ai_provider_connection
SET provider_type = 'custom'
WHERE provider_type IS NULL
   OR TRIM(provider_type) = '';
