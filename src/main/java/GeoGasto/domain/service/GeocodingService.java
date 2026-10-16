package GeoGasto.domain.service;

import GeoGasto.domain.dto.geocode.GeocodeResult;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class GeocodingService {

    @Value("${nominatim.base-url}")
    private String baseUrl;

    @Value("${nominatim.user-agent}")
    private String userAgent;

    private final JsonMapper jsonMapper;

    private RestClient client() {
        return RestClient.builder()
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.USER_AGENT, userAgent)
                .build();
    }

    public List<GeocodeResult> search(String query) {
        try {
            String body = client().get()
                    .uri(uri -> uri.path("/search")
                            .queryParam("q", query)
                            .queryParam("format", "jsonv2")
                            .queryParam("addressdetails", "1")
                            .queryParam("limit", "5")
                            .build())
                    .retrieve()
                    .body(String.class);

            JsonNode root = jsonMapper.readTree(body);
            List<GeocodeResult> results = new ArrayList<>();
            if (root.isArray()) {
                for (JsonNode node : root) {
                    results.add(toResult(node));
                }
            }
            return results;
        } catch (Exception e) {
            log.error("Error llamando a Nominatim /search: {}", e.getMessage());
            throw new RuntimeException("Error consultando el servicio de geocodificación");
        }
    }

    public GeocodeResult reverse(Double lat, Double lon) {
        try {
            String body = client().get()
                    .uri(uri -> uri.path("/reverse")
                            .queryParam("lat", lat)
                            .queryParam("lon", lon)
                            .queryParam("format", "jsonv2")
                            .queryParam("addressdetails", "1")
                            .build())
                    .retrieve()
                    .body(String.class);

            JsonNode root = jsonMapper.readTree(body);
            return toResult(root);
        } catch (Exception e) {
            log.error("Error llamando a Nominatim /reverse: {}", e.getMessage());
            throw new RuntimeException("Error consultando el servicio de geocodificación");
        }
    }

    private GeocodeResult toResult(JsonNode node) {
        String displayName = node.path("display_name").asString();
        Double lat = node.has("lat") ? node.get("lat").asDouble() : null;
        Double lon = node.has("lon") ? node.get("lon").asDouble() : null;
        String placeId = node.path("place_id").asString();

        String ciudad = null;
        String pais = null;
        JsonNode address = node.path("address");
        if (!address.isMissingNode()) {
            ciudad = address.path("city").asString();
            if (ciudad == null || ciudad.isEmpty()) {
                ciudad = address.path("town").asString();
            }
            if (ciudad == null || ciudad.isEmpty()) {
                ciudad = address.path("village").asString();
            }
            pais = address.path("country").asString();
        }

        return GeocodeResult.builder()
                .displayName(displayName)
                .latitud(lat)
                .longitud(lon)
                .ciudad(ciudad)
                .pais(pais)
                .placeId(placeId)
                .build();
    }
}