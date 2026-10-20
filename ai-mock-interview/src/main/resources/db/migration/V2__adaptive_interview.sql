ALTER TABLE mi_interview ADD COLUMN mode VARCHAR(12) NOT NULL DEFAULT 'PRACTICE';
ALTER TABLE mi_interview ADD COLUMN phase VARCHAR(24) NOT NULL DEFAULT 'READY';
ALTER TABLE mi_interview ADD COLUMN max_follow_ups INT NOT NULL DEFAULT 3;
ALTER TABLE mi_turn ADD COLUMN parent_turn_id BIGINT;
ALTER TABLE mi_turn ADD COLUMN root_sequence INT NOT NULL DEFAULT 0;
ALTER TABLE mi_turn ADD COLUMN depth INT NOT NULL DEFAULT 0;
ALTER TABLE mi_turn ADD COLUMN remaining_millis BIGINT;
ALTER TABLE mi_turn ADD COLUMN follow_up_reason VARCHAR(500);
UPDATE mi_turn SET root_sequence = sequence_no;
UPDATE mi_interview SET phase = 'ANSWERING'
WHERE status = 'IN_PROGRESS' AND EXISTS (
    SELECT 1 FROM mi_turn WHERE mi_turn.interview_id = mi_interview.id AND mi_turn.status = 'ACTIVE'
);
CREATE UNIQUE INDEX uk_turn_follow_up_parent ON mi_turn(parent_turn_id);
