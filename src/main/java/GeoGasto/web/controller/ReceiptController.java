package GeoGasto.web.controller;

import GeoGasto.domain.dto.receipt.ReceiptDownload;
import GeoGasto.domain.dto.receipt.ReceiptResponse;
import GeoGasto.domain.service.ReceiptService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/expenses/{expenseId}/receipt")
@RequiredArgsConstructor
public class ReceiptController {

    private final ReceiptService receiptService;

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ReceiptResponse> subir(
            Authentication auth,
            @PathVariable Long expenseId,
            @RequestParam("file") MultipartFile file
    ) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(receiptService.subir(auth.getName(), expenseId, file));
    }

    @GetMapping
    public ResponseEntity<Resource> descargar(Authentication auth, @PathVariable Long expenseId) {
        ReceiptDownload receipt = receiptService.descargar(auth.getName(), expenseId);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(receipt.contentType()))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.inline()
                                .filename(receipt.nombreOriginal(), StandardCharsets.UTF_8)
                                .build().toString())
                .body(receipt.resource());
    }

    @DeleteMapping
    public ResponseEntity<Void> eliminar(Authentication auth, @PathVariable Long expenseId) {
        receiptService.eliminar(auth.getName(), expenseId);
        return ResponseEntity.noContent().build();
    }
}
