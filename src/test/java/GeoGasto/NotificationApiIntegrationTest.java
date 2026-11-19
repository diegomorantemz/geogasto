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
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings({"rawtypes", "unchecked"})
@AutoConfigureTestRestTemplate
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class NotificationApiIntegrationTest extends AbstractIntegrationTest {

    private static final ZoneId ZONA = ZoneId.of("America/Lima");

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void generaAlertasDePresupuestoYLasMarcaComoLeidas() {
        String token = registrar("notif-" + UUID.randomUUID() + "@test.com");
        long categoriaId = crearCategoria(token);
        LocalDate hoy = LocalDate.now(ZONA);
        String mes = hoy.toString().substring(0, 7);

        restTemplate.exchange("/api/budgets", HttpMethod.POST,
                conToken(token, Map.of("categoryId", categoriaId, "mes", mes, "monto", 100)), Map.class);
        crearGasto(token, hoy, 85.00, categoriaId);

        ResponseEntity<Map> primera = get("/api/notifications", token);
        assertThat(primera.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((Number) primera.getBody().get("unread")).intValue()).isGreaterThanOrEqualTo(1);
        List<Map<String, Object>> items = (List<Map<String, Object>>) primera.getBody().get("notifications");
        assertThat(items).anyMatch(n -> "PRESUPUESTO".equals(n.get("tipo")));

        // Abrir de nuevo no duplica.
        int cantidad = items.size();
        ResponseEntity<Map> segunda = get("/api/notifications", token);
        assertThat(((List) segunda.getBody().get("notifications"))).hasSize(cantidad);

        ResponseEntity<Void> todas = restTemplate.exchange(
                "/api/notifications/read-all", HttpMethod.POST, conToken(token), Void.class);
        assertThat(todas.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<Map> tercera = get("/api/notifications", token);
        assertThat(((Number) tercera.getBody().get("unread")).intValue()).isEqualTo(0);
    }

    @Test
    void generaRecordatorioDeRecurrenteProximo() {
        String token = registrar("notif-rec-" + UUID.randomUUID() + "@test.com");
        long categoriaId = crearCategoria(token);
        LocalDate inicio = LocalDate.now(ZONA).plusDays(1);

        Map<String, Object> body = new HashMap<>();
        body.put("monto", 50.00);
        body.put("fechaInicio", inicio.toString());
        body.put("frecuencia", "MONTHLY");
        body.put("descripcion", "Netflix recordatorio");
        body.put("categoryId", categoriaId);
        restTemplate.exchange("/api/recurring-expenses", HttpMethod.POST, conToken(token, body), Map.class);

        ResponseEntity<Map> response = get("/api/notifications", token);
        List<Map<String, Object>> items = (List<Map<String, Object>>) response.getBody().get("notifications");
        assertThat(items).anyMatch(n -> "RECURRENTE".equals(n.get("tipo")));
    }

    @Test
    void notificacionesEstanAisladasPorUsuario() {
        String tokenA = registrar("notif-a-" + UUID.randomUUID() + "@test.com");
        String tokenB = registrar("notif-b-" + UUID.randomUUID() + "@test.com");
        long categoriaId = crearCategoria(tokenA);
        LocalDate hoy = LocalDate.now(ZONA);
        String mes = hoy.toString().substring(0, 7);

        restTemplate.exchange("/api/budgets", HttpMethod.POST,
                conToken(tokenA, Map.of("categoryId", categoriaId, "mes", mes, "monto", 100)), Map.class);
        crearGasto(tokenA, hoy, 90.00, categoriaId);
        get("/api/notifications", tokenA);

        ResponseEntity<Map> responseB = get("/api/notifications", tokenB);
        assertThat((List) responseB.getBody().get("notifications")).isEmpty();
        assertThat(((Number) responseB.getBody().get("unread")).intValue()).isEqualTo(0);
    }

    private void crearGasto(String token, LocalDate fecha, double monto, long categoriaId) {
        Map<String, Object> body = new HashMap<>();
        body.put("monto", monto);
        body.put("fecha", fecha.toString());
        body.put("descripcion", "Gasto notificación");
        body.put("categoryId", categoriaId);
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/expenses", HttpMethod.POST, conToken(token, body), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    private String registrar(String email) {
        Map<String, Object> body = Map.of(
                "email", email,
                "password", "secreto123",
                "nombre", "Usuario Notificación",
                "moneda", "PEN",
                "timezone", "America/Lima");

        ResponseEntity<Map> response = restTemplate.postForEntity("/api/auth/register", body, Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return (String) response.getBody().get("token");
    }

    private long crearCategoria(String token) {
        Map<String, Object> body = Map.of("nombre", "Notificación Test", "color", "#0ea5e9");
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/categories", HttpMethod.POST, conToken(token, body), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return ((Number) response.getBody().get("id")).longValue();
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
