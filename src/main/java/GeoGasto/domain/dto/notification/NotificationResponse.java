package GeoGasto.domain.dto.notification;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationResponse {
    private Long id;
    private String tipo;
    private String titulo;
    private String mensaje;
    private String link;
    private Boolean leida;
    private LocalDateTime createdAt;
}
