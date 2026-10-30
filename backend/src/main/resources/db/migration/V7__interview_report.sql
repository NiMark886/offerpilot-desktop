CREATE TABLE interview_report (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    session_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    overall_score INT NOT NULL,
    dimensions_json TEXT NOT NULL,
    summary TEXT NOT NULL,
    strengths_json TEXT NULL,
    weaknesses_json TEXT NULL,
    suggestions_json TEXT NULL,
    status VARCHAR(20) NOT NULL,
    created_at DATETIME(3) NOT NULL,
    updated_at DATETIME(3) NOT NULL
);

CREATE UNIQUE INDEX uk_interview_report_session ON interview_report(session_id);
CREATE INDEX idx_interview_report_user ON interview_report(user_id);
