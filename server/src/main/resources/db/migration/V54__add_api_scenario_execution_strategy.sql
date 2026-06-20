ALTER TABLE tb_api_scenario ADD COLUMN IF NOT EXISTS global_timeout_ms INT NOT NULL DEFAULT 300000;
ALTER TABLE tb_api_scenario ADD COLUMN IF NOT EXISTS step_failure_retry_count INT NOT NULL DEFAULT 0;
ALTER TABLE tb_api_scenario ADD COLUMN IF NOT EXISTS default_step_wait_ms INT NOT NULL DEFAULT 0;
