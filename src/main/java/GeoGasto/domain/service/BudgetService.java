package GeoGasto.domain.service;

import GeoGasto.domain.dto.budget.BudgetRequest;
import GeoGasto.domain.dto.budget.BudgetResponse;
import GeoGasto.domain.dto.budget.BudgetSummaryResponse;
import GeoGasto.domain.exception.ResourceNotFoundException;
import GeoGasto.persistence.entity.Budget;
import GeoGasto.persistence.entity.Category;
import GeoGasto.persistence.entity.User;
import GeoGasto.persistence.repository.BudgetRepository;
import GeoGasto.persistence.repository.CategoryRepository;
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
import java.time.format.DateTimeParseException;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BudgetService {

    private static final BigDecimal CIEN = BigDecimal.valueOf(100);
    private static final BigDecimal UMBRAL_ALERTA = BigDecimal.valueOf(80);

    private final BudgetRepository budgetRepository;
    private final CategoryRepository categoryRepository;
    private final ExpenseRepository expenseRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public BudgetSummaryResponse resumen(String email, String mes) {
        User user = obtenerUsuario(email);
        YearMonth periodo = parseMes(mes, user);

        LocalDate desde = periodo.atDay(1);
        LocalDate hasta = periodo.atEndOfMonth();

        List<Budget> budgets = budgetRepository.findByUserIdAndPeriodo(user.getId(), desde);
        Map<Long, BigDecimal> gastos = gastosPorCategoria(user.getId(), desde, hasta);

        List<BudgetResponse> presupuestos = budgets.stream()
                .map(b -> toResponse(b, gastos.getOrDefault(b.getCategory().getId(), BigDecimal.ZERO)))
                .sorted(Comparator.comparing(BudgetResponse::getCategoryNombre, String.CASE_INSENSITIVE_ORDER))
                .toList();

        BigDecimal totalPresupuesto = presupuestos.stream()
                .map(BudgetResponse::getMonto)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal totalGastado = presupuestos.stream()
                .map(BudgetResponse::getGastado)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return BudgetSummaryResponse.builder()
                .mes(periodo.toString())
                .totalPresupuesto(totalPresupuesto)
                .totalGastado(totalGastado)
                .totalDisponible(totalPresupuesto.subtract(totalGastado))
                .porcentaje(calcularPorcentaje(totalGastado, totalPresupuesto))
                .presupuestos(presupuestos)
                .build();
    }

    @Transactional
    public BudgetResponse guardar(String email, BudgetRequest request) {
        User user = obtenerUsuario(email);
        YearMonth periodo = parseMes(request.getMes(), user);

        Category category = categoryRepository.findById(request.getCategoryId())
                .orElseThrow(() -> new ResourceNotFoundException("Categoría no encontrada"));
        if (!category.getUser().getId().equals(user.getId())) {
            throw new ResourceNotFoundException("Categoría no encontrada");
        }

        LocalDate inicio = periodo.atDay(1);
        Budget budget = budgetRepository
                .findByUserIdAndCategoryIdAndPeriodo(user.getId(), category.getId(), inicio)
                .orElseGet(Budget::new);

        budget.setUser(user);
        budget.setCategory(category);
        budget.setPeriodo(inicio);
        budget.setMonto(request.getMonto());
        budgetRepository.save(budget);

        BigDecimal gastado = expenseRepository.sumarPorCategoriaYPeriodo(
                user.getId(), category.getId(), inicio, periodo.atEndOfMonth());

        return toResponse(budget, gastado == null ? BigDecimal.ZERO : gastado);
    }

    @Transactional
    public void eliminar(String email, Long id) {
        User user = obtenerUsuario(email);
        Budget budget = budgetRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Presupuesto no encontrado"));
        budgetRepository.delete(budget);
    }

    private Map<Long, BigDecimal> gastosPorCategoria(Long userId, LocalDate desde, LocalDate hasta) {
        return expenseRepository.sumarPorCategoria(userId, desde, hasta).stream()
                .collect(Collectors.toMap(
                        ExpenseRepository.CategoriaTotal::getCategoriaId,
                        t -> t.getTotal() == null ? BigDecimal.ZERO : t.getTotal()));
    }

    private BudgetResponse toResponse(Budget budget, BigDecimal gastado) {
        BigDecimal monto = budget.getMonto();
        BigDecimal porcentaje = calcularPorcentaje(gastado, monto);
        String estado = gastado.compareTo(monto) > 0
                ? "EXCEDIDO"
                : porcentaje.compareTo(UMBRAL_ALERTA) >= 0 ? "ALERTA" : "OK";

        return BudgetResponse.builder()
                .id(budget.getId())
                .categoryId(budget.getCategory().getId())
                .categoryNombre(budget.getCategory().getNombre())
                .categoryColor(budget.getCategory().getColor())
                .mes(YearMonth.from(budget.getPeriodo()).toString())
                .monto(monto)
                .gastado(gastado)
                .disponible(monto.subtract(gastado))
                .porcentaje(porcentaje)
                .estado(estado)
                .build();
    }

    private static BigDecimal calcularPorcentaje(BigDecimal gastado, BigDecimal presupuesto) {
        if (presupuesto == null || presupuesto.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return gastado.multiply(CIEN).divide(presupuesto, 1, RoundingMode.HALF_UP);
    }

    private static YearMonth parseMes(String mes, User user) {
        if (mes == null || mes.isBlank()) {
            return YearMonth.now(zonaDe(user));
        }
        try {
            return YearMonth.parse(mes);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("El mes debe tener formato YYYY-MM");
        }
    }

    private static ZoneId zonaDe(User user) {
        try {
            return ZoneId.of(user.getTimezone());
        } catch (DateTimeException | NullPointerException e) {
            return ZoneId.of("America/Lima");
        }
    }

    private User obtenerUsuario(String email) {
        return userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
    }
}
