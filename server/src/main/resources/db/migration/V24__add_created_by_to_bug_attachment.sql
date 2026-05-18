ALTER TABLE tb_bug_attachment
    ADD COLUMN IF NOT EXISTS created_by BIGINT;
