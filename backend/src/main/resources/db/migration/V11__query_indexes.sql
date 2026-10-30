CREATE INDEX idx_interview_session_user_created ON interview_session(user_id, created_at);
CREATE INDEX idx_interview_question_session_status_seq ON interview_question(session_id, status, seq);
CREATE INDEX idx_interview_message_session_id_id ON interview_message(session_id, id);
CREATE INDEX idx_answer_evaluation_session_question ON answer_evaluation(session_id, question_id);
CREATE INDEX idx_interview_report_user_status ON interview_report(user_id, status);
CREATE INDEX idx_question_bank_status_category ON question_bank(status, category);
