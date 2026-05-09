ALTER TABLE tb_exec_report
    ADD COLUMN log_source VARCHAR(32);

UPDATE tb_exec_report
SET log_source = 'MANUAL'
WHERE log_source IS NULL OR TRIM(log_source) = '';
