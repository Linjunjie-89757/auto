CREATE TABLE IF NOT EXISTS tb_db_connection (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    workspace_id BIGINT NOT NULL,
    connection_name VARCHAR(255) NOT NULL,
    db_type VARCHAR(32) NOT NULL,
    driver_class_name VARCHAR(255),
    jdbc_url VARCHAR(1000) NOT NULL,
    username VARCHAR(255),
    password_encrypted TEXT,
    pool_max INT NOT NULL DEFAULT 10,
    timeout_ms INT NOT NULL DEFAULT 5000,
    description TEXT,
    status TINYINT NOT NULL DEFAULT 1,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_db_connection_workspace ON tb_db_connection (workspace_id);
