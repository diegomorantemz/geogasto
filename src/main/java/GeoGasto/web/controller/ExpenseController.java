package GeoGasto.web.controller;

import GeoGasto.domain.dto.expense.ExpenseMapResponse;
import GeoGasto.domain.dto.expense.ExpenseRequest;
import GeoGasto.domain.dto.expense.ExpenseResponse;
import GeoGasto.domain.dto.expense.ExpenseWithLocationRequest;
import GeoGasto.domain.service.ExpenseService;
import GeoGasto.web.dto.PageResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

@RestController
@RequestMapping("/api/expenses")
@RequiredArgsConstructor
@Validated
public class ExpenseController {

    private static final Set<String> CAMPOS_ORDENABLES = Set.of("fecha", "monto", "descripcion", "createdAt");
    private static final int TAMANO_MAXIMO_PAGINA = 100;

    private final ExpenseService expenseService;

    @GetMapping
    public ResponseEntity<PageResponse<ExpenseResponse>> listar(
            Authentication auth,
            Pageable pageable,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) @Size(max = 255) String q
    ) {
        return ResponseEntity.ok(PageResponse.from(expenseService.listar(
                auth.getName(), sanitizar(pageable), desde, hasta, categoryId, q)));
    }

    @GetMapping("/map")
    public ResponseEntity<List<ExpenseMapResponse>> listarParaMapa(
            Authentication auth,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) @Size(max = 255) String q
    ) {
        return ResponseEntity.ok(expenseService.listarParaMapa(
                auth.getName(), desde, hasta, categoryId, q));
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportar(
            Authentication auth,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false) @Size(max = 255) String q
    ) {
        byte[] csv = expenseService.exportarCsv(auth.getName(), desde, hasta, categoryId, q);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"gastos.csv\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(csv);
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

    @PutMapping("/{id}/with-location")
    public ResponseEntity<ExpenseResponse> actualizarConUbicacion(
            Authentication auth,
            @PathVariable Long id,
            @Valid @RequestBody ExpenseWithLocationRequest request
    ) {
        return ResponseEntity.ok(expenseService.actualizarConUbicacion(auth.getName(), id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(Authentication auth, @PathVariable Long id) {
        expenseService.eliminar(auth.getName(), id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Evita que un cliente pida páginas gigantes o intente ordenar por campos no permitidos.
     */
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
