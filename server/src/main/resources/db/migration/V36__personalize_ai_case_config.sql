ALTER TABLE tb_ai_provider_connection
    ADD COLUMN IF NOT EXISTS owner_user_id BIGINT;

ALTER TABLE tb_ai_case_config
    ADD COLUMN IF NOT EXISTS owner_user_id BIGINT;

DROP INDEX IF EXISTS uk_ai_case_config_workspace_role;

CREATE UNIQUE INDEX IF NOT EXISTS uk_ai_case_config_owner_role
    ON tb_ai_case_config (owner_user_id, role_type);

CREATE INDEX IF NOT EXISTS idx_ai_provider_connection_owner
    ON tb_ai_provider_connection (owner_user_id);

CREATE INDEX IF NOT EXISTS idx_ai_case_config_owner
    ON tb_ai_case_config (owner_user_id);
