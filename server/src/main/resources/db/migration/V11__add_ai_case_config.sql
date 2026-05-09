CREATE TABLE IF NOT EXISTS tb_ai_case_config (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    workspace_id BIGINT NOT NULL,
    provider VARCHAR(64) NOT NULL,
    model VARCHAR(128) NOT NULL,
    base_url VARCHAR(512) NOT NULL,
    api_key_cipher_text TEXT,
    prompt_template TEXT NOT NULL,
    review_checklist TEXT,
    temperature DOUBLE NOT NULL DEFAULT 0.3,
    max_cases INT NOT NULL DEFAULT 20,
    status INT NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_ai_case_config_workspace
    ON tb_ai_case_config (workspace_id);
