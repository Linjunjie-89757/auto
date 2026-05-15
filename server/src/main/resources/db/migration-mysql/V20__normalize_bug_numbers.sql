UPDATE tb_bug_info target
JOIN (
    SELECT id, ROW_NUMBER() OVER (ORDER BY id) AS seq_no
    FROM tb_bug_info
) numbered ON numbered.id = target.id
SET target.bug_no = CONCAT('TMP-BUG-', LPAD(numbered.seq_no, 6, '0'));

UPDATE tb_bug_info target
JOIN (
    SELECT id, ROW_NUMBER() OVER (ORDER BY id) AS seq_no
    FROM tb_bug_info
) numbered ON numbered.id = target.id
SET target.bug_no = CONCAT('BUG-', LPAD(numbered.seq_no, 3, '0'));
