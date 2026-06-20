CREATE TABLE IF NOT EXISTS tb_web_ui_report_share (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    workspace_id BIGINT NOT NULL,
    share_type VARCHAR(32) NOT NULL,
    target_id BIGINT NOT NULL,
    token_hash VARCHAR(128) NOT NULL,
    status INT NOT NULL DEFAULT 1,
    expires_at TIMESTAMP,
    created_by VARCHAR(128),
    last_accessed_at TIMESTAMP,
    access_count INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_web_ui_report_share_token ON tb_web_ui_report_share(token_hash);
CREATE INDEX IF NOT EXISTS idx_web_ui_report_share_target ON tb_web_ui_report_share(workspace_id, share_type, target_id);
CREATE INDEX IF NOT EXISTS idx_web_ui_report_share_status ON tb_web_ui_report_share(status);
