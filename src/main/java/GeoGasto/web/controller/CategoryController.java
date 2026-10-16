package GeoGasto.web.controller;

import GeoGasto.domain.dto.category.CategoryRequest;
import GeoGasto.domain.dto.category.CategoryResponse;
import GeoGasto.domain.service.CategoryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/categories")
@RequiredArgsConstructor
public class CategoryController {

    private final CategoryService categoryService;

    @GetMapping
    public ResponseEntity<List<CategoryResponse>> listar(Authentication auth) {
        return ResponseEntity.ok(categoryService.listar(auth.getName()));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CategoryResponse> obtener(Authentication auth, @PathVariable Long id) {
        return ResponseEntity.ok(categoryService.obtener(auth.getName(), id));
    }

    @PostMapping
    public ResponseEntity<CategoryResponse> crear(
            Authentication auth,
            @Valid @RequestBody CategoryRequest request
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(categoryService.crear(auth.getName(), request));
    }

    @PutMapping("/{id}")
    public ResponseEntity<CategoryResponse> actualizar(
            Authentication auth,
            @PathVariable Long id,
            @Valid @RequestBody CategoryRequest request
    ) {
        return ResponseEntity.ok(categoryService.actualizar(auth.getName(), id, request));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(Authentication auth, @PathVariable Long id) {
        categoryService.eliminar(auth.getName(), id);
        return ResponseEntity.noContent().build();
    }
}