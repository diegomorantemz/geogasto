package GeoGasto.web.controller;

import GeoGasto.domain.dto.geocode.GeocodeResult;
import GeoGasto.domain.service.GeocodingService;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/geocode")
@RequiredArgsConstructor
@Validated
public class GeocodingController {

    private final GeocodingService geocodingService;

    @GetMapping("/search")
    public ResponseEntity<List<GeocodeResult>> search(
            @RequestParam("q") @NotBlank @Size(min = 3, max = 200) String query
    ) {
        return ResponseEntity.ok(geocodingService.search(query));
    }

    @GetMapping("/reverse")
    public ResponseEntity<GeocodeResult> reverse(
            @RequestParam("lat") @DecimalMin("-90.0") @DecimalMax("90.0") Double lat,
            @RequestParam("lon") @DecimalMin("-180.0") @DecimalMax("180.0") Double lon
    ) {
        return ResponseEntity.ok(geocodingService.reverse(lat, lon));
    }
}
