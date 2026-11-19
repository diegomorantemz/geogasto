package GeoGasto.domain.service;

import GeoGasto.domain.dto.geocode.GeocodeResult;
import GeoGasto.domain.exception.GeocodingException;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@Slf4j
public class GeocodingService {

    private final RestClient client;
    private final JsonMapper jsonMapper;
    private final long minIntervalMs;
    private final Cache<String, List<GeocodeResult>> searchCache;
    private final Cache<String, GeocodeResult> reverseCache;

    private long lastRequestAt = 0L;

    public GeocodingService(
            @Qualifier("nominatimRestClient") RestClient client,
            JsonMapper jsonMapper,
            @Value("${nominatim.min-interval-ms:1000}") long minIntervalMs,
            @Value("${nominatim.cache-hours:24}") long cacheHours
    ) {
        this.client = client;
        this.jsonMapper = jsonMapper;
        this.minIntervalMs = minIntervalMs;
        Duration cacheTtl = Duration.ofHours(Math.max(cacheHours, 1));
        this.searchCache = Caffeine.newBuilder()
                .expireAfterWrite(cacheTtl)
                .maximumSize(1000)
                .build();
        this.reverseCache = Caffeine.newBuilder()
                .expireAfterWrite(cacheTtl)
                .maximumSize(2000)
                .build();
    }

    public List<GeocodeResult> search(String query) {
        String limpia = query == null ? "" : query.trim();
        if (limpia.length() < 3) {
            throw new IllegalArgumentException("La búsqueda debe tener al menos 3 caracteres");
        }

        String key = limpia.toLowerCase(Locale.ROOT);
        List<GeocodeResult> cached = searchCache.getIfPresent(key);
        if (cached != null) {
            return cached;
        }

        throttle();

        try {
            String body = client.get()
                    .uri(uri -> uri.path("/search")
                            .queryParam("q", limpia)
                            .queryParam("format", "jsonv2")
                            .queryParam("addressdetails", "1")
                            .queryParam("limit", "5")
                            .build())
                    .retrieve()
                    .body(String.class);

            List<GeocodeResult> results = parseResults(body);
            searchCache.put(key, results);
            return results;
        } catch (GeocodingException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error llamando a Nominatim /search: {}", e.getMessage());
            throw new GeocodingException("No se pudo consultar el servicio de geocodificación", e);
        }
    }

    public GeocodeResult reverse(Double lat, Double lon) {
        if (lat == null || lon == null) {
            throw new IllegalArgumentException("La latitud y longitud son obligatorias");
        }

        String key = String.format(Locale.ROOT, "%.5f,%.5f", lat, lon);
        GeocodeResult cached = reverseCache.getIfPresent(key);
        if (cached != null) {
            return cached;
        }

        throttle();

        try {
            String body = client.get()
                    .uri(uri -> uri.path("/reverse")
                            .queryParam("lat", lat)
                            .queryParam("lon", lon)
                            .queryParam("format", "jsonv2")
                            .queryParam("addressdetails", "1")
                            .build())
                    .retrieve()
                    .body(String.class);

            if (body == null || body.isBlank()) {
                throw new GeocodingException("El servicio de geocodificación devolvió una respuesta vacía");
            }

            GeocodeResult result = toResult(jsonMapper.readTree(body));
            if (result.getLatitud() == null || result.getLongitud() == null) {
                throw new GeocodingException("El servicio de geocodificación no devolvió coordenadas");
            }
            reverseCache.put(key, result);
            return result;
        } catch (GeocodingException e) {
            throw e;
        } catch (Exception e) {
            log.error("Error llamando a Nominatim /reverse: {}", e.getMessage());
            throw new GeocodingException("No se pudo consultar el servicio de geocodificación", e);
        }
    }

    private List<GeocodeResult> parseResults(String body) {
        if (body == null || body.isBlank()) {
            return List.of();
        }

        try {
            JsonNode root = jsonMapper.readTree(body);
            List<GeocodeResult> results = new ArrayList<>();
            if (root.isArray()) {
                for (JsonNode node : root) {
                    GeocodeResult result = toResult(node);
                    if (result.getLatitud() != null && result.getLongitud() != null) {
                        results.add(result);
                    }
                }
            }
            return List.copyOf(results);
        } catch (Exception e) {
            throw new GeocodingException("Respuesta inválida del servicio de geocodificación", e);
        }
    }

    /**
     * Nominatim pide no superar una petición por segundo. Esta aplicación es pequeña,
     * así que serializamos y esperamos lo necesario entre llamadas.
     */
    private synchronized void throttle() {
        long now = System.currentTimeMillis();
        long wait = minIntervalMs - (now - lastRequestAt);
        if (wait > 0) {
            try {
                Thread.sleep(wait);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new GeocodingException("La consulta de geocodificación fue interrumpida", e);
            }
        }
        lastRequestAt = System.currentTimeMillis();
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
