CREATE EXTENSION IF NOT EXISTS postgis;

ALTER TABLE challenges
    ADD COLUMN city VARCHAR(255) NULL,
    ADD COLUMN location geography(Point, 4326) NULL;

CREATE INDEX idx_challenges_location_gist ON challenges USING GIST (location)
    WHERE location IS NOT NULL;
