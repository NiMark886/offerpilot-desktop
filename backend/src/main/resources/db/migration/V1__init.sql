CREATE TABLE sys_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(128) NULL,
    phone VARCHAR(32) NULL,
    password_hash VARCHAR(100) NOT NULL,
    nickname VARCHAR(64) NOT NULL,
    role VARCHAR(20) NOT NULL DEFAULT 'USER',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    deleted TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL
);

CREATE UNIQUE INDEX uk_sys_user_email ON sys_user(email);
CREATE UNIQUE INDEX uk_sys_user_phone ON sys_user(phone);

CREATE TABLE user_refresh_token (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    jti VARCHAR(64) NOT NULL,
    token_hash CHAR(64) NOT NULL,
    expires_at DATETIME(3) NOT NULL,
    revoked TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL
);

CREATE UNIQUE INDEX uk_refresh_token_jti ON user_refresh_token(jti);
CREATE INDEX idx_refresh_token_user_id ON user_refresh_token(user_id);

CREATE TABLE job_template (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(128) NOT NULL,
    level VARCHAR(32) NOT NULL,
    category VARCHAR(64) NOT NULL,
    jd_text TEXT NOT NULL,
    config_json TEXT NOT NULL,
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    sort_order INT NOT NULL DEFAULT 0,
    deleted TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL
);

CREATE INDEX idx_job_template_status_sort ON job_template(status, sort_order);
