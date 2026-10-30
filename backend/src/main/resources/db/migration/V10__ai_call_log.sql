CREATE TABLE ai_call_log (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    trace_id VARCHAR(64) NULL,
    user_id BIGINT NULL,
    session_id BIGINT NULL,
    scene VARCHAR(32) NOT NULL,
    prompt_version VARCHAR(32) NOT NULL,
    model VARCHAR(64) NOT NULL,
    prompt_tokens INT NULL,
    completion_tokens INT NULL,
    latency_ms INT NOT NULL,
    success TINYINT NOT NULL,
    fallback TINYINT NOT NULL DEFAULT 0,
    error_code VARCHAR(64) NULL,
    created_at DATETIME(3) NOT NULL
);

CREATE INDEX idx_ai_call_log_user_time ON ai_call_log(user_id, created_at);
CREATE INDEX idx_ai_call_log_scene_time ON ai_call_log(scene, created_at);
