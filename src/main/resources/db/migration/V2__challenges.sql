CREATE TABLE challenges (
    id BIGSERIAL PRIMARY KEY,
    owner_user_id BIGINT NOT NULL REFERENCES users (id),
    title VARCHAR(500) NOT NULL,
    description VARCHAR(8000),
    start_date DATE NOT NULL,
    end_date DATE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL
);

CREATE INDEX idx_challenges_owner_user_id ON challenges (owner_user_id);
