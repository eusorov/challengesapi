-- Rollup bookkeeping: one row per challenge after raw check_ins are summarized and removed.
CREATE TABLE check_in_rollup_runs (
    challenge_id BIGINT PRIMARY KEY REFERENCES challenges (id) ON DELETE CASCADE,
    status VARCHAR(32) NOT NULL,
    error_message TEXT,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_check_in_rollup_runs_status ON check_in_rollup_runs (status);

-- Aggregated check-ins after rollup (per user × challenge × optional subtask).
-- Partial unique indexes: one row for whole-challenge (subtask_id IS NULL) vs per-subtask rows.
CREATE TABLE check_in_summaries (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users (id),
    challenge_id BIGINT NOT NULL REFERENCES challenges (id) ON DELETE CASCADE,
    subtask_id BIGINT REFERENCES subtasks (id) ON DELETE SET NULL,
    total_check_ins BIGINT NOT NULL,
    first_check_in_date DATE NOT NULL,
    last_check_in_date DATE NOT NULL,
    rolled_up_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW()
);

CREATE UNIQUE INDEX uq_check_in_summaries_user_challenge_whole
    ON check_in_summaries (user_id, challenge_id)
    WHERE subtask_id IS NULL;

CREATE UNIQUE INDEX uq_check_in_summaries_user_challenge_subtask
    ON check_in_summaries (user_id, challenge_id, subtask_id)
    WHERE subtask_id IS NOT NULL;

CREATE INDEX idx_check_in_summaries_challenge_id ON check_in_summaries (challenge_id);
