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

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SuppressWarnings({"rawtypes", "unchecked"})
@AutoConfigureTestRestTemplate
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ExpenseImportApiIntegrationTest extends AbstractIntegrationTest {

    @Autowired
    private TestRestTemplate restTemplate;

    @Test
    void previsualizaEImportaCsvSinDuplicados() {
        String token = registrar("import-" + UUID.randomUUID() + "@test.com");
        String csv = """
                fecha,monto,descripcion,categoria
                17/11/2026,25.50,Almuerzo,Comida
                2026-11-17,10.00,Taxi,Transporte
                17/11/2026,25.50,Almuerzo,Comida
                17/11/2026,abc,Inválido,Comida
                """;

        ResponseEntity<Map> preview = multipart("/api/expenses/import/preview", token, csv);
        assertThat(preview.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((Number) preview.getBody().get("total")).intValue()).isEqualTo(4);
        assertThat(((Number) preview.getBody().get("validas")).intValue()).isEqualTo(2);
        assertThat(((Number) preview.getBody().get("duplicadas")).intValue()).isEqualTo(1);
        assertThat(((Number) preview.getBody().get("errores")).intValue()).isEqualTo(1);

        ResponseEntity<Map> importado = multipart("/api/expenses/import", token, csv);
        assertThat(importado.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(((Number) importado.getBody().get("importados")).intValue()).isEqualTo(2);
        assertThat(((Number) importado.getBody().get("errores")).intValue()).isEqualTo(1);

        ResponseEntity<Map> segunda = multipart("/api/expenses/import", token, csv);
        assertThat(((Number) segunda.getBody().get("importados")).intValue()).isEqualTo(0);

        ResponseEntity<Map> gastos = restTemplate.exchange(
                "/api/expenses?size=100", HttpMethod.GET, conToken(token), Map.class);
        assertThat(((Number) gastos.getBody().get("totalElements")).intValue()).isEqualTo(2);
    }

    private ResponseEntity<Map> multipart(String url, String token, String csv) {
        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource(csv.getBytes(StandardCharsets.UTF_8)) {
            @Override
            public String getFilename() {
                return "extracto.csv";
            }
        });

        HttpHeaders headers = new HttpHeaders();
        headers.setBearerAuth(token);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        return restTemplate.exchange(url, HttpMethod.POST, new HttpEntity<>(body, headers), Map.class);
    }

    private String registrar(String email) {
        Map<String, Object> body = Map.of(
                "email", email,
                "password", "secreto123",
                "nombre", "Usuario Import",
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
}
