CREATE TABLE IF NOT EXISTS tb_api_definition_case_change_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    workspace_id BIGINT NOT NULL,
    definition_id BIGINT NOT NULL,
    case_id BIGINT NOT NULL,
    case_name VARCHAR(255) NOT NULL,
    change_type VARCHAR(32) NOT NULL,
    change_summary TEXT,
    operator_id BIGINT,
    operator_name VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
