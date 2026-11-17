-- Plantillas de gastos recurrentes.

CREATE TABLE recurring_expenses (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    category_id BIGINT REFERENCES categories(id) ON DELETE SET NULL,
    location_id BIGINT REFERENCES locations(id) ON DELETE SET NULL,
    monto NUMERIC(12, 2) NOT NULL,
    fecha_inicio DATE NOT NULL,
    proxima_fecha DATE NOT NULL,
    fecha_fin DATE,
    frecuencia VARCHAR(10) NOT NULL,
    descripcion VARCHAR(255),
    activo BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_recurring_monto_positivo CHECK (monto > 0),
    CONSTRAINT chk_recurring_frecuencia CHECK (frecuencia IN ('DAILY', 'WEEKLY', 'MONTHLY', 'YEARLY')),
    CONSTRAINT chk_recurring_fechas CHECK (fecha_fin IS NULL OR fecha_fin >= fecha_inicio)
);

CREATE INDEX idx_recurring_user_activo_proxima
    ON recurring_expenses(user_id, activo, proxima_fecha);
