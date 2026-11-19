package GeoGasto.web.controller;

import GeoGasto.domain.dto.importcsv.ImportPreviewResponse;
import GeoGasto.domain.dto.importcsv.ImportResultResponse;
import GeoGasto.domain.service.ExpenseImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/expenses/import")
@RequiredArgsConstructor
public class ExpenseImportController {

    private final ExpenseImportService expenseImportService;

    @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImportPreviewResponse> previsualizar(
            Authentication auth,
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false, defaultValue = "auto") String delimiter
    ) {
        return ResponseEntity.ok(expenseImportService.previsualizar(
                auth.getName(), file, categoryId, delimiter));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImportResultResponse> importar(
            Authentication auth,
            @RequestParam("file") MultipartFile file,
            @RequestParam(required = false) Long categoryId,
            @RequestParam(required = false, defaultValue = "auto") String delimiter
    ) {
        return ResponseEntity.ok(expenseImportService.importar(
                auth.getName(), file, categoryId, delimiter));
    }
}
