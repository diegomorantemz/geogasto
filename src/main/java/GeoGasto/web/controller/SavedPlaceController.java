package GeoGasto.web.controller;

import GeoGasto.domain.dto.savedplace.SavedPlaceResponse;
import GeoGasto.domain.service.SavedPlaceService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/saved-places")
@RequiredArgsConstructor
public class SavedPlaceController {

    private final SavedPlaceService savedPlaceService;

    @GetMapping
    public ResponseEntity<List<SavedPlaceResponse>> listar(Authentication auth) {
        return ResponseEntity.ok(savedPlaceService.listar(auth.getName()));
    }

    @PostMapping
    public ResponseEntity<SavedPlaceResponse> crear(
            Authentication auth,
            @RequestBody CreateSavedPlaceRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(savedPlaceService.crear(auth.getName(), request.getAlias(), request.getLocationId()));
    }

    @PostMapping("/{id}/use")
    public ResponseEntity<Void> registrarUso(Authentication auth, @PathVariable Long id) {
        savedPlaceService.registrarUso(auth.getName(), id);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(Authentication auth, @PathVariable Long id) {
        savedPlaceService.eliminar(auth.getName(), id);
        return ResponseEntity.noContent().build();
    }

    public static class CreateSavedPlaceRequest {
        private String alias;
        private Long locationId;
        public String getAlias() { return alias; }
        public void setAlias(String alias) { this.alias = alias; }
        public Long getLocationId() { return locationId; }
        public void setLocationId(Long locationId) { this.locationId = locationId; }
    }
}