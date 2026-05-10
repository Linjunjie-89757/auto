ALTER TABLE tb_ai_case_config
    ADD COLUMN role_type VARCHAR(32) NULL AFTER workspace_id;

UPDATE tb_ai_case_config
SET role_type = 'CASE_GENERATOR'
WHERE role_type IS NULL OR TRIM(role_type) = '';

ALTER TABLE tb_ai_case_config
    MODIFY COLUMN role_type VARCHAR(32) NOT NULL DEFAULT 'CASE_GENERATOR';

DROP INDEX uk_ai_case_config_workspace ON tb_ai_case_config;

CREATE UNIQUE INDEX uk_ai_case_config_workspace_role
    ON tb_ai_case_config (workspace_id, role_type);
