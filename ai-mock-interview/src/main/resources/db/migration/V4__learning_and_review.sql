ALTER TABLE mi_question ADD COLUMN job_family VARCHAR(30) NOT NULL DEFAULT 'GENERAL';
ALTER TABLE mi_question ADD COLUMN tags VARCHAR(300) NOT NULL DEFAULT ',未分类,';
ALTER TABLE mi_question ADD COLUMN review_status VARCHAR(20) NOT NULL DEFAULT 'APPROVED';
ALTER TABLE mi_question ADD COLUMN review_note VARCHAR(500) NOT NULL DEFAULT '';
ALTER TABLE mi_question ADD COLUMN reviewed_by BIGINT NULL;
ALTER TABLE mi_question ADD COLUMN reviewed_at TIMESTAMP NULL;
UPDATE mi_question SET job_family = 'JAVA_BACKEND' WHERE position = 'Java 后端';
CREATE INDEX idx_question_review ON mi_question(review_status, job_family, difficulty);
ALTER TABLE mi_interview ADD COLUMN interviewer_id BIGINT NULL;
ALTER TABLE mi_turn ADD COLUMN question_id BIGINT NULL;
ALTER TABLE mi_turn ADD COLUMN tags VARCHAR(300) NOT NULL DEFAULT ',未分类,';
ALTER TABLE mi_turn ADD COLUMN retry_of_turn_id BIGINT NULL;
CREATE INDEX idx_turn_question ON mi_turn(question_id);
CREATE INDEX idx_turn_retry ON mi_turn(retry_of_turn_id);
CREATE TABLE mi_favorite (
 user_id BIGINT NOT NULL,
 question_id BIGINT NOT NULL,
 PRIMARY KEY(user_id, question_id),
 FOREIGN KEY(user_id) REFERENCES mi_user(id) ON DELETE CASCADE,
 FOREIGN KEY(question_id) REFERENCES mi_question(id) ON DELETE CASCADE
);
CREATE TABLE mi_review (
 interview_id BIGINT PRIMARY KEY,
 reviewer_id BIGINT NOT NULL,
 score INT NULL,
 comment VARCHAR(2000) NOT NULL,
 updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
 FOREIGN KEY(interview_id) REFERENCES mi_interview(id) ON DELETE CASCADE,
 FOREIGN KEY(reviewer_id) REFERENCES mi_user(id)
);
