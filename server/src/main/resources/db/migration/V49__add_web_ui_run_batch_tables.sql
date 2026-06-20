CREATE TABLE IF NOT EXISTS tb_web_ui_run_batch (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    workspace_id BIGINT NOT NULL,
    batch_name VARCHAR(255) NOT NULL,
    source VARCHAR(32) NOT NULL DEFAULT 'MANUAL',
    environment_id BIGINT,
    environment_name VARCHAR(255),
    status VARCHAR(32) NOT NULL,
    total_cases INT NOT NULL DEFAULT 0,
    success_cases INT NOT NULL DEFAULT 0,
    failed_cases INT NOT NULL DEFAULT 0,
    duration_ms BIGINT,
    failure_summary VARCHAR(2048),
    operator_name VARCHAR(128),
    ci_token_id BIGINT,
    external_build_id VARCHAR(255),
    started_at TIMESTAMP,
    finished_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_web_ui_run_batch_workspace ON tb_web_ui_run_batch(workspace_id);
CREATE INDEX IF NOT EXISTS idx_web_ui_run_batch_status ON tb_web_ui_run_batch(status);
CREATE INDEX IF NOT EXISTS idx_web_ui_run_batch_source ON tb_web_ui_run_batch(source);

ALTER TABLE tb_web_ui_run ADD COLUMN IF NOT EXISTS batch_id BIGINT;
ALTER TABLE tb_web_ui_run ADD COLUMN IF NOT EXISTS batch_sort_order INT;

CREATE INDEX IF NOT EXISTS idx_web_ui_run_batch ON tb_web_ui_run(batch_id);
