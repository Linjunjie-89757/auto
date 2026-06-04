ALTER TABLE tb_ai_case_config
    MODIFY COLUMN max_cases INT NOT NULL DEFAULT 50;

UPDATE tb_ai_case_config
SET max_cases = 50
WHERE max_cases = 20;
