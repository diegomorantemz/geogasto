-- Ingresos para calcular balance y tasa de ahorro.

CREATE TABLE incomes (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    monto NUMERIC(12, 2) NOT NULL,
    fecha DATE NOT NULL,
    descripcion VARCHAR(255),
    fuente VARCHAR(100),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_incomes_monto_positivo CHECK (monto > 0)
);

CREATE INDEX idx_incomes_user_fecha ON incomes(user_id, fecha);
