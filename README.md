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