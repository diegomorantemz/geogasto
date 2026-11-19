package GeoGasto.web.controller;

import GeoGasto.domain.dto.budget.BudgetCopyResponse;
import GeoGasto.domain.dto.budget.BudgetRequest;
import GeoGasto.domain.dto.budget.BudgetResponse;
import GeoGasto.domain.dto.budget.BudgetSummaryResponse;
import GeoGasto.domain.dto.budget.GeneralBudgetRequest;
import GeoGasto.domain.service.BudgetService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/budgets")
@RequiredArgsConstructor
@Validated
public class BudgetController {

    private final BudgetService budgetService;

    @GetMapping
    public ResponseEntity<BudgetSummaryResponse> resumen(
            Authentication auth,
            @RequestParam(required = false)
            @Pattern(regexp = "^\\d{4}-(0[1-9]|1[0-2])$", message = "El mes debe tener formato YYYY-MM")
            String mes
    ) {
        return ResponseEntity.ok(budgetService.resumen(auth.getName(), mes));
    }

    @PostMapping
    public ResponseEntity<BudgetResponse> guardar(
            Authentication auth,
            @Valid @RequestBody BudgetRequest request
    ) {
        return ResponseEntity.ok(budgetService.guardar(auth.getName(), request));
    }

    @PutMapping("/general")
    public ResponseEntity<Void> guardarGeneral(
            Authentication auth,
            @RequestParam(required = false)
            @Pattern(regexp = "^\\d{4}-(0[1-9]|1[0-2])$", message = "El mes debe tener formato YYYY-MM")
            String mes,
            @Valid @RequestBody GeneralBudgetRequest request
    ) {
        budgetService.guardarGeneral(auth.getName(), mes, request);
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/general")
    public ResponseEntity<Void> eliminarGeneral(
            Authentication auth,
            @RequestParam(required = false)
            @Pattern(regexp = "^\\d{4}-(0[1-9]|1[0-2])$", message = "El mes debe tener formato YYYY-MM")
            String mes
    ) {
        budgetService.eliminarGeneral(auth.getName(), mes);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/copy-previous")
    public ResponseEntity<BudgetCopyResponse> copiarMesAnterior(
            Authentication auth,
            @RequestParam(required = false)
            @Pattern(regexp = "^\\d{4}-(0[1-9]|1[0-2])$", message = "El mes debe tener formato YYYY-MM")
            String mes
    ) {
        return ResponseEntity.ok(budgetService.copiarMesAnterior(auth.getName(), mes));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(Authentication auth, @PathVariable Long id) {
        budgetService.eliminar(auth.getName(), id);
        return ResponseEntity.noContent().build();
    }
}
