ALTER TABLE interview_session ADD COLUMN mode VARCHAR(20) NOT NULL DEFAULT 'INTERVIEW';

CREATE TABLE answer_evaluation (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id BIGINT NOT NULL,
    question_id BIGINT NOT NULL,
    candidate_message_id BIGINT NOT NULL,
    correctness VARCHAR(20) NOT NULL,
    score INT NOT NULL,
    comment TEXT NOT NULL,
    missing_points TEXT NULL,
    confidence DOUBLE NOT NULL,
    created_at DATETIME(3) NOT NULL
);

CREATE INDEX idx_answer_evaluation_session ON answer_evaluation(session_id);
CREATE INDEX idx_answer_evaluation_candidate_msg ON answer_evaluation(candidate_message_id);
