package GeoGasto.domain.dto.importcsv;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImportPreviewResponse {
    private int total;
    private int validas;
    private int duplicadas;
    private int errores;
    private List<Fila> filas;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class Fila {
        private int numero;
        private String fecha;
        private String monto;
        private String descripcion;
        private String categoria;
        private boolean valida;
        private boolean duplicada;
        private String error;
    }
}
