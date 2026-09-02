-- Saved places: user-defined booking shortcuts (home, work, ...)
CREATE TABLE IF NOT EXISTS saved_places (
    id            UUID PRIMARY KEY,
    user_id       UUID NOT NULL,
    label         VARCHAR(100) NOT NULL,
    address       VARCHAR(255) NOT NULL,
    latitude      DOUBLE PRECISION NOT NULL,
    longitude     DOUBLE PRECISION NOT NULL,
    created_at    TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX IF NOT EXISTS idx_saved_places_user ON saved_places(user_id);

-- Per-user application preferences (server-side persistence, JWT-scoped)
CREATE TABLE IF NOT EXISTS user_preferences (
    user_id       UUID PRIMARY KEY,
    notify_ride_updates  BOOLEAN NOT NULL DEFAULT TRUE,
    notify_promotions    BOOLEAN NOT NULL DEFAULT FALSE,
    notify_email         BOOLEAN NOT NULL DEFAULT TRUE,
    notify_sms           BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at           TIMESTAMPTZ NOT NULL DEFAULT now()
);
