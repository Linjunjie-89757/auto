DELETE FROM tb_ai_case_config
WHERE id NOT IN (
    SELECT MAX(id)
    FROM tb_ai_case_config
    GROUP BY role_type
);

UPDATE tb_ai_case_config
SET workspace_id = 0;
