CREATE TABLE IF NOT EXISTS tb_bug_case_relation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    bug_id BIGINT NOT NULL,
    case_id BIGINT NOT NULL,
    created_by BIGINT,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_bug_case_relation UNIQUE (bug_id, case_id),
    INDEX idx_bug_case_relation_bug_id (bug_id),
    INDEX idx_bug_case_relation_case_id (case_id)
);

INSERT INTO tb_bug_case_relation (bug_id, case_id, created_at, updated_at)
SELECT id, related_case_id, created_at, updated_at
FROM tb_bug_info
WHERE related_case_id IS NOT NULL
  AND NOT EXISTS (
      SELECT 1
      FROM tb_bug_case_relation
      WHERE tb_bug_case_relation.bug_id = tb_bug_info.id
        AND tb_bug_case_relation.case_id = tb_bug_info.related_case_id
  );
