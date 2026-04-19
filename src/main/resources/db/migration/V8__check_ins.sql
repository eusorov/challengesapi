CREATE TABLE check_ins (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users (id),
    challenge_id BIGINT NOT NULL REFERENCES challenges (id) ON DELETE CASCADE,
    check_date DATE NOT NULL,
    subtask_id BIGINT REFERENCES subtasks (id) ON DELETE CASCADE
);

CREATE INDEX idx_check_ins_user_id ON check_ins (user_id);
CREATE INDEX idx_check_ins_challenge_id ON check_ins (challenge_id);
CREATE INDEX idx_check_ins_subtask_id ON check_ins (subtask_id);
CREATE INDEX idx_check_ins_check_date ON check_ins (check_date);
