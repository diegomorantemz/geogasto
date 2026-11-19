-- Presupuesto mensual general (además de los presupuestos por categoría).

CREATE TABLE general_budgets (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    periodo DATE NOT NULL,
    monto NUMERIC(12, 2) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_general_budgets_user_periodo UNIQUE (user_id, periodo),
    CONSTRAINT chk_general_budgets_monto CHECK (monto > 0)
);

CREATE INDEX idx_general_budgets_user_periodo ON general_budgets(user_id, periodo);
