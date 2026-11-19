-- Centro de notificaciones in-app.

CREATE TABLE notifications (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    tipo VARCHAR(30) NOT NULL,
    titulo VARCHAR(150) NOT NULL,
    mensaje VARCHAR(500) NOT NULL,
    link VARCHAR(255),
    leida BOOLEAN NOT NULL DEFAULT FALSE,
    dedupe_key VARCHAR(180) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_notifications_user_dedupe UNIQUE (user_id, dedupe_key)
);

CREATE INDEX idx_notifications_user_leida
    ON notifications(user_id, leida, created_at DESC);
