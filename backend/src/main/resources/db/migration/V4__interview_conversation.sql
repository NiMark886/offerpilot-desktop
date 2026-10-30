ALTER TABLE interview_session ADD COLUMN current_question_id BIGINT NULL;

ALTER TABLE interview_question ADD COLUMN follow_up_count INT NOT NULL DEFAULT 0;
ALTER TABLE interview_question ADD COLUMN hint_count INT NOT NULL DEFAULT 0;
ALTER TABLE interview_question ADD COLUMN asked_at DATETIME(3) NULL;
ALTER TABLE interview_question ADD COLUMN answered_at DATETIME(3) NULL;

CREATE TABLE interview_message (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id BIGINT NOT NULL,
    question_id BIGINT NULL,
    role VARCHAR(20) NOT NULL,
    message_type VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,
    tokens_in INT NULL,
    tokens_out INT NULL,
    model VARCHAR(64) NULL,
    created_at DATETIME(3) NOT NULL
);

CREATE INDEX idx_interview_message_session_id ON interview_message(session_id);
