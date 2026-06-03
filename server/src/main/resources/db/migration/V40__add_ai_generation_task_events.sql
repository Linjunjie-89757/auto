ALTER TABLE tb_ai_generation_task
    ADD COLUMN IF NOT EXISTS generation_raw_output TEXT;

ALTER TABLE tb_ai_generation_task
    ADD COLUMN IF NOT EXISTS review_raw_output TEXT;

CREATE TABLE IF NOT EXISTS tb_ai_generation_task_event (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    task_id VARCHAR(64) NOT NULL,
    seq INT NOT NULL,
    event_type VARCHAR(64) NOT NULL,
    phase VARCHAR(32) NOT NULL,
    level VARCHAR(16) NOT NULL,
    message VARCHAR(1000) NOT NULL,
    item_index INT,
    item_title VARCHAR(255),
    provider VARCHAR(64),
    model VARCHAR(128),
    payload_json TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_ai_generation_task_event_seq UNIQUE (task_id, seq)
);

CREATE INDEX IF NOT EXISTS idx_ai_generation_task_event_task_id
    ON tb_ai_generation_task_event (task_id);

CREATE INDEX IF NOT EXISTS idx_ai_generation_task_event_created_at
    ON tb_ai_generation_task_event (created_at);
