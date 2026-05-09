ALTER TABLE tb_ai_case_config
    ADD COLUMN IF NOT EXISTS supports_image_input TINYINT DEFAULT 0;

UPDATE tb_ai_case_config
SET supports_image_input = 0
WHERE supports_image_input IS NULL;
