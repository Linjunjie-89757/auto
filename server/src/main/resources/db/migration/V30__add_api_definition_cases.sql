CREATE TABLE IF NOT EXISTS tb_api_definition_case (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    workspace_id BIGINT NOT NULL,
    definition_id BIGINT NOT NULL,
    case_name VARCHAR(255) NOT NULL,
    description TEXT,
    tags_json TEXT,
    request_json LONGTEXT,
    assertions_json LONGTEXT,
    preprocessors_json LONGTEXT,
    postprocessors_json LONGTEXT,
    last_run_result VARCHAR(32),
    last_run_at TIMESTAMP NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
