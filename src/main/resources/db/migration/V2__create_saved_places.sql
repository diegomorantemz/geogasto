CREATE TABLE saved_places (
                              id BIGSERIAL PRIMARY KEY,
                              user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
                              alias VARCHAR(50) NOT NULL,
                              location_id BIGINT NOT NULL REFERENCES locations(id) ON DELETE CASCADE,
                              usage_count INT NOT NULL DEFAULT 0,
                              created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                              UNIQUE(user_id, alias)
);

CREATE INDEX idx_saved_places_user ON saved_places(user_id);