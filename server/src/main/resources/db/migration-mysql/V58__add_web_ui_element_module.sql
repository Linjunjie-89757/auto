CREATE TABLE IF NOT EXISTS tb_web_ui_element_module (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    workspace_id BIGINT NOT NULL,
    module_name VARCHAR(128) NOT NULL,
    description TEXT,
    sort_order INT NOT NULL DEFAULT 0,
    status VARCHAR(32) NOT NULL DEFAULT 'ENABLED',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_web_ui_element_module_workspace ON tb_web_ui_element_module(workspace_id);
CREATE INDEX idx_web_ui_element_module_status ON tb_web_ui_element_module(workspace_id, status);
CREATE INDEX idx_web_ui_element_module_sort ON tb_web_ui_element_module(workspace_id, sort_order);

ALTER TABLE tb_web_ui_element_page ADD COLUMN module_id BIGINT NULL;
CREATE INDEX idx_web_ui_element_page_module_id ON tb_web_ui_element_page(module_id);

INSERT INTO tb_web_ui_element_module (
    workspace_id,
    module_name,
    description,
    sort_order,
    status,
    created_at,
    updated_at
)
SELECT page_scope.workspace_id,
       '默认模块',
       '系统自动创建，用于承载历史页面对象',
       0,
       'ENABLED',
       CURRENT_TIMESTAMP,
       CURRENT_TIMESTAMP
FROM (
    SELECT DISTINCT workspace_id
    FROM tb_web_ui_element_page
    WHERE module_id IS NULL
) page_scope
WHERE NOT EXISTS (
    SELECT 1
    FROM tb_web_ui_element_module existing_module
    WHERE existing_module.workspace_id = page_scope.workspace_id
      AND existing_module.module_name = '默认模块'
);

UPDATE tb_web_ui_element_page page_item
JOIN tb_web_ui_element_module default_module
  ON default_module.workspace_id = page_item.workspace_id
 AND default_module.module_name = '默认模块'
SET page_item.module_id = default_module.id
WHERE page_item.module_id IS NULL;
