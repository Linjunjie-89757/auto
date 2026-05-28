UPDATE tb_ai_case_config
SET protocol_type = 'OPENAI_COMPATIBLE_CHAT'
WHERE protocol_type IS NULL
   OR protocol_type = 'OPENAI_CHAT_COMPLETIONS';

UPDATE tb_ai_case_config
SET protocol_type = 'OPENAI_COMPATIBLE_RESPONSES'
WHERE protocol_type = 'OPENAI_RESPONSES';

CREATE TABLE IF NOT EXISTS tb_ai_provider_connection (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    workspace_id BIGINT NOT NULL,
    connection_name VARCHAR(255) NOT NULL,
    protocol_type VARCHAR(64) NOT NULL,
    base_url VARCHAR(1000) NOT NULL,
    api_key_cipher_text TEXT,
    status TINYINT NOT NULL DEFAULT 1,
    last_verified_at TIMESTAMP NULL,
    last_fetch_models_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS tb_ai_provider_model_cache (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    connection_id BIGINT NOT NULL,
    model_name VARCHAR(255) NOT NULL,
    display_name VARCHAR(255) NOT NULL,
    raw_metadata_json CLOB,
    detected_capabilities_json CLOB,
    selectable TINYINT NOT NULL DEFAULT 1,
    last_probed_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE tb_ai_case_config
    ADD COLUMN IF NOT EXISTS provider_connection_id BIGINT;

ALTER TABLE tb_ai_case_config
    ADD COLUMN IF NOT EXISTS capability_override_json CLOB;

INSERT INTO tb_ai_provider_connection (
    workspace_id,
    connection_name,
    protocol_type,
    base_url,
    api_key_cipher_text,
    status,
    created_at,
    updated_at
)
SELECT DISTINCT
    0,
    CONCAT('迁移连接-', cfg.protocol_type),
    cfg.protocol_type,
    cfg.base_url,
    cfg.api_key_cipher_text,
    COALESCE(cfg.status, 1),
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM tb_ai_case_config cfg
WHERE cfg.base_url IS NOT NULL
  AND TRIM(cfg.base_url) <> ''
  AND NOT EXISTS (
    SELECT 1
    FROM tb_ai_provider_connection conn
    WHERE conn.workspace_id = 0
      AND conn.protocol_type = cfg.protocol_type
      AND conn.base_url = cfg.base_url
      AND (
        conn.api_key_cipher_text = cfg.api_key_cipher_text
        OR (conn.api_key_cipher_text IS NULL AND cfg.api_key_cipher_text IS NULL)
      )
);

UPDATE tb_ai_case_config cfg
SET provider_connection_id = (
    SELECT conn.id
    FROM tb_ai_provider_connection conn
    WHERE conn.workspace_id = 0
      AND conn.protocol_type = cfg.protocol_type
      AND conn.base_url = cfg.base_url
      AND (
        conn.api_key_cipher_text = cfg.api_key_cipher_text
        OR (conn.api_key_cipher_text IS NULL AND cfg.api_key_cipher_text IS NULL)
      )
    LIMIT 1
)
WHERE cfg.provider_connection_id IS NULL
  AND cfg.base_url IS NOT NULL
  AND TRIM(cfg.base_url) <> '';

UPDATE tb_ai_case_config
SET capability_override_json = CASE
    WHEN capability_override_json IS NULL AND supports_image_input = 1 THEN '{"imageInput":true}'
    WHEN capability_override_json IS NULL AND supports_image_input = 0 THEN '{"imageInput":false}'
    ELSE capability_override_json
END;

CREATE INDEX IF NOT EXISTS idx_ai_provider_connection_workspace
    ON tb_ai_provider_connection (workspace_id);

CREATE INDEX IF NOT EXISTS idx_ai_case_config_provider_connection
    ON tb_ai_case_config (provider_connection_id);

CREATE UNIQUE INDEX IF NOT EXISTS uk_ai_provider_model_connection_name
    ON tb_ai_provider_model_cache (connection_id, model_name);
