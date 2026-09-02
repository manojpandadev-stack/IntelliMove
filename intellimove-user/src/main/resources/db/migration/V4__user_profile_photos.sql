-- Rider profile photos: small avatar images stored server-side so they
-- survive logout/login on every device. Bytes live in their own table so
-- ordinary user queries never drag binary payloads.
--
-- NOTE: intentionally NO foreign key to users(id): the auth service does not
-- provision rows in the user-service database today, so photos are keyed by
-- the JWT-verified user id and served/authorized independently.
CREATE TABLE IF NOT EXISTS user_profile_photos (
    user_id      UUID PRIMARY KEY,
    content_type VARCHAR(50) NOT NULL,
    data         BYTEA NOT NULL,
    byte_size    INTEGER NOT NULL,
    updated_at   TIMESTAMPTZ NOT NULL DEFAULT NOW()
);