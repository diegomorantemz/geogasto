package GeoGasto.web.controller;

import GeoGasto.domain.dto.geocode.GeocodeResult;
import GeoGasto.domain.service.GeocodingService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/geocode")
@RequiredArgsConstructor
public class GeocodingController {

    private final GeocodingService geocodingService;

    @GetMapping("/search")
    public ResponseEntity<List<GeocodeResult>> search(@RequestParam("q") String query) {
        return ResponseEntity.ok(geocodingService.search(query));
    }

    @GetMapping("/reverse")
    public ResponseEntity<GeocodeResult> reverse(
            @RequestParam("lat") Double lat,
            @RequestParam("lon") Double lon
    ) {
        return ResponseEntity.ok(geocodingService.reverse(lat, lon));
    }
}