CREATE TABLE IF NOT EXISTS tb_web_ui_run (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    workspace_id BIGINT NOT NULL,
    case_id BIGINT NULL,
    case_name VARCHAR(255) NOT NULL,
    environment_id BIGINT NULL,
    environment_name VARCHAR(255) NULL,
    status VARCHAR(32) NOT NULL,
    browser_type VARCHAR(32) NOT NULL DEFAULT 'CHROMIUM',
    headless BOOLEAN NOT NULL DEFAULT TRUE,
    base_url VARCHAR(512) NULL,
    total_steps INT NOT NULL DEFAULT 0,
    passed_steps INT NOT NULL DEFAULT 0,
    failed_steps INT NOT NULL DEFAULT 0,
    skipped_steps INT NOT NULL DEFAULT 0,
    duration_ms BIGINT NULL,
    failure_summary VARCHAR(2048) NULL,
    operator_name VARCHAR(128) NULL,
    started_at TIMESTAMP NULL,
    finished_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_web_ui_run_workspace ON tb_web_ui_run(workspace_id);
CREATE INDEX idx_web_ui_run_case ON tb_web_ui_run(case_id);
CREATE INDEX idx_web_ui_run_status ON tb_web_ui_run(status);

CREATE TABLE IF NOT EXISTS tb_web_ui_run_step (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    run_id BIGINT NOT NULL,
    case_step_id BIGINT NULL,
    step_name VARCHAR(255) NOT NULL,
    step_type VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    locator_type VARCHAR(64) NULL,
    locator_value VARCHAR(2048) NULL,
    input_value_snapshot TEXT NULL,
    duration_ms BIGINT NULL,
    error_message TEXT NULL,
    screenshot_artifact_id BIGINT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    started_at TIMESTAMP NULL,
    finished_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_web_ui_run_step_run ON tb_web_ui_run_step(run_id);
CREATE INDEX idx_web_ui_run_step_sort ON tb_web_ui_run_step(run_id, sort_order);

CREATE TABLE IF NOT EXISTS tb_web_ui_run_artifact (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    workspace_id BIGINT NOT NULL,
    run_id BIGINT NOT NULL,
    step_id BIGINT NULL,
    artifact_type VARCHAR(64) NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(128) NOT NULL,
    file_size BIGINT NOT NULL DEFAULT 0,
    storage_path VARCHAR(1024) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_web_ui_run_artifact_workspace ON tb_web_ui_run_artifact(workspace_id);
CREATE INDEX idx_web_ui_run_artifact_run ON tb_web_ui_run_artifact(run_id);
