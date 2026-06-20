ALTER TABLE tb_api_scenario
    ADD COLUMN global_timeout_ms INT NOT NULL DEFAULT 300000,
    ADD COLUMN step_failure_retry_count INT NOT NULL DEFAULT 0,
    ADD COLUMN default_step_wait_ms INT NOT NULL DEFAULT 0;
