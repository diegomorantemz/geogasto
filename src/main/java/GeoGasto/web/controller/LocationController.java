package GeoGasto.web.controller;

import GeoGasto.domain.dto.location.LocationRequest;
import GeoGasto.domain.dto.location.LocationResponse;
import GeoGasto.domain.service.LocationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/locations")
@RequiredArgsConstructor
public class LocationController {

    private final LocationService locationService;

    @GetMapping("/{id}")
    public ResponseEntity<LocationResponse> obtener(@PathVariable Long id) {
        return ResponseEntity.ok(locationService.obtener(id));
    }

    @PostMapping
    public ResponseEntity<LocationResponse> crear(@Valid @RequestBody LocationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(locationService.crear(request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<LocationResponse> actualizar(
            @PathVariable Long id,
            @Valid @RequestBody LocationRequest request
    ) {
        return ResponseEntity.ok(locationService.actualizar(id, request));
    }
}