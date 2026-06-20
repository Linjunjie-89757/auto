CREATE TABLE IF NOT EXISTS tb_web_ui_case (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    workspace_id BIGINT NOT NULL,
    module_name VARCHAR(128),
    case_name VARCHAR(255) NOT NULL,
    description TEXT,
    base_url VARCHAR(512),
    browser_type VARCHAR(32) NOT NULL DEFAULT 'CHROMIUM',
    headless BOOLEAN NOT NULL DEFAULT TRUE,
    default_timeout_ms INT NOT NULL DEFAULT 10000,
    status VARCHAR(32) NOT NULL DEFAULT 'ENABLED',
    last_run_result VARCHAR(32),
    last_run_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_web_ui_case_workspace ON tb_web_ui_case(workspace_id);
CREATE INDEX IF NOT EXISTS idx_web_ui_case_module ON tb_web_ui_case(module_name);
CREATE INDEX IF NOT EXISTS idx_web_ui_case_status ON tb_web_ui_case(status);

CREATE TABLE IF NOT EXISTS tb_web_ui_case_step (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    case_id BIGINT NOT NULL,
    step_name VARCHAR(255) NOT NULL,
    step_type VARCHAR(32) NOT NULL,
    locator_type VARCHAR(64),
    locator_value VARCHAR(1024),
    input_value TEXT,
    timeout_ms INT NOT NULL DEFAULT 5000,
    continue_on_failure BOOLEAN NOT NULL DEFAULT FALSE,
    screenshot_policy VARCHAR(32) NOT NULL DEFAULT 'ON_FAILURE',
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_web_ui_case_step_case ON tb_web_ui_case_step(case_id);
CREATE INDEX IF NOT EXISTS idx_web_ui_case_step_sort ON tb_web_ui_case_step(case_id, sort_order);

CREATE TABLE IF NOT EXISTS tb_web_ui_environment (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    workspace_id BIGINT NOT NULL,
    environment_name VARCHAR(128) NOT NULL,
    base_url VARCHAR(512) NOT NULL,
    browser_type VARCHAR(32) NOT NULL DEFAULT 'CHROMIUM',
    headless BOOLEAN NOT NULL DEFAULT TRUE,
    default_timeout_ms INT NOT NULL DEFAULT 10000,
    status INT NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_web_ui_environment_workspace ON tb_web_ui_environment(workspace_id);
CREATE INDEX IF NOT EXISTS idx_web_ui_environment_status ON tb_web_ui_environment(status);
