ALTER TABLE tb_ai_case_config
    ADD COLUMN top_p DOUBLE NOT NULL DEFAULT 0.9;

UPDATE tb_ai_case_config
SET top_p = 0.7
WHERE role_type = 'CASE_REVIEWER';

UPDATE tb_ai_case_config
SET top_p = 0.9
WHERE role_type = 'CASE_GENERATOR';
