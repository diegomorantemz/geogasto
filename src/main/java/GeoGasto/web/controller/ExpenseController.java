package GeoGasto.web.controller;

import GeoGasto.domain.dto.expense.ExpenseRequest;
import GeoGasto.domain.dto.expense.ExpenseResponse;
import GeoGasto.domain.dto.expense.ExpenseWithLocationRequest;
import GeoGasto.domain.service.ExpenseService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/expenses")
@RequiredArgsConstructor
public class ExpenseController {

    private final ExpenseService expenseService;

    @GetMapping
    public ResponseEntity<Page<ExpenseResponse>> listar(Authentication auth, Pageable pageable) {
        return ResponseEntity.ok(expenseService.listar(auth.getName(), pageable));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ExpenseResponse> obtener(Authentication auth, @PathVariable Long id) {
        return ResponseEntity.ok(expenseService.obtener(auth.getName(), id));
    }

    @PostMapping
    public ResponseEntity<ExpenseResponse> crear(
            Authentication auth,
            @Valid @RequestBody ExpenseRequest request
    ) {
        ExpenseResponse creado = expenseService.crear(auth.getName(), request);
        return ResponseEntity.status(HttpStatus.CREATED).body(creado);
    }

    @PostMapping("/with-location")
    public ResponseEntity<ExpenseResponse> crearConUbicacion(
            Authentication auth,
            @Valid @RequestBody ExpenseWithLocationRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(expenseService.crearConUbicacion(auth.getName(), request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ExpenseResponse> actualizar(
            Authentication auth,
            @PathVariable Long id,
            @Valid @RequestBody ExpenseRequest request
    ) {
        return ResponseEntity.ok(expenseService.actualizar(auth.getName(), id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(Authentication auth, @PathVariable Long id) {
        expenseService.eliminar(auth.getName(), id);
        return ResponseEntity.noContent().build();
    }
}