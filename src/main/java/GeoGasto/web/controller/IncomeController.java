package GeoGasto.web.controller;

import GeoGasto.domain.dto.income.IncomeRequest;
import GeoGasto.domain.dto.income.IncomeResponse;
import GeoGasto.domain.service.IncomeService;
import GeoGasto.web.dto.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/incomes")
@RequiredArgsConstructor
@Validated
public class IncomeController {

    private static final Set<String> CAMPOS_ORDENABLES = Set.of("fecha", "monto", "descripcion", "fuente", "createdAt");
    private static final int TAMANO_MAXIMO_PAGINA = 100;

    private final IncomeService incomeService;

    @GetMapping
    public ResponseEntity<PageResponse<IncomeResponse>> listar(
            Authentication auth,
            Pageable pageable,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) @Size(max = 255) String q
    ) {
        return ResponseEntity.ok(PageResponse.from(incomeService.listar(
                auth.getName(), sanitizar(pageable), desde, hasta, q)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<IncomeResponse> obtener(Authentication auth, @PathVariable Long id) {
        return ResponseEntity.ok(incomeService.obtener(auth.getName(), id));
    }

    @PostMapping
    public ResponseEntity<IncomeResponse> crear(
            Authentication auth,
            @Valid @RequestBody IncomeRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(incomeService.crear(auth.getName(), request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<IncomeResponse> actualizar(
            Authentication auth,
            @PathVariable Long id,
            @Valid @RequestBody IncomeRequest request
    ) {
        return ResponseEntity.ok(incomeService.actualizar(auth.getName(), id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(Authentication auth, @PathVariable Long id) {
        incomeService.eliminar(auth.getName(), id);
        return ResponseEntity.noContent().build();
    }

    private Pageable sanitizar(Pageable pageable) {
        int pagina = Math.max(pageable.getPageNumber(), 0);
        int tamano = Math.min(Math.max(pageable.getPageSize(), 1), TAMANO_MAXIMO_PAGINA);

        List<Sort.Order> ordenes = pageable.getSort().stream()
                .filter(orden -> CAMPOS_ORDENABLES.contains(orden.getProperty()))
                .toList();

        Sort sort = ordenes.isEmpty()
                ? Sort.by(Sort.Direction.DESC, "fecha")
                : Sort.by(ordenes);

        return PageRequest.of(pagina, tamano, sort);
    }
}
