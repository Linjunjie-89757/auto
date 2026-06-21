CREATE TABLE IF NOT EXISTS tb_web_ui_element (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    workspace_id BIGINT NOT NULL,
    page_name VARCHAR(128) NOT NULL,
    group_name VARCHAR(128),
    element_name VARCHAR(255) NOT NULL,
    locator_type VARCHAR(64) NOT NULL,
    locator_value VARCHAR(1024) NOT NULL,
    description TEXT,
    status VARCHAR(32) NOT NULL DEFAULT 'ENABLED',
    last_validate_result VARCHAR(32),
    last_validate_at TIMESTAMP,
    last_validate_message TEXT,
    last_match_count INT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX IF NOT EXISTS idx_web_ui_element_workspace ON tb_web_ui_element(workspace_id);
CREATE INDEX IF NOT EXISTS idx_web_ui_element_page ON tb_web_ui_element(workspace_id, page_name);
CREATE INDEX IF NOT EXISTS idx_web_ui_element_group ON tb_web_ui_element(workspace_id, group_name);
CREATE INDEX IF NOT EXISTS idx_web_ui_element_status ON tb_web_ui_element(workspace_id, status);

ALTER TABLE tb_web_ui_case_step ADD COLUMN IF NOT EXISTS element_id BIGINT;
CREATE INDEX IF NOT EXISTS idx_web_ui_case_step_element ON tb_web_ui_case_step(element_id);

ALTER TABLE tb_web_ui_case_template_step ADD COLUMN IF NOT EXISTS element_id BIGINT;
CREATE INDEX IF NOT EXISTS idx_web_ui_template_step_element ON tb_web_ui_case_template_step(element_id);
