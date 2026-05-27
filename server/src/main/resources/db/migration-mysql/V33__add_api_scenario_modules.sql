CREATE TABLE IF NOT EXISTS tb_api_scenario_module (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    workspace_id BIGINT NOT NULL,
    parent_id BIGINT,
    module_name VARCHAR(255) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE tb_api_scenario
    ADD COLUMN module_id BIGINT,
    ADD COLUMN priority VARCHAR(16) NOT NULL DEFAULT 'P1',
    ADD COLUMN status VARCHAR(32) NOT NULL DEFAULT 'IN_PROGRESS',
    ADD COLUMN scenario_assertions_json LONGTEXT,
    ADD COLUMN scenario_variables_json LONGTEXT;

INSERT INTO tb_api_scenario_module (workspace_id, parent_id, module_name, sort_order, created_at, updated_at)
SELECT workspace_id, NULL, '默认模块', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM (
    SELECT DISTINCT workspace_id
    FROM tb_api_scenario
) workspace_ids
WHERE NOT EXISTS (
    SELECT 1
    FROM tb_api_scenario_module existing
    WHERE existing.workspace_id = workspace_ids.workspace_id
      AND existing.parent_id IS NULL
      AND existing.module_name = '默认模块'
);

INSERT INTO tb_api_scenario_module (workspace_id, parent_id, module_name, sort_order, created_at, updated_at)
SELECT workspace_id, NULL, directory_name, 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM (
    SELECT DISTINCT workspace_id, directory_name
    FROM tb_api_scenario
    WHERE directory_name IS NOT NULL AND TRIM(directory_name) <> ''
) directories
WHERE NOT EXISTS (
    SELECT 1
    FROM tb_api_scenario_module existing
    WHERE existing.workspace_id = directories.workspace_id
      AND existing.parent_id IS NULL
      AND existing.module_name = directories.directory_name
);

UPDATE tb_api_scenario scenario
JOIN tb_api_scenario_module module
  ON module.workspace_id = scenario.workspace_id
 AND module.parent_id IS NULL
 AND module.module_name = scenario.directory_name
SET scenario.module_id = module.id
WHERE scenario.module_id IS NULL
  AND scenario.directory_name IS NOT NULL
  AND TRIM(scenario.directory_name) <> '';

UPDATE tb_api_scenario scenario
JOIN tb_api_scenario_module module
  ON module.workspace_id = scenario.workspace_id
 AND module.parent_id IS NULL
 AND module.module_name = '默认模块'
SET scenario.module_id = module.id
WHERE scenario.module_id IS NULL;

