CREATE TABLE IF NOT EXISTS tb_api_definition_module (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    workspace_id BIGINT NOT NULL,
    parent_id BIGINT,
    module_name VARCHAR(255) NOT NULL,
    sort_order INT NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

INSERT INTO tb_api_definition_module (workspace_id, parent_id, module_name, sort_order, created_at, updated_at)
SELECT workspace_id, NULL, directory_name, 10, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
FROM (
    SELECT DISTINCT workspace_id, directory_name
    FROM tb_api_definition
    WHERE directory_name IS NOT NULL AND TRIM(directory_name) <> '' AND directory_name NOT LIKE '%/%'
) directories
WHERE NOT EXISTS (
    SELECT 1
    FROM tb_api_definition_module existing
    WHERE existing.workspace_id = directories.workspace_id
      AND existing.parent_id IS NULL
      AND existing.module_name = directories.directory_name
);
