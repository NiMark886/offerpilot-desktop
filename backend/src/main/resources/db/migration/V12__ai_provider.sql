CREATE TABLE ai_provider (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    name VARCHAR(64) NOT NULL,
    provider_type VARCHAR(32) NOT NULL,
    base_url VARCHAR(512) NOT NULL,
    model VARCHAR(128) NOT NULL,
    api_key_cipher TEXT NOT NULL,
    api_key_hint VARCHAR(32) NOT NULL,
    temperature DOUBLE NOT NULL DEFAULT 0.4,
    enabled TINYINT NOT NULL DEFAULT 1,
    active TINYINT NOT NULL DEFAULT 0,
    deleted TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL
);

CREATE INDEX idx_ai_provider_user_active ON ai_provider(user_id, active);
CREATE INDEX idx_ai_provider_user_enabled ON ai_provider(user_id, enabled);
