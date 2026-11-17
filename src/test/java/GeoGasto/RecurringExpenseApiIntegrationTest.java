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
class RecurringExpenseApiIntegrationTest extends AbstractIntegrationTest {

    private static final ZoneId ZONA = ZoneId.of("America/Lima");

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void creaRecurrenteMensualGeneraGastoYNoDuplica() {
        String token = registrar("recurring-" + UUID.randomUUID() + "@test.com");
        long categoriaId = crearCategoria(token);
        LocalDate hoy = LocalDate.now(ZONA);

        Map<String, Object> body = recurrenteBody("Alquiler", 100.00, hoy, "MONTHLY", categoriaId);
        ResponseEntity<Map> creado = restTemplate.exchange(
                "/api/recurring-expenses", HttpMethod.POST, conToken(token, body), Map.class);
        assertThat(creado.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        long recurringId = ((Number) creado.getBody().get("id")).longValue();
        assertThat(creado.getBody().get("proximaFecha")).isEqualTo(hoy.plusMonths(1).toString());
        assertThat(creado.getBody().get("activo")).isEqualTo(true);

        assertThat(contarGastos(token, "Alquiler")).isEqualTo(1);

        ResponseEntity<Map> generacion = restTemplate.exchange(
                "/api/recurring-expenses/generate", HttpMethod.POST, conToken(token), Map.class);
        assertThat(generacion.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((Number) generacion.getBody().get("generados")).intValue()).isEqualTo(0);
        assertThat(contarGastos(token, "Alquiler")).isEqualTo(1);

        Map<String, Object> edicion = recurrenteBody("Alquiler", 120.00, hoy, "MONTHLY", categoriaId);
        ResponseEntity<Map> actualizado = restTemplate.exchange(
                "/api/recurring-expenses/" + recurringId, HttpMethod.PUT,
                conToken(token, edicion), Map.class);
        assertThat(actualizado.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((Number) actualizado.getBody().get("monto")).doubleValue()).isEqualTo(120.00);

        ResponseEntity<Void> borrado = restTemplate.exchange(
                "/api/recurring-expenses/" + recurringId, HttpMethod.DELETE, conToken(token), Void.class);
        assertThat(borrado.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);
        assertThat(contarGastos(token, "Alquiler")).isEqualTo(1);
    }

    @Test
    void recurrenteFuturoEsperaSuFecha() {
        String token = registrar("recurring-future-" + UUID.randomUUID() + "@test.com");
        long categoriaId = crearCategoria(token);
        LocalDate hoy = LocalDate.now(ZONA);
        LocalDate inicio = hoy.plusDays(10);

        Map<String, Object> body = recurrenteBody("Seguro futuro", 75.00, inicio, "MONTHLY", categoriaId);
        ResponseEntity<Map> creado = restTemplate.exchange(
                "/api/recurring-expenses", HttpMethod.POST, conToken(token, body), Map.class);
        assertThat(creado.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(creado.getBody().get("proximaFecha")).isEqualTo(inicio.toString());

        assertThat(contarGastos(token, "Seguro futuro")).isEqualTo(0);

        ResponseEntity<Map> generacion = restTemplate.exchange(
                "/api/recurring-expenses/generate", HttpMethod.POST, conToken(token), Map.class);
        assertThat(((Number) generacion.getBody().get("generados")).intValue()).isEqualTo(0);
    }

    @Test
    void recurrenteGeneraPeriodosVencidosAlCrearse() {
        String token = registrar("recurring-catchup-" + UUID.randomUUID() + "@test.com");
        long categoriaId = crearCategoria(token);
        LocalDate hoy = LocalDate.now(ZONA);
        LocalDate inicio = hoy.minusMonths(2);

        Map<String, Object> body = recurrenteBody("Suscripción", 20.00, inicio, "MONTHLY", categoriaId);
        ResponseEntity<Map> creado = restTemplate.exchange(
                "/api/recurring-expenses", HttpMethod.POST, conToken(token, body), Map.class);
        assertThat(creado.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        assertThat(contarGastos(token, "Suscripción")).isEqualTo(3);
        assertThat(creado.getBody().get("proximaFecha")).isEqualTo(hoy.plusMonths(1).toString());
    }

    @Test
    void recurrentesEstanAisladosPorUsuario() {
        String tokenA = registrar("recurring-a-" + UUID.randomUUID() + "@test.com");
        String tokenB = registrar("recurring-b-" + UUID.randomUUID() + "@test.com");
        long categoriaA = crearCategoria(tokenA);
        LocalDate hoy = LocalDate.now(ZONA);

        ResponseEntity<Map> creado = restTemplate.exchange(
                "/api/recurring-expenses", HttpMethod.POST,
                conToken(tokenA, recurrenteBody("Internet", 80.00, hoy, "MONTHLY", categoriaA)), Map.class);
        long recurringId = ((Number) creado.getBody().get("id")).longValue();

        ResponseEntity<List> listaB = restTemplate.exchange(
                "/api/recurring-expenses", HttpMethod.GET, conToken(tokenB), List.class);
        assertThat(listaB.getBody()).isEmpty();

        ResponseEntity<Void> borradoAjeno = restTemplate.exchange(
                "/api/recurring-expenses/" + recurringId, HttpMethod.DELETE, conToken(tokenB), Void.class);
        assertThat(borradoAjeno.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        ResponseEntity<Map> generacionB = restTemplate.exchange(
                "/api/recurring-expenses/generate", HttpMethod.POST, conToken(tokenB), Map.class);
        assertThat(((Number) generacionB.getBody().get("generados")).intValue()).isEqualTo(0);

        ResponseEntity<List> listaA = restTemplate.exchange(
                "/api/recurring-expenses", HttpMethod.GET, conToken(tokenA), List.class);
        assertThat(listaA.getBody()).hasSize(1);
    }

    @Test
    void validaFechasDeLaRecurrencia() {
        String token = registrar("recurring-val-" + UUID.randomUUID() + "@test.com");
        long categoriaId = crearCategoria(token);
        LocalDate hoy = LocalDate.now(ZONA);

        Map<String, Object> body = recurrenteBody("Inválido", 10.00, hoy, "MONTHLY", categoriaId);
        body.put("fechaFin", hoy.minusDays(1).toString());

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/recurring-expenses", HttpMethod.POST, conToken(token, body), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    private int contarGastos(String token, String descripcion) {
        ResponseEntity<Map> page = restTemplate.exchange(
                "/api/expenses?q=" + descripcion.replace(" ", "%20") + "&size=100",
                HttpMethod.GET, conToken(token), Map.class);
        assertThat(page.getStatusCode()).isEqualTo(HttpStatus.OK);
        return ((Number) page.getBody().get("totalElements")).intValue();
    }

    private Map<String, Object> recurrenteBody(String descripcion, double monto, LocalDate inicio,
                                               String frecuencia, long categoriaId) {
        Map<String, Object> body = new HashMap<>();
        body.put("descripcion", descripcion);
        body.put("monto", monto);
        body.put("fechaInicio", inicio.toString());
        body.put("frecuencia", frecuencia);
        body.put("categoryId", categoriaId);
        return body;
    }

    private String registrar(String email) {
        Map<String, Object> body = Map.of(
                "email", email,
                "password", "secreto123",
                "nombre", "Usuario Recurrente",
                "moneda", "PEN",
                "timezone", "America/Lima");

        ResponseEntity<Map> response = restTemplate.postForEntity("/api/auth/register", body, Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return (String) response.getBody().get("token");
    }

    private long crearCategoria(String token) {
        Map<String, Object> body = Map.of("nombre", "Recurrente Test", "color", "#f97316");
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
