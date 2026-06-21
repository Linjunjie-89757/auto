CREATE TABLE IF NOT EXISTS tb_api_execution_suite_module (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    workspace_id BIGINT NOT NULL,
    parent_id BIGINT,
    module_name VARCHAR(255) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS tb_api_execution_suite (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    workspace_id BIGINT NOT NULL,
    module_id BIGINT,
    suite_name VARCHAR(255) NOT NULL,
    priority VARCHAR(16) NOT NULL DEFAULT 'P1',
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
    description TEXT,
    environment_id BIGINT,
    variable_set_id BIGINT,
    run_mode VARCHAR(32) NOT NULL DEFAULT 'SERIAL',
    run_on VARCHAR(32) NOT NULL DEFAULT 'LOCAL',
    notify_enabled TINYINT(1) NOT NULL DEFAULT 1,
    continue_on_failure TINYINT(1) NOT NULL DEFAULT 0,
    global_timeout_ms INT NOT NULL DEFAULT 300000,
    step_failure_retry_count INT NOT NULL DEFAULT 0,
    default_step_wait_ms INT NOT NULL DEFAULT 0,
    schedule_enabled TINYINT(1) NOT NULL DEFAULT 0,
    cron_expression VARCHAR(255),
    branch_name VARCHAR(255),
    trigger_source VARCHAR(255),
    branch_note TEXT,
    last_run_result VARCHAR(32),
    last_run_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS tb_api_execution_suite_item (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    workspace_id BIGINT NOT NULL,
    suite_id BIGINT NOT NULL,
    item_type VARCHAR(32) NOT NULL,
    item_id BIGINT NOT NULL,
    item_name_snapshot VARCHAR(255) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    enabled TINYINT(1) NOT NULL DEFAULT 1,
    description TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS tb_api_execution_suite_run_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    workspace_id BIGINT NOT NULL,
    suite_id BIGINT NOT NULL,
    suite_name VARCHAR(255) NOT NULL,
    report_id BIGINT,
    result VARCHAR(32) NOT NULL,
    failure_summary TEXT,
    total_count INT NOT NULL DEFAULT 0,
    success_count INT NOT NULL DEFAULT 0,
    failed_count INT NOT NULL DEFAULT 0,
    skipped_count INT NOT NULL DEFAULT 0,
    duration_ms BIGINT NOT NULL DEFAULT 0,
    environment_id BIGINT,
    variable_set_id BIGINT,
    operator_id BIGINT,
    operator_name VARCHAR(255),
    detail_json LONGTEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
