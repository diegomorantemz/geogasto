-- Presupuestos mensuales por categoría.

CREATE TABLE budgets (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    category_id BIGINT NOT NULL REFERENCES categories(id) ON DELETE CASCADE,
    periodo DATE NOT NULL,
    monto NUMERIC(12, 2) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT chk_budgets_monto_positivo CHECK (monto > 0),
    CONSTRAINT uq_budgets_user_category_periodo UNIQUE (user_id, category_id, periodo)
);

CREATE INDEX idx_budgets_user_periodo ON budgets(user_id, periodo);
