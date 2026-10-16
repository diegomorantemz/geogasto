CREATE TABLE users (
                       id BIGSERIAL PRIMARY KEY,
                       email VARCHAR(100) NOT NULL UNIQUE,
                       password VARCHAR(255) NOT NULL,
                       nombre VARCHAR(100) NOT NULL,
                       moneda VARCHAR(3) NOT NULL DEFAULT 'USD',
                       timezone VARCHAR(50) NOT NULL DEFAULT 'America/Lima',
                       created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE categories (
                            id BIGSERIAL PRIMARY KEY,
                            nombre VARCHAR(50) NOT NULL,
                            icono VARCHAR(50),
                            color VARCHAR(7),
                            user_id BIGINT NOT NULL REFERENCES users(id),
                            created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE locations (
                           id BIGSERIAL PRIMARY KEY,
                           direccion VARCHAR(500),
                           latitud DOUBLE PRECISION,
                           longitud DOUBLE PRECISION,
                           ciudad VARCHAR(100),
                           pais VARCHAR(100),
                           place_id VARCHAR(255),
                           created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE expenses (
                          id BIGSERIAL PRIMARY KEY,
                          monto NUMERIC(12,2) NOT NULL,
                          fecha DATE NOT NULL,
                          descripcion VARCHAR(255),
                          category_id BIGINT REFERENCES categories(id),
                          location_id BIGINT REFERENCES locations(id),
                          user_id BIGINT NOT NULL REFERENCES users(id),
                          created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_expenses_user_fecha ON expenses(user_id, fecha);