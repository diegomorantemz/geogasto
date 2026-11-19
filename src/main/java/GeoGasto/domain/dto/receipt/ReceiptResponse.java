package GeoGasto.domain.dto.receipt;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ReceiptResponse {

    private Long id;
    private Long expenseId;
    private String nombreOriginal;
    private String contentType;
    private Long tamano;
    private LocalDateTime createdAt;
}
