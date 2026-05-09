ALTER TABLE tb_case_info ADD COLUMN execution_status VARCHAR(32) NULL;
ALTER TABLE tb_case_info ADD COLUMN executor_id BIGINT NULL;
ALTER TABLE tb_case_info ADD COLUMN created_by BIGINT NULL;
ALTER TABLE tb_case_info ADD COLUMN updated_by BIGINT NULL;

UPDATE tb_case_info
SET execution_status = 'NOT_RUN'
WHERE execution_status IS NULL;

UPDATE tb_case_info
SET executor_id = owner_id
WHERE executor_id IS NULL;

UPDATE tb_case_info
SET created_by = owner_id
WHERE created_by IS NULL;

UPDATE tb_case_info
SET updated_by = owner_id
WHERE updated_by IS NULL;
