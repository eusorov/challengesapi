CREATE TABLE subtasks (
    id BIGSERIAL PRIMARY KEY,
    challenge_id BIGINT NOT NULL REFERENCES challenges (id) ON DELETE CASCADE,
    title VARCHAR(500) NOT NULL,
    sort_index INTEGER NOT NULL
);

CREATE INDEX idx_subtasks_challenge_id ON subtasks (challenge_id);
