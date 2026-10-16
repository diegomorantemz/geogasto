package GeoGasto.domain.service;

import GeoGasto.domain.dto.stats.StatsResponse;
import GeoGasto.persistence.entity.Expense;
import GeoGasto.persistence.entity.User;
import GeoGasto.domain.exception.ResourceNotFoundException;
import GeoGasto.persistence.repository.ExpenseRepository;
import GeoGasto.persistence.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StatsService {

    private final ExpenseRepository expenseRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public StatsResponse getStats(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        // Traemos los últimos 12 meses de gastos
        LocalDate hasta = LocalDate.now();
        LocalDate desde = hasta.minusMonths(11).withDayOfMonth(1);
        List<Expense> gastos = expenseRepository.findByUserIdAndFechaBetween(user.getId(), desde, hasta);

        BigDecimal totalMes = calcularTotalMes(gastos);
        List<StatsResponse.CategoryStat> porCategoria = calcularPorCategoria(gastos);
        List<StatsResponse.MonthStat> porMes = calcularPorMes(gastos);

        long cantidadCategorias = gastos.stream()
                .map(e -> e.getCategory() != null ? e.getCategory().getId() : null)
                .filter(Objects::nonNull)
                .distinct()
                .count();

        return StatsResponse.builder()
                .totalMes(totalMes)
                .cantidadGastos((long) gastos.size())
                .cantidadCategorias(cantidadCategorias)
                .gastosPorCategoria(porCategoria)
                .gastosPorMes(porMes)
                .build();
    }

    private BigDecimal calcularTotalMes(List<Expense> gastos) {
        YearMonth mesActual = YearMonth.now();
        return gastos.stream()
                .filter(e -> YearMonth.from(e.getFecha()).equals(mesActual))
                .map(Expense::getMonto)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private List<StatsResponse.CategoryStat> calcularPorCategoria(List<Expense> gastos) {
        YearMonth mesActual = YearMonth.now();

        Map<String, StatsResponse.CategoryStat> map = new LinkedHashMap<>();
        for (Expense e : gastos) {
            if (!YearMonth.from(e.getFecha()).equals(mesActual)) continue;

            String nombre = e.getCategory() != null ? e.getCategory().getNombre() : "Sin categoría";
            String color = e.getCategory() != null ? e.getCategory().getColor() : "#adb5bd";

            map.compute(nombre, (k, v) -> {
                if (v == null) {
                    return StatsResponse.CategoryStat.builder()
                            .categoryNombre(nombre)
                            .color(color)
                            .total(e.getMonto())
                            .cantidad(1L)
                            .build();
                }
                v.setTotal(v.getTotal().add(e.getMonto()));
                v.setCantidad(v.getCantidad() + 1);
                return v;
            });
        }
        return new ArrayList<>(map.values());
    }

    private List<StatsResponse.MonthStat> calcularPorMes(List<Expense> gastos) {
        DateTimeFormatter inputFmt = DateTimeFormatter.ofPattern("yyyy-MM");
        DateTimeFormatter labelFmt = DateTimeFormatter.ofPattern("MMM yyyy", new Locale("es", "PE"));

        Map<YearMonth, BigDecimal> totales = new TreeMap<>();
        for (Expense e : gastos) {
            YearMonth ym = YearMonth.from(e.getFecha());
            totales.merge(ym, e.getMonto(), BigDecimal::add);
        }

        return totales.entrySet().stream()
                .map(entry -> StatsResponse.MonthStat.builder()
                        .mes(entry.getKey().format(inputFmt))
                        .mesLabel(entry.getKey().format(labelFmt))
                        .total(entry.getValue())
                        .build())
                .collect(Collectors.toList());
    }
}