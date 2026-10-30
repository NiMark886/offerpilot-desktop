CREATE TABLE resume_draft (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    title VARCHAR(120) NOT NULL,
    content_json TEXT NOT NULL,
    deleted TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL
);

CREATE INDEX idx_resume_draft_user_id ON resume_draft(user_id);
