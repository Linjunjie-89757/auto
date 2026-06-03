SET @add_generation_raw_output = (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = DATABASE()
              AND table_name = 'tb_ai_generation_task'
              AND column_name = 'generation_raw_output'
        ),
        'SELECT 1',
        'ALTER TABLE tb_ai_generation_task ADD COLUMN generation_raw_output TEXT NULL'
    )
);
PREPARE stmt FROM @add_generation_raw_output;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

SET @add_review_raw_output = (
    SELECT IF(
        EXISTS (
            SELECT 1
            FROM information_schema.columns
            WHERE table_schema = DATABASE()
              AND table_name = 'tb_ai_generation_task'
              AND column_name = 'review_raw_output'
        ),
        'SELECT 1',
        'ALTER TABLE tb_ai_generation_task ADD COLUMN review_raw_output TEXT NULL'
    )
);
PREPARE stmt FROM @add_review_raw_output;
EXECUTE stmt;
DEALLOCATE PREPARE stmt;

CREATE TABLE IF NOT EXISTS tb_ai_generation_task_event (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id VARCHAR(64) NOT NULL,
    seq INT NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    phase VARCHAR(32) NOT NULL,
    level VARCHAR(16) NOT NULL,
    message VARCHAR(1000) NOT NULL,
    item_index INT NULL,
    item_title VARCHAR(255) NULL,
    provider VARCHAR(64) NULL,
    model VARCHAR(128) NULL,
    payload_json TEXT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE KEY uk_ai_generation_task_event_seq (task_id, seq),
    KEY idx_ai_generation_task_event_task_id (task_id),
    KEY idx_ai_generation_task_event_created_at (created_at)
);
