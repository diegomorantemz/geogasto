package GeoGasto;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings({"rawtypes", "unchecked"})
@AutoConfigureTestRestTemplate
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ExpenseApiIntegrationTest extends AbstractIntegrationTest {

    private static final ZoneId ZONA = ZoneId.of("America/Lima");

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void registroPerfilMonedaYCategoriasPorDefecto() {
        String email = "perfil-" + UUID.randomUUID() + "@test.com";
        String token = registrar(email, "PEN");

        ResponseEntity<Map> me = get("/api/auth/me", token);
        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(me.getBody().get("email")).isEqualTo(email);
        assertThat(me.getBody().get("moneda")).isEqualTo("PEN");
        assertThat(me.getBody().get("nombre")).isEqualTo("Usuario Test");

        ResponseEntity<List> categorias = restTemplate.exchange(
                "/api/categories", HttpMethod.GET, conToken(token), List.class);
        assertThat(categorias.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(categorias.getBody()).hasSizeGreaterThanOrEqualTo(7);

        ResponseEntity<Map> actualizado = restTemplate.exchange(
                "/api/auth/me", HttpMethod.PUT,
                conToken(token, Map.of("moneda", "EUR")), Map.class);
        assertThat(actualizado.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(actualizado.getBody().get("moneda")).isEqualTo("EUR");

        ResponseEntity<Map> sinToken = restTemplate.getForEntity("/api/auth/me", Map.class);
        assertThat(sinToken.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void filtrosPaginacionBusquedaYExportacionCsv() {
        String token = registrar("gastos-" + UUID.randomUUID() + "@test.com", "PEN");
        long categoriaId = crearCategoria(token);

        crearGasto(token, "2026-01-10", 10.50, categoriaId, "Compra enero");
        crearGasto(token, "2026-02-10", 20.00, categoriaId, "Almuerzo de febrero");
        crearGasto(token, "2026-02-20", 30.00, categoriaId, "Cena de febrero");

        ResponseEntity<Map> febrero = get("/api/expenses?desde=2026-02-01&hasta=2026-02-28", token);
        assertThat(febrero.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((Number) febrero.getBody().get("totalElements")).intValue()).isEqualTo(2);

        ResponseEntity<Map> porCategoria = get("/api/expenses?categoryId=" + categoriaId, token);
        assertThat(((Number) porCategoria.getBody().get("totalElements")).intValue()).isEqualTo(3);

        ResponseEntity<Map> porTexto = get("/api/expenses?q=almuerzo", token);
        assertThat(((Number) porTexto.getBody().get("totalElements")).intValue()).isEqualTo(1);

        ResponseEntity<Map> paginaGrande = get("/api/expenses?size=9999", token);
        assertThat(((Number) paginaGrande.getBody().get("size")).intValue()).isEqualTo(100);

        ResponseEntity<String> csv = restTemplate.exchange(
                "/api/expenses/export?desde=2026-02-01&hasta=2026-02-28",
                HttpMethod.GET, conToken(token), String.class);
        assertThat(csv.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(csv.getHeaders().getContentType().toString()).contains("text/csv");
        assertThat(csv.getBody()).contains("Fecha,Descripcion,Categoria,Monto");
        assertThat(csv.getBody()).contains("Almuerzo de febrero");
        assertThat(csv.getBody()).doesNotContain("Compra enero");
    }

    @Test
    void aislamientoDeUbicacionesEntreUsuarios() {
        String tokenA = registrar("a-" + UUID.randomUUID() + "@test.com", "PEN");
        String tokenB = registrar("b-" + UUID.randomUUID() + "@test.com", "PEN");

        Map<String, Object> ubicacion = new HashMap<>();
        ubicacion.put("direccion", "Casa A");
        ubicacion.put("latitud", -12.05);
        ubicacion.put("longitud", -77.04);
        ubicacion.put("ciudad", "Lima");
        ubicacion.put("pais", "Peru");

        ResponseEntity<Map> creada = restTemplate.exchange(
                "/api/locations", HttpMethod.POST, conToken(tokenA, ubicacion), Map.class);
        assertThat(creada.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        long ubicacionId = ((Number) creada.getBody().get("id")).longValue();

        assertThat(get("/api/locations/" + ubicacionId, tokenA).getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(get("/api/locations/" + ubicacionId, tokenB).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        Map<String, Object> gasto = new HashMap<>();
        gasto.put("monto", 10);
        gasto.put("fecha", LocalDate.now(ZONA).minusDays(1).toString());
        gasto.put("descripcion", "Intento");
        gasto.put("locationId", ubicacionId);

        ResponseEntity<Map> intento = restTemplate.exchange(
                "/api/expenses", HttpMethod.POST, conToken(tokenB, gasto), Map.class);
        assertThat(intento.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void eliminaUbicacionHuerfanaAlBorrarGasto() {
        String token = registrar("huerfana-" + UUID.randomUUID() + "@test.com", "PEN");

        Map<String, Object> gasto = new HashMap<>();
        gasto.put("monto", 15);
        gasto.put("fecha", LocalDate.now(ZONA).minusDays(1).toString());
        gasto.put("descripcion", "Con ubicacion");
        gasto.put("locationDireccion", "Casa");
        gasto.put("locationLatitud", -12.05);
        gasto.put("locationLongitud", -77.04);

        ResponseEntity<Map> creado = restTemplate.exchange(
                "/api/expenses/with-location", HttpMethod.POST, conToken(token, gasto), Map.class);
        assertThat(creado.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        long gastoId = ((Number) creado.getBody().get("id")).longValue();
        long ubicacionId = ((Number) creado.getBody().get("locationId")).longValue();
        assertThat(get("/api/locations/" + ubicacionId, token).getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Void> borrado = restTemplate.exchange(
                "/api/expenses/" + gastoId, HttpMethod.DELETE, conToken(token), Void.class);
        assertThat(borrado.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        assertThat(get("/api/locations/" + ubicacionId, token).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void editarGastoConservaYPermiteQuitarUbicacion() {
        String token = registrar("editar-" + UUID.randomUUID() + "@test.com", "PEN");
        Map<String, Object> creado = crearGastoConUbicacion(token, -12.05, -77.04);

        long gastoId = ((Number) creado.get("id")).longValue();
        long ubicacionId = ((Number) creado.get("locationId")).longValue();

        Map<String, Object> edicion = new HashMap<>();
        edicion.put("monto", 25.50);
        edicion.put("fecha", LocalDate.now(ZONA).minusDays(1).toString());
        edicion.put("descripcion", "Editado");
        edicion.put("locationId", ubicacionId);

        ResponseEntity<Map> actualizado = restTemplate.exchange(
                "/api/expenses/" + gastoId, HttpMethod.PUT, conToken(token, edicion), Map.class);
        assertThat(actualizado.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((Number) actualizado.getBody().get("locationId")).longValue()).isEqualTo(ubicacionId);

        edicion.remove("locationId");
        ResponseEntity<Map> sinUbicacion = restTemplate.exchange(
                "/api/expenses/" + gastoId, HttpMethod.PUT, conToken(token, edicion), Map.class);
        assertThat(sinUbicacion.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(sinUbicacion.getBody().get("locationId")).isNull();
        assertThat(get("/api/locations/" + ubicacionId, token).getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void reutilizaUbicacionesDelMismoPlaceId() {
        String token = registrar("place-" + UUID.randomUUID() + "@test.com", "PEN");
        String placeId = "place-" + UUID.randomUUID();

        Map<String, Object> primero = crearGastoConUbicacion(token, -12.10, -77.02, placeId);
        Map<String, Object> segundo = crearGastoConUbicacion(token, -12.10, -77.02, placeId);

        assertThat(((Number) primero.get("locationId")).longValue())
                .isEqualTo(((Number) segundo.get("locationId")).longValue());
    }

    @Test
    void mapaDevuelveCoordenadasYFiltra() {
        String token = registrar("mapa-" + UUID.randomUUID() + "@test.com", "PEN");
        crearGastoConUbicacion(token, -12.121, -77.030);

        ResponseEntity<List> response = restTemplate.exchange(
                "/api/expenses/map", HttpMethod.GET, conToken(token), List.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).hasSize(1);

        Map<String, Object> punto = (Map<String, Object>) response.getBody().get(0);
        assertThat(((Number) punto.get("latitud")).doubleValue()).isEqualTo(-12.121);
        assertThat(((Number) punto.get("longitud")).doubleValue()).isEqualTo(-77.030);
    }

    @Test
    void lugaresGuardadosCrudYValidacion() {
        String token = registrar("lugares-" + UUID.randomUUID() + "@test.com", "PEN");
        long locationId = crearUbicacion(token, "Casa", -12.05, -77.04);

        Map<String, Object> body = Map.of("alias", "Casa", "locationId", locationId);
        ResponseEntity<Map> creado = restTemplate.exchange(
                "/api/saved-places", HttpMethod.POST, conToken(token, body), Map.class);
        assertThat(creado.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        long placeId = ((Number) creado.getBody().get("id")).longValue();

        ResponseEntity<Map> duplicado = restTemplate.exchange(
                "/api/saved-places", HttpMethod.POST,
                conToken(token, Map.of("alias", "casa", "locationId", locationId)), Map.class);
        assertThat(duplicado.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<Map> invalido = restTemplate.exchange(
                "/api/saved-places", HttpMethod.POST,
                conToken(token, Map.of("alias", " ", "locationId", locationId)), Map.class);
        assertThat(invalido.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<List> lista = restTemplate.exchange(
                "/api/saved-places", HttpMethod.GET, conToken(token), List.class);
        assertThat(lista.getBody()).hasSize(1);

        ResponseEntity<Map> renombrado = restTemplate.exchange(
                "/api/saved-places/" + placeId, HttpMethod.PUT,
                conToken(token, Map.of("alias", "Oficina")), Map.class);
        assertThat(renombrado.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(renombrado.getBody().get("alias")).isEqualTo("Oficina");

        ResponseEntity<Void> borrado = restTemplate.exchange(
                "/api/saved-places/" + placeId, HttpMethod.DELETE, conToken(token), Void.class);
        assertThat(borrado.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
    }

    @Test
    void cambiarPasswordInvalidaLaAnterior() {
        String email = "clave-" + UUID.randomUUID() + "@test.com";
        String token = registrar(email, "PEN");

        Map<String, Object> cambio = Map.of(
                "currentPassword", "secreto123",
                "newPassword", "nuevaClave123");

        ResponseEntity<Void> response = restTemplate.exchange(
                "/api/auth/me/password", HttpMethod.PUT, conToken(token, cambio), Void.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<Map> loginNuevo = restTemplate.postForEntity(
                "/api/auth/login", Map.of("email", email, "password", "nuevaClave123"), Map.class);
        assertThat(loginNuevo.getStatusCode()).isEqualTo(HttpStatus.OK);

        ResponseEntity<Map> loginViejo = restTemplate.postForEntity(
                "/api/auth/login", Map.of("email", email, "password", "secreto123"), Map.class);
        assertThat(loginViejo.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void neutralizaFormulasEnElCsv() {
        String token = registrar("csv-" + UUID.randomUUID() + "@test.com", "PEN");
        long categoriaId = crearCategoria(token);
        crearGasto(token, LocalDate.now(ZONA).minusDays(1).toString(), 5.00, categoriaId, "=1+1");

        ResponseEntity<String> csv = restTemplate.exchange(
                "/api/expenses/export", HttpMethod.GET, conToken(token), String.class);

        assertThat(csv.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(csv.getBody()).contains("'=1+1");
    }

    @Test
    void validaDatosYProtegeCategoriasConGastos() {
        String token = registrar("validacion-" + UUID.randomUUID() + "@test.com", "PEN");
        long categoriaId = crearCategoria(token);

        crearGasto(token, LocalDate.now(ZONA).toString(), 10.00, categoriaId, "Gasto valido");

        ResponseEntity<Map> borrado = restTemplate.exchange(
                "/api/categories/" + categoriaId, HttpMethod.DELETE, conToken(token), Map.class);
        assertThat(borrado.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(borrado.getBody().get("message").toString()).contains("gastos asociados");

        Map<String, Object> futuro = new HashMap<>();
        futuro.put("monto", 10);
        futuro.put("fecha", LocalDate.now(ZONA).plusDays(1).toString());
        futuro.put("descripcion", "Futuro");
        ResponseEntity<Map> fechaFutura = restTemplate.exchange(
                "/api/expenses", HttpMethod.POST, conToken(token, futuro), Map.class);
        assertThat(fechaFutura.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        Map<String, Object> coordenadas = new HashMap<>();
        coordenadas.put("direccion", "Fuera del mundo");
        coordenadas.put("latitud", 100);
        coordenadas.put("longitud", -77);
        ResponseEntity<Map> latitudInvalida = restTemplate.exchange(
                "/api/locations", HttpMethod.POST, conToken(token, coordenadas), Map.class);
        assertThat(latitudInvalida.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void registroRechazaTimezoneInvalida() {
        Map<String, Object> body = Map.of(
                "email", "tz-" + UUID.randomUUID() + "@test.com",
                "password", "secreto123",
                "nombre", "Timezone Test",
                "timezone", "Planeta/Desconocido");

        ResponseEntity<Map> response = restTemplate.postForEntity("/api/auth/register", body, Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void estadisticasComparanMesActualConElAnterior() {
        String token = registrar("stats-" + UUID.randomUUID() + "@test.com", "PEN");
        long categoriaId = crearCategoria(token);

        LocalDate hoy = LocalDate.now(ZONA);
        LocalDate mesAnterior = YearMonth.from(hoy).minusMonths(1).atDay(10);

        crearGasto(token, hoy.toString(), 100.00, categoriaId, "Gasto actual");
        crearGasto(token, mesAnterior.toString(), 50.00, categoriaId, "Gasto anterior");

        ResponseEntity<Map> response = get("/api/stats", token);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);

        Map<String, Object> stats = response.getBody();
        assertThat(((Number) stats.get("totalMes")).doubleValue()).isEqualTo(100.00);
        assertThat(((Number) stats.get("totalMesAnterior")).doubleValue()).isEqualTo(50.00);
        assertThat(((Number) stats.get("variacionPorcentual")).doubleValue()).isEqualTo(100.0);
        assertThat(stats.get("categoriaTopNombre")).isEqualTo("Gasto Test");
        assertThat(((List) stats.get("gastosPorMes"))).hasSize(12);
    }

    private String registrar(String email, String moneda) {
        Map<String, Object> body = Map.of(
                "email", email,
                "password", "secreto123",
                "nombre", "Usuario Test",
                "moneda", moneda);

        ResponseEntity<Map> response = restTemplate.postForEntity("/api/auth/register", body, Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        return (String) response.getBody().get("token");
    }

    private long crearCategoria(String token) {
        Map<String, Object> body = Map.of(
                "nombre", "Gasto Test",
                "color", "#ff0000");
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/categories", HttpMethod.POST, conToken(token, body), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return ((Number) response.getBody().get("id")).longValue();
    }

    private long crearUbicacion(String token, String direccion, double lat, double lon) {
        Map<String, Object> body = new HashMap<>();
        body.put("direccion", direccion);
        body.put("latitud", lat);
        body.put("longitud", lon);
        body.put("ciudad", "Lima");
        body.put("pais", "Peru");

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/locations", HttpMethod.POST, conToken(token, body), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return ((Number) response.getBody().get("id")).longValue();
    }

    private Map<String, Object> crearGastoConUbicacion(String token, double lat, double lon) {
        return crearGastoConUbicacion(token, lat, lon, null);
    }

    private Map<String, Object> crearGastoConUbicacion(String token, double lat, double lon, String placeId) {
        Map<String, Object> gasto = new HashMap<>();
        gasto.put("monto", 15);
        gasto.put("fecha", LocalDate.now(ZONA).minusDays(1).toString());
        gasto.put("descripcion", "Gasto con ubicacion");
        gasto.put("locationDireccion", "Casa");
        gasto.put("locationLatitud", lat);
        gasto.put("locationLongitud", lon);
        gasto.put("locationCiudad", "Lima");
        gasto.put("locationPais", "Peru");
        if (placeId != null) {
            gasto.put("locationPlaceId", placeId);
        }

        ResponseEntity<Map> creado = restTemplate.exchange(
                "/api/expenses/with-location", HttpMethod.POST, conToken(token, gasto), Map.class);
        assertThat(creado.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return creado.getBody();
    }

    private void crearGasto(String token, String fecha, double monto, long categoriaId, String descripcion) {
        Map<String, Object> body = new HashMap<>();
        body.put("monto", monto);
        body.put("fecha", fecha);
        body.put("descripcion", descripcion);
        body.put("categoryId", categoriaId);

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/expenses", HttpMethod.POST, conToken(token, body), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    private ResponseEntity<Map> get(String url, String token) {
        return restTemplate.exchange(url, HttpMethod.GET, conToken(token), Map.class);
    }

    private HttpEntity<Void> conToken(String token) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        return new HttpEntity<>(headers);
    }

    private HttpEntity<Map<String, Object>> conToken(String token, Map<String, Object> body) {
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);
        return new HttpEntity<>(body, headers);
    }
}
