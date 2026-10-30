CREATE TABLE user_quota (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    plan VARCHAR(20) NOT NULL DEFAULT 'FREE',
    period_start DATETIME(3) NOT NULL,
    period_end DATETIME(3) NOT NULL,
    session_limit INT NOT NULL DEFAULT 3,
    session_used INT NOT NULL DEFAULT 0,
    token_limit INT NOT NULL DEFAULT 200000,
    token_used INT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL
);

CREATE UNIQUE INDEX uk_user_quota_user ON user_quota(user_id);

INSERT INTO sys_user (email, password_hash, nickname, role, status, deleted, created_at, updated_at)
SELECT 'admin@offerpilot.local',
       '$2a$10$nNb26GYj3EUuomya0HhIR.QqXQOQ.qUTme3MZbYrNPLK90J/y2emC',
       'Admin', 'ADMIN', 'ACTIVE', 0, CURRENT_TIMESTAMP(3), CURRENT_TIMESTAMP(3)
WHERE NOT EXISTS (SELECT 1 FROM sys_user WHERE email = 'admin@offerpilot.local');
