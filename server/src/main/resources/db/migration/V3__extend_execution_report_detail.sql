ALTER TABLE tb_exec_report ADD COLUMN IF NOT EXISTS log_text TEXT;
ALTER TABLE tb_exec_report ADD COLUMN IF NOT EXISTS attachments_json TEXT;
