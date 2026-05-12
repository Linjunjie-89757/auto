ALTER TABLE tb_ai_generation_task
    ADD COLUMN IF NOT EXISTS created_by BIGINT;

ALTER TABLE tb_ai_generation_task
    ADD COLUMN IF NOT EXISTS updated_by BIGINT;
