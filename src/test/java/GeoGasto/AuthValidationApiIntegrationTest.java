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
class AuthValidationApiIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void registroRechazaEmailInvalido() {
        ResponseEntity<Map> response = registrar("no-es-un-email", "secreto123", "Usuario");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(errores(response)).containsKey("email");
    }

    @Test
    void registroRechazaPasswordCorta() {
        String email = "corta-" + UUID.randomUUID() + "@test.com";

        ResponseEntity<Map> response = registrar(email, "1234567", "Usuario");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(errores(response)).containsKey("password");
    }

    @Test
    void registroRechazaNombreVacio() {
        String email = "vacio-" + UUID.randomUUID() + "@test.com";

        ResponseEntity<Map> response = registrar(email, "secreto123", "   ");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(errores(response)).containsKey("nombre");
    }

    @Test
    void registroRechazaMonedaInvalida() {
        Map<String, Object> body = cuerpoRegistro(
                "moneda-" + UUID.randomUUID() + "@test.com", "secreto123", "Usuario");
        body.put("moneda", "PESOS");

        ResponseEntity<Map> response = restTemplate.postForEntity("/api/auth/register", body, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(errores(response)).containsKey("moneda");
    }

    @Test
    void loginRechazaPasswordIncorrecta() {
        String email = "login-" + UUID.randomUUID() + "@test.com";
        ResponseEntity<Map> registro = restTemplate.postForEntity(
                "/api/auth/register", cuerpoRegistro(email, "secreto123", "Usuario"), Map.class);
        assertThat(registro.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        Map<String, Object> login = Map.of("email", email, "password", "incorrecta123");
        ResponseEntity<Map> response = restTemplate.postForEntity("/api/auth/login", login, Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
    }

    @Test
    void cambioPasswordRechazaPasswordCorta() {
        String email = "cambio-" + UUID.randomUUID() + "@test.com";
        ResponseEntity<Map> registro = restTemplate.postForEntity(
                "/api/auth/register", cuerpoRegistro(email, "secreto123", "Usuario"), Map.class);
        assertThat(registro.getStatusCode()).isEqualTo(HttpStatus.CREATED);

        String token = (String) registro.getBody().get("token");
        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.APPLICATION_JSON);

        Map<String, Object> body = Map.of(
                "currentPassword", "secreto123",
                "newPassword", "corta");

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/auth/me/password", HttpMethod.PUT,
                new HttpEntity<>(body, headers), Map.class);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
        assertThat(errores(response)).containsKey("newPassword");
    }

    private ResponseEntity<Map> registrar(String email, String password, String nombre) {
        return restTemplate.postForEntity(
                "/api/auth/register", cuerpoRegistro(email, password, nombre), Map.class);
    }

    private Map<String, Object> cuerpoRegistro(String email, String password, String nombre) {
        Map<String, Object> body = new HashMap<>();
        body.put("email", email);
        body.put("password", password);
        body.put("nombre", nombre);
        body.put("moneda", "PEN");
        body.put("timezone", "America/Lima");
        return body;
    }

    private Map<String, String> errores(ResponseEntity<Map> response) {
        assertThat(response.getBody()).isNotNull();
        return (Map<String, String>) response.getBody().get("errors");
    }
}
