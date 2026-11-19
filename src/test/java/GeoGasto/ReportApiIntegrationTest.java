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
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings({"rawtypes", "unchecked"})
@AutoConfigureTestRestTemplate
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ReportApiIntegrationTest extends AbstractIntegrationTest {

    private static final ZoneId ZONA = ZoneId.of("America/Lima");

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void generaReportePdfMensual() {
        String token = registrar("report-" + UUID.randomUUID() + "@test.com");
        long categoriaId = crearCategoria(token);
        LocalDate hoy = LocalDate.now(ZONA);
        String mes = YearMonth.from(hoy).toString();

        crearGasto(token, hoy, 42.50, categoriaId);
        restTemplate.exchange("/api/incomes", HttpMethod.POST,
                conToken(token, Map.of("monto", 100.00, "fecha", hoy.toString(),
                        "descripcion", "Ingreso reporte", "fuente", "Salario")), Map.class);

        ResponseEntity<byte[]> response = restTemplate.exchange(
                "/api/reports/monthly?mes=" + mes, HttpMethod.GET, conToken(token), byte[].class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.APPLICATION_PDF);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().length).isGreaterThan(1000);
        assertThat(new String(response.getBody(), 0, 4)).isEqualTo("%PDF");
    }

    private void crearGasto(String token, LocalDate fecha, double monto, long categoriaId) {
        Map<String, Object> body = new HashMap<>();
        body.put("monto", monto);
        body.put("fecha", fecha.toString());
        body.put("descripcion", "Gasto reporte");
        body.put("categoryId", categoriaId);
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/expenses", HttpMethod.POST, conToken(token, body), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
    }

    private String registrar(String email) {
        Map<String, Object> body = Map.of(
                "email", email,
                "password", "secreto123",
                "nombre", "Usuario Reporte",
                "moneda", "PEN",
                "timezone", "America/Lima");

        ResponseEntity<Map> response = restTemplate.postForEntity("/api/auth/register", body, Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return (String) response.getBody().get("token");
    }

    private long crearCategoria(String token) {
        Map<String, Object> body = Map.of("nombre", "Reporte Test", "color", "#0ea5e9");
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/categories", HttpMethod.POST, conToken(token, body), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return ((Number) response.getBody().get("id")).longValue();
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
