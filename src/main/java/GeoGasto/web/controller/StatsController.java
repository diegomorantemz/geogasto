package GeoGasto.web.controller;

import GeoGasto.domain.dto.stats.StatsResponse;
import GeoGasto.domain.service.StatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/stats")
@RequiredArgsConstructor
public class StatsController {

    private final StatsService statsService;

    @GetMapping
    public ResponseEntity<StatsResponse> getStats(Authentication auth) {
        return ResponseEntity.ok(statsService.getStats(auth.getName()));
    }
}