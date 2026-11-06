# GeoGasto

Sistema web de administración de gastos personales con registro de ubicación geográfica.

Permite registrar gastos, clasificarlos por categorías, y asociarlos a una ubicación
obtenida por GPS, búsqueda por nombre o selección manual en un mapa interactivo.

---

## Tabla de contenidos

- [Descripción](#descripción)
- [Características principales](#características-principales)
- [Arquitectura](#arquitectura)
- [Stack tecnológico](#stack-tecnológico)
- [Requisitos previos](#requisitos-previos)
- [Instalación y ejecución](#instalación-y-ejecución)
- [Configuración](#configuración)
- [Endpoints de la API](#endpoints-de-la-api)
- [Estructura del proyecto](#estructura-del-proyecto)
- [Uso de la aplicación](#uso-de-la-aplicación)
- [Decisiones técnicas](#decisiones-técnicas)
- [Autor](#autor)

---

## Descripción

**GeoGasto** es una aplicación web full-stack que resuelve un problema común:
la mayoría de las apps de finanzas personales ignoran **dónde** ocurrió cada gasto.
GeoGasto integra la ubicación como ciudadano de primera clase, permitiendo:

- Ver en un mapa dónde gastas más.
- Identificar patrones geográficos (ej: gastas más en restaurantes cerca de la oficina).
- Guardar lugares frecuentes ("Casa", "Trabajo", "Súper") para acelerar el registro.

El proyecto fue desarrollado como trabajo universitario, aplicando buenas prácticas
de desarrollo backend con Spring Boot y arquitectura REST.

---

## Características principales

### Gestión de gastos
- CRUD completo de gastos con monto, fecha, descripción y categoría.
- Filtros por rango de fechas y categoría.
- Paginación de resultados.

### Gestión de categorías
- CRUD completo con nombre, color e icono personalizables.
- Categorías por usuario (cada quien gestiona las suyas).

### Ubicaciones (feature principal)
- **GPS actual**: obtiene la ubicación del navegador y la convierte en dirección.
- **Búsqueda por nombre**: autocompletado con lugares reales (Nominatim/OpenStreetMap).
- **Selección en mapa**: clic en el mapa Leaflet para marcar un punto exacto.
- **Lugares guardados**: alias como "Casa" o "Trabajo" para usar en un clic.

### Dashboard
- Total gastado en el mes actual.
- Cantidad de gastos y categorías usadas.
- Gráfico de dona: distribución por categoría (mes actual).
- Gráfico de barras: evolución mensual (últimos 12 meses).

### Seguridad
- Autenticación con JWT (JSON Web Tokens).
- Contraseñas hasheadas con BCrypt.
- Aislamiento de datos por usuario: cada quien ve solo sus gastos.

### Extras
- Exportación de gastos a CSV.
- Documentación interactiva con Swagger UI.

---

## Arquitectura

El proyecto sigue una arquitectura en capas clásica de Spring Boot:

┌─────────────────────────────────────────┐
│ Frontend (HTML + Bootstrap + JS) │
│ - Páginas estáticas en /static │
│ - Fetch API con JWT en localStorage │
└────────────────┬────────────────────────┘
│ HTTP/JSON
┌────────────────▼────────────────────────┐
│ API REST (Spring Boot) │
│ ┌──────────────────────────────────┐ │
│ │ Controllers (@RestController) │ │
│ ├──────────────────────────────────┤ │
│ │ Services (lógica de negocio) │ │
│ ├──────────────────────────────────┤ │
│ │ Repositories (Spring Data JPA) │ │
│ └──────────────────────────────────┘ │
└────────────────┬────────────────────────┘
│ JDBC
┌────────────────▼────────────────────────┐
│ PostgreSQL │
│ - Migraciones con Flyway │
└─────────────────────────────────────────┘

Servicios externos:
- **Nominatim (OpenStreetMap)**: geocodificación (búsqueda y reverse).
- **OpenStreetMap tiles**: renderizado del mapa (Leaflet).

---

## Stack tecnológico

### Backend
| Tecnología | Versión | Uso |
|------------|---------|-----|
| Java | 17 | Lenguaje |
| Spring Boot | 4.1.1 | Framework principal |
| Spring Security | 7.x | Autenticación y autorización |
| Spring Data JPA | 4.x | Persistencia |
| Hibernate | 7.x | ORM |
| JJWT | 0.12.6 | Generación/validación de JWT |
| Flyway | 12.x | Migraciones de base de datos |
| Lombok | 1.18.x | Reducción de boilerplate |
| PostgreSQL Driver | 42.7.x | Conexión a PostgreSQL |
| SpringDoc OpenAPI | 2.6.0 | Documentación de API |

### Frontend
| Tecnología | Uso |
|------------|-----|
| HTML5 + CSS3 | Estructura y estilos |
| Bootstrap 5.3 | Framework CSS responsive |
| Bootstrap Icons | Iconografía |
| JavaScript (vanilla) | Lógica de cliente |
| Leaflet 1.9 | Mapas interactivos |
| Chart.js 4.4 | Gráficos del dashboard |

### Base de datos
- **PostgreSQL 14+**

---

## Requisitos previos

Antes de ejecutar el proyecto, asegúrate de tener instalado:

- **Java 17** o superior ([descarga](https://adoptium.net/))
- **Maven 3.9+** (o usa el wrapper `./mvnw` incluido)
- **PostgreSQL 14+** corriendo en `localhost:5432`
- **Git**

### Verificar instalación

```bash
java -version    # debe mostrar 17 o superior
mvn -version     # debe mostrar 3.9+
psql --version   # debe mostrar PostgreSQL 14+
```

> El puerto de PostgreSQL se configura en el archivo `.env` (por defecto el proyecto
> de ejemplo usa `5432`, pero puedes usar cualquier puerto, por ejemplo `5433`).

---

## Instalación y ejecución

### 1. Clonar el repositorio

```bash
git clone <url-del-repositorio> geogasto
cd geogasto
```

### 2. Crear la base de datos

```bash
createdb -U postgres GeoGasto
```

Si tu instalación usa otro usuario o puerto:

```bash
createdb -h localhost -p 5433 -U postgres GeoGasto
```

### 3. Configurar variables de entorno

Copia el archivo de ejemplo y completa tus credenciales:

```bash
cp .env.example .env
```

```properties
DB_URL=jdbc:postgresql://localhost:5432/GeoGasto
DB_USER=postgres
DB_PASSWORD=tu_password
```

> `.env` está en `.gitignore`: nunca subas credenciales reales al repositorio.

### 4. Ejecutar la aplicación

```bash
./mvnw spring-boot:run
```

La aplicación queda disponible en:

- Aplicación web: <http://localhost:8080>
- Swagger UI: <http://localhost:8080/swagger-ui.html>

Flyway crea y actualiza el esquema automáticamente al arrancar.

### 5. Ejecutar las pruebas

```bash
./mvnw test
```

> Las pruebas de integración actuales requieren una PostgreSQL accesible con las
> credenciales del `.env`.

### 6. Compilar el JAR

```bash
./mvnw clean package
java -jar target/geogasto-0.0.1-SNAPSHOT.jar
```

---

## Configuración

| Variable / propiedad | Descripción | Valor por defecto |
|----------------------|-------------|-------------------|
| `DB_URL` | URL JDBC de PostgreSQL | — |
| `DB_USER` | Usuario de la base de datos | — |
| `DB_PASSWORD` | Contraseña de la base de datos | — |
| `JWT_SECRET` | Clave para firmar los JWT (mínimo 256 bits) | — |
| `jwt.expiration` | Vigencia del token en ms | `86400000` (24 h) |
| `nominatim.base-url` | URL de Nominatim | `https://nominatim.openstreetmap.org` |
| `server.port` | Puerto HTTP | `8080` |

> Genera `JWT_SECRET` con una clave aleatoria de al menos 256 bits, por ejemplo:
> `openssl rand -base64 48`. No reutilices la clave de ejemplo en producción.

---

## Endpoints de la API

Todas las rutas `/api/**` (excepto `/api/auth/**`) requieren el header:

```http
Authorization: Bearer <token>
```

### Autenticación

| Método | Ruta | Descripción |
|--------|------|-------------|
| `POST` | `/api/auth/register` | Registrar usuario y obtener token |
| `POST` | `/api/auth/login` | Iniciar sesión y obtener token |
| `GET` | `/api/auth/me` | Obtener perfil del usuario autenticado |
| `PUT` | `/api/auth/me` | Actualizar nombre, moneda o zona horaria |

### Categorías

| Método | Ruta | Descripción |
|--------|------|-------------|
| `GET` | `/api/categories` | Listar categorías del usuario |
| `GET` | `/api/categories/{id}` | Obtener una categoría |
| `POST` | `/api/categories` | Crear categoría |
| `PUT` | `/api/categories/{id}` | Actualizar categoría |
| `DELETE` | `/api/categories/{id}` | Eliminar categoría |

### Gastos

| Método | Ruta | Descripción |
|--------|------|-------------|
| `GET` | `/api/expenses` | Listar gastos paginados y filtrados |
| `GET` | `/api/expenses/{id}` | Obtener un gasto |
| `POST` | `/api/expenses` | Crear gasto |
| `POST` | `/api/expenses/with-location` | Crear gasto con ubicación nueva |
| `PUT` | `/api/expenses/{id}` | Actualizar gasto |
| `DELETE` | `/api/expenses/{id}` | Eliminar gasto |
| `GET` | `/api/expenses/export` | Exportar gastos a CSV |

Parámetros de `GET /api/expenses`:

| Parámetro | Ejemplo | Descripción |
|-----------|---------|-------------|
| `page` | `0` | Número de página (base 0) |
| `size` | `10` | Tamaño de página |
| `sort` | `fecha,desc` | Ordenamiento |
| `desde` | `2026-01-01` | Fecha mínima (inclusive) |
| `hasta` | `2026-12-31` | Fecha máxima (inclusive) |
| `categoryId` | `3` | Filtrar por categoría |

`GET /api/expenses/export` acepta los mismos filtros `desde`, `hasta` y
`categoryId`, y devuelve un archivo `gastos.csv` en UTF-8.

### Ubicaciones

| Método | Ruta | Descripción |
|--------|------|-------------|
| `GET` | `/api/locations/{id}` | Obtener ubicación |
| `POST` | `/api/locations` | Crear ubicación |
| `PUT` | `/api/locations/{id}` | Actualizar ubicación |

### Lugares guardados

| Método | Ruta | Descripción |
|--------|------|-------------|
| `GET` | `/api/saved-places` | Listar lugares guardados |
| `POST` | `/api/saved-places` | Guardar un lugar (`alias`, `locationId`) |
| `POST` | `/api/saved-places/{id}/use` | Incrementar contador de uso |
| `DELETE` | `/api/saved-places/{id}` | Eliminar lugar guardado |

### Geocodificación

| Método | Ruta | Descripción |
|--------|------|-------------|
| `GET` | `/api/geocode/search?q=...` | Buscar lugares por nombre |
| `GET` | `/api/geocode/reverse?lat=...&lon=...` | Obtener dirección desde coordenadas |

### Estadísticas

| Método | Ruta | Descripción |
|--------|------|-------------|
| `GET` | `/api/stats` | Resumen del mes y últimos 12 meses |

---

## Estructura del proyecto

```text
src/main/java/GeoGasto/
├── config/              # Seguridad, JWT y UserDetails
├── domain/
│   ├── dto/             # Request/Response por módulo
│   ├── exception/       # Excepciones de dominio
│   └── service/         # Lógica de negocio
├── persistence/
│   ├── entity/          # Entidades JPA
│   └── repository/      # Repositorios Spring Data
└── web/
    ├── advice/          # Manejo global de errores
    └── controller/      # Endpoints REST

src/main/resources/
├── db/migration/        # Migraciones Flyway (V1, V2, ...)
└── static/              # Frontend HTML + CSS + JS
```

---

## Uso de la aplicación

1. **Regístrate** en `/register.html` e inicia sesión en `/login.html`.
2. **Crea categorías** en `/categories.html` (nombre, color e icono).
3. **Registra un gasto** en `/new-expense.html`:
   - Usa tu ubicación GPS actual.
   - Busca un lugar por nombre.
   - Haz clic o arrastra el marcador en el mapa.
   - O guarda el lugar como favorito ("Casa", "Trabajo", etc.).
4. **Consulta y filtra** tus gastos en `/expenses.html` por fecha y categoría.
5. **Exporta** el listado filtrado a CSV.
6. **Revisa el dashboard** en `/index.html`: total del mes, distribución por
   categoría y evolución de los últimos 12 meses.

---

## Decisiones técnicas

- **Arquitectura en capas** (`controller → service → repository`) para separar
  responsabilidades y facilitar las pruebas.
- **DTOs** en lugar de exponer entidades JPA, evitando fugas de datos y ciclos
  de serialización.
- **JWT stateless** con BCrypt: el servidor no guarda sesiones.
- **Aislamiento por usuario**: cada consulta filtra por el usuario autenticado.
- **Flyway** como única fuente del esquema; Hibernate solo valida (`ddl-auto=validate`).
- **Filtros en base de datos** mediante `Specification`, no en el navegador.
- **Nominatim/OpenStreetMap** para geocodificación y **Leaflet** para el mapa.
- **Chart.js** para las gráficas del dashboard.

### Limitaciones conocidas y siguientes pasos

- Las ubicaciones aún no tienen `user_id`; conviene validar la propiedad para
  cerrar un posible IDOR.
- Añadir pruebas con Testcontainers para no depender de una base local.
- Mover `jwt.secret` a una variable de entorno en producción.
- Agregar Docker Compose y un pipeline de CI.

---

## Autor

**Diego Morante** — Proyecto universitario de administración de gastos con
ubicación geográfica.
