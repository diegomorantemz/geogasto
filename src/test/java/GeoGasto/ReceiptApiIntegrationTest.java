package GeoGasto;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.resttestclient.TestRestTemplate;
import org.springframework.boot.resttestclient.autoconfigure.AutoConfigureTestRestTemplate;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings({"rawtypes", "unchecked"})
@AutoConfigureTestRestTemplate
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ReceiptApiIntegrationTest extends AbstractIntegrationTest {

    private static final ZoneId ZONA = ZoneId.of("America/Lima");
    private static final byte[] PNG = {
            (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0, 0, 0, 0
    };

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void subeConsultaYEliminaComprobante() {
        String email = "receipt-" + UUID.randomUUID() + "@test.com";
        String token = registrar(email);
        long expenseId = crearGasto(token);

        ResponseEntity<Map> subido = subirComprobante(token, expenseId, "recibo.png", "image/png", PNG);
        assertThat(subido.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(subido.getBody().get("nombreOriginal")).isEqualTo("recibo.png");
        assertThat(subido.getBody().get("contentType")).isEqualTo("image/png");

        ResponseEntity<Map> gasto = restTemplate.exchange(
                "/api/expenses/" + expenseId, HttpMethod.GET, conToken(token), Map.class);
        assertThat(((Number) gasto.getBody().get("receiptId")).longValue())
                .isEqualTo(((Number) subido.getBody().get("id")).longValue());
        assertThat(gasto.getBody().get("receiptNombre")).isEqualTo("recibo.png");

        ResponseEntity<byte[]> descarga = restTemplate.exchange(
                "/api/expenses/" + expenseId + "/receipt", HttpMethod.GET, conToken(token), byte[].class);
        assertThat(descarga.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(descarga.getHeaders().getContentType()).isEqualTo(MediaType.IMAGE_PNG);
        assertThat(descarga.getBody()).isEqualTo(PNG);

        ResponseEntity<Void> borrado = restTemplate.exchange(
                "/api/expenses/" + expenseId + "/receipt", HttpMethod.DELETE, conToken(token), Void.class);
        assertThat(borrado.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        assertThat(restTemplate.exchange("/api/expenses/" + expenseId + "/receipt",
                HttpMethod.GET, conToken(token), byte[].class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);

        ResponseEntity<Map> sinComprobante = restTemplate.exchange(
                "/api/expenses/" + expenseId, HttpMethod.GET, conToken(token), Map.class);
        assertThat(sinComprobante.getBody().get("receiptId")).isNull();
    }

    @Test
    void validaTipoYContenidoDelComprobante() {
        String token = registrar("receipt-val-" + UUID.randomUUID() + "@test.com");
        long expenseId = crearGasto(token);

        ResponseEntity<Map> texto = subirComprobante(
                token, expenseId, "recibo.txt", "text/plain", "hola".getBytes());
        assertThat(texto.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);

        ResponseEntity<Map> firmaIncorrecta = subirComprobante(
                token, expenseId, "falso.png", "image/png", "no es un png".getBytes());
        assertThat(firmaIncorrecta.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
    }

    @Test
    void comprobantesEstanAisladosPorUsuario() {
        String tokenA = registrar("receipt-a-" + UUID.randomUUID() + "@test.com");
        String tokenB = registrar("receipt-b-" + UUID.randomUUID() + "@test.com");
        long expenseA = crearGasto(tokenA);
        subirComprobante(tokenA, expenseA, "recibo.png", "image/png", PNG);

        assertThat(restTemplate.exchange("/api/expenses/" + expenseA + "/receipt",
                HttpMethod.GET, conToken(tokenB), byte[].class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);

        ResponseEntity<Map> intentoAjeno = subirComprobante(
                tokenB, expenseA, "otro.png", "image/png", PNG);
        assertThat(intentoAjeno.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
    }

    @Test
    void eliminarGastoEliminaSuComprobante() {
        String token = registrar("receipt-expense-" + UUID.randomUUID() + "@test.com");
        long expenseId = crearGasto(token);
        subirComprobante(token, expenseId, "recibo.png", "image/png", PNG);

        ResponseEntity<Void> borrado = restTemplate.exchange(
                "/api/expenses/" + expenseId, HttpMethod.DELETE, conToken(token), Void.class);
        assertThat(borrado.getStatusCode()).isEqualTo(HttpStatus.NO_CONTENT);

        assertThat(restTemplate.exchange("/api/expenses/" + expenseId + "/receipt",
                HttpMethod.GET, conToken(token), byte[].class).getStatusCode())
                .isEqualTo(HttpStatus.NOT_FOUND);
    }

    private ResponseEntity<Map> subirComprobante(String token, long expenseId, String filename,
                                                 String contentType, byte[] bytes) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(bytes) {
            @Override
            public String getFilename() {
                return filename;
            }
        });

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        return restTemplate.exchange(
                "/api/expenses/" + expenseId + "/receipt",
                HttpMethod.POST,
                new HttpEntity<>(body, headers),
                Map.class);
    }

    private long crearGasto(String token) {
        Map<String, Object> body = new HashMap<>();
        body.put("monto", 25.50);
        body.put("fecha", LocalDate.now(ZONA).toString());
        body.put("descripcion", "Gasto con comprobante");

        ResponseEntity<Map> response = restTemplate.exchange(
                "/api/expenses", HttpMethod.POST, conToken(token, body), Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return ((Number) response.getBody().get("id")).longValue();
    }

    private String registrar(String email) {
        Map<String, Object> body = Map.of(
                "email", email,
                "password", "secreto123",
                "nombre", "Usuario Comprobante",
                "moneda", "PEN",
                "timezone", "America/Lima");

        ResponseEntity<Map> response = restTemplate.postForEntity("/api/auth/register", body, Map.class);
        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        return (String) response.getBody().get("token");
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
