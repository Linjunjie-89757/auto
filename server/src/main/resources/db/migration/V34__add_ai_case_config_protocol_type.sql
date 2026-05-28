ALTER TABLE tb_ai_case_config
    ADD COLUMN IF NOT EXISTS protocol_type VARCHAR(64);
