CREATE TABLE resume (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    file_name VARCHAR(255) NOT NULL,
    file_path VARCHAR(512) NOT NULL,
    content_type VARCHAR(128) NULL,
    file_size BIGINT NOT NULL,
    raw_text TEXT NULL,
    parsed_json TEXT NULL,
    parse_status VARCHAR(20) NOT NULL,
    deleted TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL
);

CREATE INDEX idx_resume_user_id ON resume(user_id);

CREATE TABLE interview_session (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id BIGINT NOT NULL,
    job_template_id BIGINT NOT NULL,
    resume_id BIGINT NOT NULL,
    difficulty VARCHAR(20) NOT NULL,
    duration_minutes INT NOT NULL,
    persona VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    blueprint_json TEXT NULL,
    total_score INT NULL,
    started_at DATETIME(3) NULL,
    ended_at DATETIME(3) NULL,
    deleted TINYINT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL
);

CREATE INDEX idx_interview_session_user_id ON interview_session(user_id);

CREATE TABLE interview_question (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id BIGINT NOT NULL,
    seq INT NOT NULL,
    type VARCHAR(20) NOT NULL,
    difficulty INT NOT NULL,
    content TEXT NOT NULL,
    knowledge_tags TEXT NULL,
    reference_points TEXT NULL,
    follow_up_hints TEXT NULL,
    status VARCHAR(20) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL
);

CREATE INDEX idx_interview_question_session_id ON interview_question(session_id);
