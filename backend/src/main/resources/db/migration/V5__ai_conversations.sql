CREATE TABLE ai_conversation (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT UNSIGNED NOT NULL,
    title VARCHAR(80) NOT NULL DEFAULT '新会话',
    version INT NOT NULL DEFAULT 0,
    deleted TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    KEY idx_ai_conversation_user (user_id, deleted, updated_at, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE ai_message (
    id BIGINT UNSIGNED AUTO_INCREMENT PRIMARY KEY,
    conversation_id BIGINT UNSIGNED NOT NULL,
    request_id VARCHAR(64) CHARACTER SET ascii COLLATE ascii_bin NOT NULL,
    role VARCHAR(16) NOT NULL,
    content TEXT NOT NULL,
    metadata_json JSON,
    deleted TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL,
    UNIQUE KEY uk_ai_message_request (conversation_id, request_id, role),
    KEY idx_ai_message_conversation (conversation_id, deleted, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
