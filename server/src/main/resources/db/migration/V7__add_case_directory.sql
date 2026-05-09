CREATE TABLE IF NOT EXISTS tb_case_directory (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    workspace_id BIGINT NOT NULL,
    parent_id BIGINT,
    directory_name VARCHAR(128) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

ALTER TABLE tb_case_info ADD COLUMN case_directory_id BIGINT;

MERGE INTO tb_case_directory (id, workspace_id, parent_id, directory_name, created_at, updated_at) KEY(id) VALUES
(10001, 1, NULL, '核心流程', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(10002, 1, NULL, '回归补齐', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(10003, 2, NULL, '异常处理', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
(10004, 3, NULL, '兼容回归', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

UPDATE tb_case_info SET case_directory_id = 10001 WHERE id = 128;
UPDATE tb_case_info SET case_directory_id = 10002 WHERE id = 129;
UPDATE tb_case_info SET case_directory_id = 10003 WHERE id = 130;
UPDATE tb_case_info SET case_directory_id = 10004 WHERE id = 131;
