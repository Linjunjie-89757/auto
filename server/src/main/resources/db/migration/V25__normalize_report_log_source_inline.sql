UPDATE tb_exec_report
SET log_source = 'MANUAL'
WHERE UPPER(TRIM(COALESCE(log_source, ''))) = 'INLINE';
