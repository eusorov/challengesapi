CREATE TABLE invites (
    id BIGSERIAL PRIMARY KEY,
    inviter_id BIGINT NOT NULL REFERENCES users (id),
    invitee_id BIGINT NOT NULL REFERENCES users (id),
    challenge_id BIGINT NOT NULL REFERENCES challenges (id) ON DELETE CASCADE,
    subtask_id BIGINT REFERENCES subtasks (id) ON DELETE CASCADE,
    status VARCHAR(24) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    expires_at TIMESTAMP WITH TIME ZONE
);

CREATE INDEX idx_invites_inviter_id ON invites (inviter_id);
CREATE INDEX idx_invites_invitee_id ON invites (invitee_id);
CREATE INDEX idx_invites_challenge_id ON invites (challenge_id);
CREATE INDEX idx_invites_subtask_id ON invites (subtask_id);
