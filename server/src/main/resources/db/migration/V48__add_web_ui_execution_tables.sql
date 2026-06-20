CREATE TABLE IF NOT EXISTS tb_web_ui_run (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    workspace_id BIGINT NOT NULL,
    case_id BIGINT,
    case_name VARCHAR(255) NOT NULL,
    environment_id BIGINT,
    environment_name VARCHAR(255),
    status VARCHAR(32) NOT NULL,
    browser_type VARCHAR(32) NOT NULL DEFAULT 'CHROMIUM',
    headless BOOLEAN NOT NULL DEFAULT TRUE,
    base_url VARCHAR(512),
    total_steps INT NOT NULL DEFAULT 0,
    passed_steps INT NOT NULL DEFAULT 0,
    failed_steps INT NOT NULL DEFAULT 0,
    skipped_steps INT NOT NULL DEFAULT 0,
    duration_ms BIGINT,
    failure_summary VARCHAR(2048),
    operator_name VARCHAR(128),
    started_at TIMESTAMP,
    finished_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_web_ui_run_workspace ON tb_web_ui_run(workspace_id);
CREATE INDEX IF NOT EXISTS idx_web_ui_run_case ON tb_web_ui_run(case_id);
CREATE INDEX IF NOT EXISTS idx_web_ui_run_status ON tb_web_ui_run(status);

CREATE TABLE IF NOT EXISTS tb_web_ui_run_step (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    run_id BIGINT NOT NULL,
    case_step_id BIGINT,
    step_name VARCHAR(255) NOT NULL,
    step_type VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    locator_type VARCHAR(64),
    locator_value VARCHAR(2048),
    input_value_snapshot TEXT,
    duration_ms BIGINT,
    error_message TEXT,
    screenshot_artifact_id BIGINT,
    sort_order INT NOT NULL DEFAULT 0,
    started_at TIMESTAMP,
    finished_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_web_ui_run_step_run ON tb_web_ui_run_step(run_id);
CREATE INDEX IF NOT EXISTS idx_web_ui_run_step_sort ON tb_web_ui_run_step(run_id, sort_order);

CREATE TABLE IF NOT EXISTS tb_web_ui_run_artifact (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    workspace_id BIGINT NOT NULL,
    run_id BIGINT NOT NULL,
    step_id BIGINT,
    artifact_type VARCHAR(64) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(128) NOT NULL,
    file_size BIGINT NOT NULL DEFAULT 0,
    storage_path VARCHAR(1024) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_web_ui_run_artifact_workspace ON tb_web_ui_run_artifact(workspace_id);
CREATE INDEX IF NOT EXISTS idx_web_ui_run_artifact_run ON tb_web_ui_run_artifact(run_id);
