-- Cada ubicación pasa a pertenecer a un usuario.
-- Se hace en tres pasos para no romper los datos existentes.

ALTER TABLE locations
    ADD COLUMN user_id BIGINT REFERENCES users(id) ON DELETE CASCADE;

-- Backfill: el dueño se deduce del gasto o lugar guardado que referencia la ubicación.
UPDATE locations l
SET user_id = ref.user_id
FROM (
    SELECT location_id, MIN(user_id) AS user_id
    FROM (
        SELECT location_id, user_id FROM expenses WHERE location_id IS NOT NULL
        UNION ALL
        SELECT location_id, user_id FROM saved_places
    ) referencias
    GROUP BY location_id
) ref
WHERE l.id = ref.location_id
  AND l.user_id IS NULL;

-- Las ubicaciones sin dueño no son accesibles por nadie: se eliminan.
DELETE FROM locations WHERE user_id IS NULL;

ALTER TABLE locations ALTER COLUMN user_id SET NOT NULL;

CREATE INDEX idx_locations_user ON locations(user_id);
