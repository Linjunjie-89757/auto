DELETE t1
FROM tb_ai_case_config t1
JOIN tb_ai_case_config t2
  ON t1.role_type = t2.role_type
 AND t1.id < t2.id;

UPDATE tb_ai_case_config
SET workspace_id = 0;
