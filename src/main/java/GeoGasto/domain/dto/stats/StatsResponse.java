package GeoGasto.domain.dto.stats;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StatsResponse {
    private BigDecimal totalMes;
    private Long cantidadGastos;
    private Long cantidadCategorias;
    private List<CategoryStat> gastosPorCategoria;
    private List<MonthStat> gastosPorMes;

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CategoryStat {
        private String categoryNombre;
        private String color;
        private BigDecimal total;
        private Long cantidad;
    }

    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MonthStat {
        private String mes;        // "2026-10"
        private String mesLabel;   // "Oct 2026"
        private BigDecimal total;
    }
}