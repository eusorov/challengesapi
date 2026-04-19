CREATE TABLE schedules (
    id BIGSERIAL PRIMARY KEY,
    challenge_id BIGINT UNIQUE REFERENCES challenges (id) ON DELETE CASCADE,
    subtask_id BIGINT UNIQUE REFERENCES subtasks (id) ON DELETE CASCADE,
    kind VARCHAR(40) NOT NULL,
    CONSTRAINT schedules_one_owner_chk CHECK (
        (challenge_id IS NOT NULL AND subtask_id IS NULL)
        OR (challenge_id IS NULL AND subtask_id IS NOT NULL)
    )
);

CREATE INDEX idx_schedules_challenge_id ON schedules (challenge_id);
CREATE INDEX idx_schedules_subtask_id ON schedules (subtask_id);
