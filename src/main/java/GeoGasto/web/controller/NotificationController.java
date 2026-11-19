package GeoGasto.web.controller;

import GeoGasto.domain.dto.notification.NotificationSummaryResponse;
import GeoGasto.domain.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ResponseEntity<NotificationSummaryResponse> listar(Authentication auth) {
        return ResponseEntity.ok(notificationService.listar(auth.getName()));
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<Void> marcarLeida(Authentication auth, @PathVariable Long id) {
        notificationService.marcarLeida(auth.getName(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/read-all")
    public ResponseEntity<Void> marcarTodas(Authentication auth) {
        notificationService.marcarTodas(auth.getName());
        return ResponseEntity.noContent().build();
    }
}
