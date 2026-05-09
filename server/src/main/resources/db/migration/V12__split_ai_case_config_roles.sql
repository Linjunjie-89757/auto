ALTER TABLE tb_ai_case_config ADD COLUMN IF NOT EXISTS role_type VARCHAR(32);

UPDATE tb_ai_case_config
SET role_type = 'CASE_GENERATOR'
WHERE role_type IS NULL OR TRIM(role_type) = '';

ALTER TABLE tb_ai_case_config ALTER COLUMN role_type SET DEFAULT 'CASE_GENERATOR';
ALTER TABLE tb_ai_case_config ALTER COLUMN role_type SET NOT NULL;

DROP INDEX IF EXISTS uk_ai_case_config_workspace;

CREATE UNIQUE INDEX IF NOT EXISTS uk_ai_case_config_workspace_role
    ON tb_ai_case_config (workspace_id, role_type);
