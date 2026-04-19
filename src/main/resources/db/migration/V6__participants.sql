CREATE TABLE participants (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users (id),
    challenge_id BIGINT NOT NULL REFERENCES challenges (id) ON DELETE CASCADE,
    subtask_id BIGINT REFERENCES subtasks (id) ON DELETE CASCADE,
    joined_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_participants_user_id ON participants (user_id);
CREATE INDEX idx_participants_challenge_id ON participants (challenge_id);
CREATE INDEX idx_participants_subtask_id ON participants (subtask_id);
