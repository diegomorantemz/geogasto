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
class BudgetApiIntegrationTest extends AbstractIntegrationTest {

    private static final ZoneId ZONA = ZoneId.of("America/Lima");

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void creaPresupuestoCalculaAlertasYActualizaSinDuplicar() {
        String token = registrar("budget-" + UUID.randomUUID() + "@test.com");
        long categoriaId = crearCategoria(token);
        String mes = YearMonth.now(ZONA).toString();
        LocalDate hoy = LocalDate.now(ZONA);

        ResponseEntity<Map> creado = guardarPresupuesto(token, categoriaId, mes, 100.00);
        assertThat(creado.getStatusCode()).isEqualTo(HttpStatus.OK);
        long budgetId = ((Number) creado.getBody().get("id")).longValue();

        crearGasto(token, hoy.toString(), 30.00, categoriaId, "Gasto presupuesto 1");

        Map resumen = getResumen(token, mes);
        assertThat(((Number) resumen.get("totalPresupuesto")).doubleValue()).isEqualTo(100.00);
        assertThat(((Number) resumen.get("totalGastado")).doubleValue()).isEqualTo(30.00);
        assertThat(((Number) resumen.get("totalDisponible")).doubleValue()).isEqualTo(70.00);

        List<Map<String, Object>> presupuestos = (List<Map<String, Object>>) resumen.get("presupuestos");
        assertThat(presupuestos).hasSize(1);
        assertThat(presupuestos.get(0).get("estado")).isEqualTo("OK");
        assertThat(((Number) presupuestos.get(0).get("porcentaje")).doubleValue()).isEqualTo(30.0);

        crearGasto(token, hoy.toString(), 55.00, categoriaId, "Gasto presupuesto 2");
        resumen = getResumen(token, mes);
        presupuestos = (List<Map<String, Object>>) resumen.get("presupuestos");
        assertThat(presupuestos.get(0).get("estado")).isEqualTo("ALERTA");
        assertThat(((Number) presupuestos.get(0).get("porcentaje")).doubleValue()).isEqualTo(85.0);

        // Mismo mes/categoría: actualiza, no duplica.
        guardarPresupuesto(token, categoriaId, mes, 80.00);
        resumen = getResumen(token, mes);
        presupuestos = (List<Map<String, Object>>) resumen.get("presupuestos");
        assertThat(presupuestos).hasSize(1);
        assertThat(((Number) presupuestos.get(0).get("monto")).doubleValue()).isEqualTo(80.00);
        assertThat(presupuestos.get(0).get("estado")).isEqualTo("EXCEDIDO");
        assertThat(((Number) presupuestos.get(0).get("disponible")).doubleValue()).isEqualTo(-5.00);

        ResponseEntity<Void> borrado = restTemplate.exchange(
                "/api/budgets/" + budgetId, HttpMethod.DELETE, conToken(token), Void.class);
        assertThat(borrado.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        resumen = getResumen(token, mes);
        assertThat((List<?>) resumen.get("presupuestos")).isEmpty();
        assertThat(((Number) resumen.get("totalPresupuesto")).doubleValue()).isEqualTo(0.0);
    }

    @Test
    void presupuestosEstanAisladosPorUsuario() {
        String tokenA = registrar("budget-a-" + UUID.randomUUID() + "@test.com");
        String tokenB = registrar("budget-b-" + UUID.randomUUID() + "@test.com");
        long categoriaA = crearCategoria(tokenA);
        String mes = YearMonth.now(ZONA).toString();

        ResponseEntity<Map> creado = guardarPresupuesto(tokenA, categoriaA, mes, 50.00);
        long budgetId = ((Number) creado.getBody().get("id")).longValue();

        Map resumenB = getResumen(tokenB, mes);
        assertThat((List<?>) resumenB.get("presupuestos")).isEmpty();

        ResponseEntity<Void> borradoAjeno = restTemplate.exchange(
                "/api/budgets/" + budgetId, HttpMethod.DELETE, conToken(tokenB), Void.class);
        assertThat(borradoAjeno.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);

        Map resumenA = getResumen(tokenA, mes);
        assertThat((List<?>) resumenA.get("presupuestos")).hasSize(1);
    }

    @Test
    void validaMesMontoYCategoriaDelPresupuesto() {
        String token = registrar("budget-val-" + UUID.randomUUID() + "@test.com");
        long categoriaId = crearCategoria(token);

        Map<String, Object> mesInvalido = Map.of(
                "categoryId", categoriaId, "mes", "2026-13", "monto", 100);
        ResponseEntity<Map> respuestaMes = restTemplate.exchange(
                "/api/budgets", HttpMethod.POST, conToken(token, mesInvalido), Map.class);
        assertThat(respuestaMes.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        Map<String, Object> montoInvalido = Map.of(
                "categoryId", categoriaId, "mes", YearMonth.now(ZONA).toString(), "monto", 0);
        ResponseEntity<Map> respuestaMonto = restTemplate.exchange(
                "/api/budgets", HttpMethod.POST, conToken(token, montoInvalido), Map.class);
        assertThat(respuestaMonto.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        String tokenOtro = registrar("budget-otro-" + UUID.randomUUID() + "@test.com");
        Map<String, Object> categoriaAjena = Map.of(
                "categoryId", categoriaId, "mes", YearMonth.now(ZONA).toString(), "monto", 10);
        ResponseEntity<Map> respuestaCategoria = restTemplate.exchange(
                "/api/budgets", HttpMethod.POST, conToken(tokenOtro, categoriaAjena), Map.class);
        assertThat(respuestaCategoria.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    private String registrar(String email) {
        Map<String, Object> body = Map.of(
                "email", email,
                "password", "secreto123",
                "nombre", "Usuario Presupuesto",
                "moneda", "PEN",
                "timezone", "America/Lima");

        ResponseEntity<Map> response = restTemplate.postForEntity("/api/auth/register", body, Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return (String) response.getBody().get("token");
    }

    private long crearCategoria(String token) {
        Map<String, Object> body = Map.of(
                "nombre", "Presupuesto Test",
                "color", "#0ea5e9");
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/categories", HttpMethod.POST, conToken(token, body), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return ((Number) response.getBody().get("id")).longValue();
    }

    private ResponseEntity<Map> guardarPresupuesto(String token, long categoriaId, String mes, double monto) {
        Map<String, Object> body = Map.of(
                "categoryId", categoriaId,
                "mes", mes,
                "monto", monto);
        return restTemplate.exchange(
                "/api/budgets", HttpMethod.POST, conToken(token, body), Map.class);
    }

    private Map getResumen(String token, String mes) {
        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/budgets?mes=" + mes, HttpMethod.GET, conToken(token), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        return response.getBody();
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
