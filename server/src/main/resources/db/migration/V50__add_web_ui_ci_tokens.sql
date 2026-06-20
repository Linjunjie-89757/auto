CREATE TABLE IF NOT EXISTS tb_web_ui_ci_token (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    workspace_id BIGINT NOT NULL,
    token_name VARCHAR(128) NOT NULL,
    token_hash VARCHAR(128) NOT NULL,
    status INT NOT NULL DEFAULT 1,
    created_by VARCHAR(128),
    last_used_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_web_ui_ci_token_hash ON tb_web_ui_ci_token(token_hash);
CREATE INDEX IF NOT EXISTS idx_web_ui_ci_token_workspace ON tb_web_ui_ci_token(workspace_id);
CREATE INDEX IF NOT EXISTS idx_web_ui_ci_token_status ON tb_web_ui_ci_token(status);
