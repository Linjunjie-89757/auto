CREATE TABLE IF NOT EXISTS tb_api_definition (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    workspace_id BIGINT NOT NULL,
    definition_name VARCHAR(255) NOT NULL,
    http_method VARCHAR(16) NOT NULL,
    path VARCHAR(500) NOT NULL,
    directory_name VARCHAR(255),
    description TEXT,
    tags_json TEXT,
    request_json LONGTEXT,
    assertions_json LONGTEXT,
    extractors_json LONGTEXT,
    last_run_result VARCHAR(32),
    last_run_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS tb_api_scenario (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    workspace_id BIGINT NOT NULL,
    scenario_name VARCHAR(255) NOT NULL,
    directory_name VARCHAR(255),
    description TEXT,
    tags_json TEXT,
    steps_json LONGTEXT,
    default_env_id BIGINT,
    variable_set_id BIGINT,
    continue_on_failure TINYINT(1) NOT NULL DEFAULT 0,
    related_case_id BIGINT,
    last_run_result VARCHAR(32),
    last_run_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS tb_api_run_step_result (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    workspace_id BIGINT NOT NULL,
    report_id BIGINT NOT NULL,
    step_order INT NOT NULL,
    step_name VARCHAR(255) NOT NULL,
    definition_id BIGINT,
    success TINYINT(1) NOT NULL DEFAULT 0,
    duration_ms BIGINT NOT NULL DEFAULT 0,
    request_snapshot_json LONGTEXT,
    response_snapshot_json LONGTEXT,
    assertion_results_json LONGTEXT,
    extraction_results_json LONGTEXT,
    error_message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
