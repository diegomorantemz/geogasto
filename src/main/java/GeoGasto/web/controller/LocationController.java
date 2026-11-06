package GeoGasto.web.controller;

import GeoGasto.domain.dto.location.LocationRequest;
import GeoGasto.domain.dto.location.LocationResponse;
import GeoGasto.domain.service.LocationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/locations")
@RequiredArgsConstructor
public class LocationController {

    private final LocationService locationService;

    @GetMapping("/{id}")
    public ResponseEntity<LocationResponse> obtener(Authentication auth, @PathVariable Long id) {
        return ResponseEntity.ok(locationService.obtener(auth.getName(), id));
    }

    @PostMapping
    public ResponseEntity<LocationResponse> crear(
            Authentication auth,
            @Valid @RequestBody LocationRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(locationService.crear(auth.getName(), request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<LocationResponse> actualizar(
            Authentication auth,
            @PathVariable Long id,
            @Valid @RequestBody LocationRequest request
    ) {
        return ResponseEntity.ok(locationService.actualizar(auth.getName(), id, request));
    }
}