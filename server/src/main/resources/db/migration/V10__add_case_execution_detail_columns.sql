ALTER TABLE tb_case_info ADD COLUMN execution_comment TEXT;
ALTER TABLE tb_case_info ADD COLUMN executed_at TIMESTAMP;

UPDATE tb_case_info
SET execution_status = 'BLOCKED'
WHERE execution_status = 'RUNNING';
