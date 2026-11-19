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
class IncomeApiIntegrationTest extends AbstractIntegrationTest {

    private static final ZoneId ZONA = ZoneId.of("America/Lima");

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void crudBusquedaYPaginacionDeIngresos() {
        String token = registrar("income-" + UUID.randomUUID() + "@test.com");
        LocalDate hoy = LocalDate.now(ZONA);

        ResponseEntity<Map> creado = restTemplate.exchange(
                "/api/incomes", HttpMethod.POST,
                conToken(token, ingresoBody(500.00, hoy, "Salario de noviembre", "Salario")), Map.class);
        assertThat(creado.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        long incomeId = ((Number) creado.getBody().get("id")).longValue();

        ResponseEntity<Map> pagina = get("/api/incomes", token);
        assertThat(((Number) pagina.getBody().get("totalElements")).intValue()).isEqualTo(1);

        ResponseEntity<Map> porTexto = get("/api/incomes?q=salario", token);
        assertThat(((Number) porTexto.getBody().get("totalElements")).intValue()).isEqualTo(1);

        ResponseEntity<Map> detalle = get("/api/incomes/" + incomeId, token);
        assertThat(detalle.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((Number) detalle.getBody().get("monto")).doubleValue()).isEqualTo(500.00);

        ResponseEntity<Map> actualizado = restTemplate.exchange(
                "/api/incomes/" + incomeId, HttpMethod.PUT,
                conToken(token, ingresoBody(650.00, hoy, "Salario ajustado", "Salario")), Map.class);
        assertThat(actualizado.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((Number) actualizado.getBody().get("monto")).doubleValue()).isEqualTo(650.00);

        ResponseEntity<Map> futuro = restTemplate.exchange(
                "/api/incomes", HttpMethod.POST,
                conToken(token, ingresoBody(100.00, hoy.plusDays(1), "Futuro", "Otro")), Map.class);
        assertThat(futuro.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<Map> montoInvalido = restTemplate.exchange(
                "/api/incomes", HttpMethod.POST,
                conToken(token, ingresoBody(0.00, hoy, "Cero", "Otro")), Map.class);
        assertThat(montoInvalido.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<Void> borrado = restTemplate.exchange(
                "/api/incomes/" + incomeId, HttpMethod.DELETE, conToken(token), Void.class);
        assertThat(borrado.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        ResponseEntity<Map> vacio = get("/api/incomes", token);
        assertThat(((Number) vacio.getBody().get("totalElements")).intValue()).isEqualTo(0);
    }

    @Test
    void ingresosEstanAisladosPorUsuario() {
        String tokenA = registrar("income-a-" + UUID.randomUUID() + "@test.com");
        String tokenB = registrar("income-b-" + UUID.randomUUID() + "@test.com");
        LocalDate hoy = LocalDate.now(ZONA);

        ResponseEntity<Map> creado = restTemplate.exchange(
                "/api/incomes", HttpMethod.POST,
                conToken(tokenA, ingresoBody(300.00, hoy, "Freelance", "Freelance")), Map.class);
        long incomeId = ((Number) creado.getBody().get("id")).longValue();

        ResponseEntity<Map> paginaB = get("/api/incomes", tokenB);
        assertThat(((Number) paginaB.getBody().get("totalElements")).intValue()).isEqualTo(0);

        assertThat(get("/api/incomes/" + incomeId, tokenB).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);

        ResponseEntity<Void> borradoAjeno = restTemplate.exchange(
                "/api/incomes/" + incomeId, HttpMethod.DELETE, conToken(tokenB), Void.class);
        assertThat(borradoAjeno.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        ResponseEntity<Map> paginaA = get("/api/incomes", tokenA);
        assertThat(((Number) paginaA.getBody().get("totalElements")).intValue()).isEqualTo(1);
    }

    @Test
    void estadisticasCalculanBalanceYTasaDeAhorro() {
        String token = registrar("income-stats-" + UUID.randomUUID() + "@test.com");
        long categoriaId = crearCategoria(token);

        LocalDate hoy = LocalDate.now(ZONA);
        YearMonth mesActual = YearMonth.from(hoy);
        LocalDate mesAnterior = mesActual.minusMonths(1).atDay(10);

        restTemplate.exchange("/api/incomes", HttpMethod.POST,
                conToken(token, ingresoBody(500.00, hoy, "Ingreso actual", "Salario")), Map.class);
        restTemplate.exchange("/api/incomes", HttpMethod.POST,
                conToken(token, ingresoBody(100.00, mesAnterior, "Ingreso anterior", "Extra")), Map.class);

        Map<String, Object> gasto = new HashMap<>();
        gasto.put("monto", 100.00);
        gasto.put("fecha", hoy.toString());
        gasto.put("descripcion", "Gasto del mes");
        gasto.put("categoryId", categoriaId);
        restTemplate.exchange("/api/expenses", HttpMethod.POST, conToken(token, gasto), Map.class);

        ResponseEntity<Map> response = get("/api/stats", token);
        Map<String, Object> stats = response.getBody();

        assertThat(((Number) stats.get("totalIngresosMes")).doubleValue()).isEqualTo(500.00);
        assertThat(((Number) stats.get("totalIngresosMesAnterior")).doubleValue()).isEqualTo(100.00);
        assertThat(((Number) stats.get("balanceMes")).doubleValue()).isEqualTo(400.00);
        assertThat(((Number) stats.get("tasaAhorro")).doubleValue()).isEqualTo(80.0);

        List<Map<String, Object>> meses = (List<Map<String, Object>>) stats.get("gastosPorMes");
        Map<String, Object> mesActualStat = meses.stream()
                .filter(m -> mesActual.toString().equals(m.get("mes")))
                .findFirst()
                .orElseThrow();
        assertThat(((Number) mesActualStat.get("total")).doubleValue()).isEqualTo(100.00);
        assertThat(((Number) mesActualStat.get("ingresos")).doubleValue()).isEqualTo(500.00);
    }

    private String registrar(String email) {
        Map<String, Object> body = Map.of(
                "email", email,
                "password", "secreto123",
                "nombre", "Usuario Ingreso",
                "moneda", "PEN",
                "timezone", "America/Lima");

        ResponseEntity<Map> response = restTemplate.postForEntity("/api/auth/register", body, Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return (String) response.getBody().get("token");
    }

    private long crearCategoria(String token) {
        Map<String, Object> body = Map.of("nombre", "Ingreso Test", "color", "#22c55e");
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/categories", HttpMethod.POST, conToken(token, body), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return ((Number) response.getBody().get("id")).longValue();
    }

    private Map<String, Object> ingresoBody(double monto, LocalDate fecha, String descripcion, String fuente) {
        Map<String, Object> body = new HashMap<>();
        body.put("monto", monto);
        body.put("fecha", fecha.toString());
        body.put("descripcion", descripcion);
        body.put("fuente", fuente);
        return body;
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
