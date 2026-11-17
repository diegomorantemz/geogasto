-- Comprobantes adjuntos por gasto (imagen o PDF).

CREATE TABLE receipts (
    id BIGSERIAL PRIMARY KEY,
    expense_id BIGINT NOT NULL UNIQUE REFERENCES expenses(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    nombre_original VARCHAR(255) NOT NULL,
    nombre_archivo VARCHAR(255) NOT NULL UNIQUE,
    content_type VARCHAR(100) NOT NULL,
    tamano BIGINT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_receipts_user ON receipts(user_id);
