package GeoGasto.domain.dto.importcsv;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImportResultResponse {
    private int importados;
    private int omitidos;
    private int errores;
    private String mensaje;
}
