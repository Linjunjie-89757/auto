CREATE TABLE IF NOT EXISTS tb_case_execution_history (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    workspace_id BIGINT NOT NULL,
    case_id BIGINT NOT NULL,
    execution_status VARCHAR(32) NOT NULL,
    execution_comment TEXT,
    execution_note TEXT,
    executor_id BIGINT,
    executor_name VARCHAR(255),
    executed_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_case_execution_history_case_id ON tb_case_execution_history(case_id);
CREATE INDEX IF NOT EXISTS idx_case_execution_history_executed_at ON tb_case_execution_history(executed_at);
