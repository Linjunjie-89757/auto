CREATE TABLE IF NOT EXISTS tb_web_ui_element_page (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    workspace_id BIGINT NOT NULL,
    page_name VARCHAR(128) NOT NULL,
    page_path VARCHAR(512) NULL,
    description TEXT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL DEFAULT 'ENABLED',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_web_ui_element_page_workspace ON tb_web_ui_element_page(workspace_id);
CREATE INDEX idx_web_ui_element_page_status ON tb_web_ui_element_page(workspace_id, status);
CREATE INDEX idx_web_ui_element_page_sort ON tb_web_ui_element_page(workspace_id, sort_order);

CREATE TABLE IF NOT EXISTS tb_web_ui_element_group (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    workspace_id BIGINT NOT NULL,
    page_id BIGINT NOT NULL,
    group_name VARCHAR(128) NOT NULL,
    description TEXT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL DEFAULT 'ENABLED',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_web_ui_element_group_workspace ON tb_web_ui_element_group(workspace_id);
CREATE INDEX idx_web_ui_element_group_page ON tb_web_ui_element_group(page_id);
CREATE INDEX idx_web_ui_element_group_status ON tb_web_ui_element_group(workspace_id, status);
CREATE INDEX idx_web_ui_element_group_sort ON tb_web_ui_element_group(page_id, sort_order);

ALTER TABLE tb_web_ui_element ADD COLUMN page_id BIGINT NULL;
ALTER TABLE tb_web_ui_element ADD COLUMN group_id BIGINT NULL;
CREATE INDEX idx_web_ui_element_page_id ON tb_web_ui_element(page_id);
CREATE INDEX idx_web_ui_element_group_id ON tb_web_ui_element(group_id);
