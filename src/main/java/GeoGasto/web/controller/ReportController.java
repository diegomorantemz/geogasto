package GeoGasto.web.controller;

import GeoGasto.domain.service.ReportService;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;
import java.time.YearMonth;
import java.time.ZoneId;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
@Validated
public class ReportController {

    private final ReportService reportService;

    @GetMapping("/monthly")
    public ResponseEntity<byte[]> mensual(
            Authentication auth,
            @RequestParam(required = false)
            @Pattern(regexp = "^\\d{4}-(0[1-9]|1[0-2])$", message = "El mes debe tener formato YYYY-MM")
            String mes
    ) {
        byte[] pdf = reportService.generarMensual(auth.getName(), mes);
        String periodo = mes != null ? mes : YearMonth.now(ZoneId.systemDefault()).toString();

        return ResponseEntity.ok()
                .contentType(MediaType.APPLICATION_PDF)
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        ContentDisposition.attachment()
                                .filename("geogasto-" + periodo + ".pdf", StandardCharsets.UTF_8)
                                .build().toString())
                .body(pdf);
    }
}
