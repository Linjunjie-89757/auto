CREATE TEMPORARY TABLE tmp_bug_numbered AS
SELECT id, ROW_NUMBER() OVER (ORDER BY id) AS seq_no
FROM tb_bug_info;

UPDATE tb_bug_info
SET bug_no = CONCAT(
    'TMP-BUG-',
    LPAD(CAST((SELECT seq_no FROM tmp_bug_numbered WHERE tmp_bug_numbered.id = tb_bug_info.id) AS VARCHAR), 6, '0')
);

UPDATE tb_bug_info
SET bug_no = CONCAT(
    'BUG-',
    LPAD(CAST((SELECT seq_no FROM tmp_bug_numbered WHERE tmp_bug_numbered.id = tb_bug_info.id) AS VARCHAR), 3, '0')
);

DROP TABLE tmp_bug_numbered;
