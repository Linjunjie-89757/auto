CREATE TABLE IF NOT EXISTS tb_web_ui_case_template (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    workspace_id BIGINT NOT NULL,
    module_name VARCHAR(128) NULL,
    template_name VARCHAR(255) NOT NULL,
    description TEXT NULL,
    base_url VARCHAR(512) NULL,
    browser_type VARCHAR(32) NOT NULL DEFAULT 'CHROMIUM',
    headless BOOLEAN NOT NULL DEFAULT TRUE,
    default_timeout_ms INT NOT NULL DEFAULT 10000,
    status VARCHAR(32) NOT NULL DEFAULT 'ENABLED',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_web_ui_template_workspace ON tb_web_ui_case_template(workspace_id);
CREATE INDEX idx_web_ui_template_module ON tb_web_ui_case_template(module_name);
CREATE INDEX idx_web_ui_template_status ON tb_web_ui_case_template(status);

CREATE TABLE IF NOT EXISTS tb_web_ui_case_template_step (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    template_id BIGINT NOT NULL,
    step_name VARCHAR(255) NOT NULL,
    step_type VARCHAR(32) NOT NULL,
    locator_type VARCHAR(64) NULL,
    locator_value VARCHAR(1024) NULL,
    input_value TEXT NULL,
    timeout_ms INT NOT NULL DEFAULT 5000,
    continue_on_failure BOOLEAN NOT NULL DEFAULT FALSE,
    screenshot_policy VARCHAR(32) NOT NULL DEFAULT 'ON_FAILURE',
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_web_ui_template_step_template ON tb_web_ui_case_template_step(template_id);
CREATE INDEX idx_web_ui_template_step_sort ON tb_web_ui_case_template_step(template_id, sort_order);
