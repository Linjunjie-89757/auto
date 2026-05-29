ALTER TABLE tb_ai_provider_connection
    ADD COLUMN IF NOT EXISTS request_timeout_seconds INT;
