package GeoGasto.web.controller;

import GeoGasto.domain.dto.recurring.RecurringExpenseRequest;
import GeoGasto.domain.dto.recurring.RecurringExpenseResponse;
import GeoGasto.domain.dto.recurring.RecurringGenerationResponse;
import GeoGasto.domain.service.RecurringExpenseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/recurring-expenses")
@RequiredArgsConstructor
public class RecurringExpenseController {

    private final RecurringExpenseService recurringExpenseService;

    @GetMapping
    public ResponseEntity<List<RecurringExpenseResponse>> listar(Authentication auth) {
        return ResponseEntity.ok(recurringExpenseService.listar(auth.getName()));
    }

    @PostMapping
    public ResponseEntity<RecurringExpenseResponse> crear(
            Authentication auth,
            @Valid @RequestBody RecurringExpenseRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(recurringExpenseService.crear(auth.getName(), request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<RecurringExpenseResponse> actualizar(
            Authentication auth,
            @PathVariable Long id,
            @Valid @RequestBody RecurringExpenseRequest request
    ) {
        return ResponseEntity.ok(recurringExpenseService.actualizar(auth.getName(), id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(Authentication auth, @PathVariable Long id) {
        recurringExpenseService.eliminar(auth.getName(), id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/generate")
    public ResponseEntity<RecurringGenerationResponse> generarPendientes(Authentication auth) {
        int generados = recurringExpenseService.generarPendientes(auth.getName());
        return ResponseEntity.ok(new RecurringGenerationResponse(generados));
    }
}
