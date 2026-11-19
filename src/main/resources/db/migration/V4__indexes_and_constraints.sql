-- Índices y restricciones para consultas y calidad de datos.

CREATE INDEX IF NOT EXISTS idx_expenses_location ON expenses(location_id);
CREATE INDEX IF NOT EXISTS idx_expenses_category ON expenses(category_id);
CREATE INDEX IF NOT EXISTS idx_locations_user_place ON locations(user_id, place_id);
CREATE INDEX IF NOT EXISTS idx_categories_user_nombre ON categories(user_id, LOWER(nombre));
CREATE INDEX IF NOT EXISTS idx_saved_places_user_alias ON saved_places(user_id, LOWER(alias));

-- Deduplicar categorías con el mismo nombre (sin distinguir mayúsculas) antes de crear el índice único.
WITH ranked AS (
    SELECT id,
           user_id,
           LOWER(nombre) AS nombre_norm,
           ROW_NUMBER() OVER (PARTITION BY user_id, LOWER(nombre) ORDER BY id) AS rn
    FROM categories
)
UPDATE expenses e
SET category_id = keeper.id
FROM ranked dup
JOIN ranked keeper
  ON keeper.user_id = dup.user_id
 AND keeper.nombre_norm = dup.nombre_norm
 AND keeper.rn = 1
WHERE dup.rn > 1
  AND e.category_id = dup.id;

WITH ranked AS (
    SELECT id,
           user_id,
           LOWER(nombre) AS nombre_norm,
           ROW_NUMBER() OVER (PARTITION BY user_id, LOWER(nombre) ORDER BY id) AS rn
    FROM categories
)
DELETE FROM categories c
USING ranked dup
WHERE c.id = dup.id
  AND dup.rn > 1;

CREATE UNIQUE INDEX IF NOT EXISTS uq_categories_user_nombre_lower
    ON categories(user_id, LOWER(nombre));

-- Restricciones de dominio.
ALTER TABLE expenses
    ADD CONSTRAINT chk_expenses_monto_positivo CHECK (monto > 0);

ALTER TABLE locations
    ADD CONSTRAINT chk_locations_latitud CHECK (latitud IS NULL OR (latitud BETWEEN -90 AND 90)),
    ADD CONSTRAINT chk_locations_longitud CHECK (longitud IS NULL OR (longitud BETWEEN -180 AND 180));

ALTER TABLE categories
    ADD CONSTRAINT chk_categories_color CHECK (color IS NULL OR color ~ '^#[0-9A-Fa-f]{6}$');
