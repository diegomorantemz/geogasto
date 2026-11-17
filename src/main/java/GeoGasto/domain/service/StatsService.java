package GeoGasto.domain.service;

import GeoGasto.domain.dto.stats.StatsResponse;
import GeoGasto.domain.exception.ResourceNotFoundException;
import GeoGasto.persistence.entity.Expense;
import GeoGasto.persistence.entity.User;
import GeoGasto.persistence.repository.ExpenseRepository;
import GeoGasto.persistence.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class StatsService {

    private final ExpenseRepository expenseRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public StatsResponse getStats(String email) {
        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));

        LocalDate hoy = LocalDate.now(zonaDe(user));
        LocalDate hasta = hoy;
        LocalDate desde = hasta.minusMonths(11).withDayOfMonth(1);

        List<Expense> gastos = expenseRepository.findByUserIdAndFechaBetween(user.getId(), desde, hasta);

        YearMonth mesActual = YearMonth.from(hoy);
        YearMonth mesAnterior = mesActual.minusMonths(1);

        List<Expense> gastosMes = gastos.stream()
                .filter(e -> YearMonth.from(e.getFecha()).equals(mesActual))
                .toList();

        BigDecimal totalMes = sumar(gastosMes);
        BigDecimal totalMesAnterior = sumar(gastos.stream()
                .filter(e -> YearMonth.from(e.getFecha()).equals(mesAnterior))
                .toList());

        BigDecimal variacion = calcularVariacion(totalMes, totalMesAnterior);
        BigDecimal promedioDiario = totalMes.divide(
                BigDecimal.valueOf(Math.max(hoy.getDayOfMonth(), 1)), 2, RoundingMode.HALF_UP);

        List<StatsResponse.CategoryStat> porCategoria = calcularPorCategoria(gastosMes);
        StatsResponse.CategoryStat categoriaTop = porCategoria.stream()
                .max(Comparator.comparing(StatsResponse.CategoryStat::getTotal))
                .orElse(null);

        long cantidadCategorias = gastosMes.stream()
                .map(e -> e.getCategory() != null ? e.getCategory().getId() : null)
                .filter(Objects::nonNull)
                .distinct()
                .count();

        long cantidadLugares = gastosMes.stream()
                .map(e -> e.getLocation() != null ? e.getLocation().getId() : null)
                .filter(Objects::nonNull)
                .distinct()
                .count();

        return StatsResponse.builder()
                .totalMes(totalMes)
                .totalMesAnterior(totalMesAnterior)
                .variacionPorcentual(variacion)
                .promedioDiario(promedioDiario)
                .categoriaTopNombre(categoriaTop != null ? categoriaTop.getCategoryNombre() : null)
                .categoriaTopColor(categoriaTop != null ? categoriaTop.getColor() : null)
                .categoriaTopTotal(categoriaTop != null ? categoriaTop.getTotal() : null)
                .cantidadGastos((long) gastosMes.size())
                .cantidadCategorias(cantidadCategorias)
                .cantidadLugares(cantidadLugares)
                .gastosPorCategoria(porCategoria)
                .gastosPorMes(calcularPorMes(gastos, YearMonth.from(desde)))
                .build();
    }

    private static ZoneId zonaDe(User user) {
        try {
            return ZoneId.of(user.getTimezone());
        } catch (DateTimeException | NullPointerException e) {
            return ZoneId.of("America/Lima");
        }
    }

    private static BigDecimal sumar(List<Expense> gastos) {
        return gastos.stream()
                .map(Expense::getMonto)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private static BigDecimal calcularVariacion(BigDecimal actual, BigDecimal anterior) {
        if (anterior.signum() == 0) {
            return null;
        }
        return actual.subtract(anterior)
                .multiply(BigDecimal.valueOf(100))
                .divide(anterior, 1, RoundingMode.HALF_UP);
    }

    private List<StatsResponse.CategoryStat> calcularPorCategoria(List<Expense> gastos) {
        Map<String, StatsResponse.CategoryStat> map = new LinkedHashMap<>();
        for (Expense e : gastos) {
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

    private List<StatsResponse.MonthStat> calcularPorMes(List<Expense> gastos, YearMonth inicio) {
        DateTimeFormatter inputFmt = DateTimeFormatter.ofPattern("yyyy-MM");
        DateTimeFormatter labelFmt = DateTimeFormatter.ofPattern("MMM yyyy", new Locale("es", "PE"));

        // Siempre devolvemos los últimos 12 meses, aunque algún mes no tenga gastos.
        Map<YearMonth, BigDecimal> totales = new TreeMap<>();
        for (int i = 0; i < 12; i++) {
            totales.put(inicio.plusMonths(i), BigDecimal.ZERO);
        }

        for (Expense e : gastos) {
            YearMonth ym = YearMonth.from(e.getFecha());
            if (totales.containsKey(ym)) {
                totales.merge(ym, e.getMonto(), BigDecimal::add);
            }
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
