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

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings({"rawtypes", "unchecked"})
@AutoConfigureTestRestTemplate
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ExpenseApiIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void registroPerfilYMoneda() {
        String email = "perfil-" + UUID.randomUUID() + "@test.com";
        String token = registrar(email, "PEN");

        ResponseEntity<Map> me = get("/api/auth/me", token);
        assertThat(me.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(me.getBody().get("email")).isEqualTo(email);
        assertThat(me.getBody().get("moneda")).isEqualTo("PEN");

        ResponseEntity<Map> actualizado = restTemplate.exchange(
                "/api/auth/me", HttpMethod.PUT,
                conToken(token, Map.of("moneda", "EUR")), Map.class);
        assertThat(actualizado.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(actualizado.getBody().get("moneda")).isEqualTo("EUR");

        ResponseEntity<Map> sinToken = restTemplate.getForEntity("/api/auth/me", Map.class);
        assertThat(sinToken.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void filtrosPaginacionYExportacionCsv() {
        String token = registrar("gastos-" + UUID.randomUUID() + "@test.com", "PEN");
        long categoriaId = crearCategoria(token);

        crearGasto(token, "2026-01-10", 10.50, categoriaId);
        crearGasto(token, "2026-02-10", 20.00, categoriaId);
        crearGasto(token, "2026-02-20", 30.00, categoriaId);

        ResponseEntity<Map> febrero = get("/api/expenses?desde=2026-02-01&hasta=2026-02-28", token);
        assertThat(febrero.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((Number) febrero.getBody().get("totalElements")).intValue()).isEqualTo(2);

        ResponseEntity<Map> porCategoria = get("/api/expenses?categoryId=" + categoriaId, token);
        assertThat(((Number) porCategoria.getBody().get("totalElements")).intValue()).isEqualTo(3);

        ResponseEntity<Map> paginaGrande = get("/api/expenses?size=9999", token);
        Map<String, Object> pageable = (Map<String, Object>) paginaGrande.getBody().get("pageable");
        assertThat(((Number) pageable.get("pageSize")).intValue()).isEqualTo(100);

        ResponseEntity<String> csv = restTemplate.exchange(
                "/api/expenses/export?desde=2026-02-01&hasta=2026-02-28",
                HttpMethod.GET, conToken(token), String.class);
        assertThat(csv.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(csv.getHeaders().getContentType().toString()).contains("text/csv");
        assertThat(csv.getBody()).contains("Fecha,Descripcion,Categoria,Monto");
        assertThat(csv.getBody()).contains("Gasto 2026-02-10");
        assertThat(csv.getBody()).doesNotContain("Gasto 2026-01-10");
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
        gasto.put("fecha", "2026-02-01");
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
        gasto.put("fecha", "2026-02-01");
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

    private String registrar(String email, String moneda) {
        Map<String, Object> body = Map.of(
                "email", email,
                "password", "secreto123",
                "nombre", "Usuario Test",
                "moneda", moneda);

        ResponseEntity<Map> response = restTemplate.postForEntity("/api/auth/register", body, Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        return (String) response.getBody().get("token");
    }

    private long crearCategoria(String token) {
        Map<String, Object> body = Map.of("nombre", "Comida", "color", "#ff0000");
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/categories", HttpMethod.POST, conToken(token, body), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return ((Number) response.getBody().get("id")).longValue();
    }

    private void crearGasto(String token, String fecha, double monto, long categoriaId) {
        Map<String, Object> body = new HashMap<>();
        body.put("monto", monto);
        body.put("fecha", fecha);
        body.put("descripcion", "Gasto " + fecha);
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
