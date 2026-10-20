CREATE TABLE mi_user (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    username VARCHAR(32) NOT NULL UNIQUE,
    password VARCHAR(100) NOT NULL,
    role VARCHAR(20) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE TABLE mi_question (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    position VARCHAR(100) NOT NULL,
    content TEXT NOT NULL,
    category VARCHAR(32) NOT NULL,
    difficulty VARCHAR(10) NOT NULL,
    reference_answer TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX idx_question_position ON mi_question(position, difficulty);
CREATE TABLE mi_interview (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    candidate_id BIGINT NOT NULL,
    position VARCHAR(100) NOT NULL,
    job_description TEXT NOT NULL,
    difficulty VARCHAR(10) NOT NULL,
    source VARCHAR(10) NOT NULL,
    seconds_per_question INT NOT NULL,
    status VARCHAR(20) NOT NULL,
    report_status VARCHAR(20) NOT NULL,
    report_json TEXT,
    total_score INT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    started_at TIMESTAMP,
    finished_at TIMESTAMP,
    FOREIGN KEY (candidate_id) REFERENCES mi_user(id)
);
CREATE INDEX idx_interview_candidate ON mi_interview(candidate_id, created_at);
CREATE TABLE mi_turn (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    interview_id BIGINT NOT NULL,
    sequence_no INT NOT NULL,
    content TEXT NOT NULL,
    category VARCHAR(32) NOT NULL,
    reference_answer TEXT NOT NULL,
    answer TEXT,
    status VARCHAR(20) NOT NULL,
    score_status VARCHAR(20) NOT NULL,
    score INT,
    evaluation_json TEXT,
    deadline TIMESTAMP,
    answered_at TIMESTAMP,
    UNIQUE (interview_id, sequence_no),
    FOREIGN KEY (interview_id) REFERENCES mi_interview(id)
);
CREATE INDEX idx_turn_deadline ON mi_turn(status, deadline);
CREATE TABLE mi_ai_task (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    type VARCHAR(10) NOT NULL,
    target_id BIGINT NOT NULL,
    status VARCHAR(20) NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    error_message VARCHAR(500),
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE (type, target_id)
);
CREATE INDEX idx_task_status ON mi_ai_task(status, updated_at);
