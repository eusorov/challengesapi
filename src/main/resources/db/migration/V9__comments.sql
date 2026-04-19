CREATE TABLE comments (
    id BIGSERIAL PRIMARY KEY,
    author_user_id BIGINT NOT NULL REFERENCES users (id),
    challenge_id BIGINT NOT NULL REFERENCES challenges (id) ON DELETE CASCADE,
    subtask_id BIGINT REFERENCES subtasks (id) ON DELETE CASCADE,
    body VARCHAR(8000) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_comments_author_user_id ON comments (author_user_id);
CREATE INDEX idx_comments_challenge_id ON comments (challenge_id);
CREATE INDEX idx_comments_subtask_id ON comments (subtask_id);
