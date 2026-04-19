-- Challenge taxonomy: exactly one category per row, chosen from Java enum ChallengeCategory (STRING).
ALTER TABLE challenges
    ADD COLUMN category VARCHAR(64) NOT NULL DEFAULT 'OTHER';

COMMENT ON COLUMN challenges.category IS 'ChallengeCategory enum name; application validates allowed values.';
