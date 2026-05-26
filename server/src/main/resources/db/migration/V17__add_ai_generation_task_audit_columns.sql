ALTER TABLE tb_ai_generation_task
    ADD COLUMN created_by BIGINT;

ALTER TABLE tb_ai_generation_task
    ADD COLUMN updated_by BIGINT;
