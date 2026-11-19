package GeoGasto.domain.service;

import GeoGasto.domain.dto.budget.BudgetCopyResponse;
import GeoGasto.domain.dto.budget.BudgetRequest;
import GeoGasto.domain.dto.budget.BudgetResponse;
import GeoGasto.domain.dto.budget.BudgetSummaryResponse;
import GeoGasto.domain.dto.budget.GeneralBudgetRequest;
import GeoGasto.domain.exception.ResourceNotFoundException;
import GeoGasto.domain.util.UserTime;
import GeoGasto.persistence.entity.Budget;
import GeoGasto.persistence.entity.Category;
import GeoGasto.persistence.entity.GeneralBudget;
import GeoGasto.persistence.entity.User;
import GeoGasto.persistence.repository.BudgetRepository;
import GeoGasto.persistence.repository.CategoryRepository;
import GeoGasto.persistence.repository.ExpenseRepository;
import GeoGasto.persistence.repository.GeneralBudgetRepository;
import GeoGasto.persistence.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
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
    private final GeneralBudgetRepository generalBudgetRepository;
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

        GeneralBudget general = generalBudgetRepository
                .findByUserIdAndPeriodo(user.getId(), desde)
                .orElse(null);
        BigDecimal gastadoGeneral = gastos.values().stream()
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal presupuestoGeneral = general != null ? general.getMonto() : null;
        BigDecimal porcentajeGeneral = general != null
                ? calcularPorcentaje(gastadoGeneral, presupuestoGeneral)
                : null;

        return BudgetSummaryResponse.builder()
                .mes(periodo.toString())
                .totalPresupuesto(totalPresupuesto)
                .totalGastado(totalGastado)
                .totalDisponible(totalPresupuesto.subtract(totalGastado))
                .porcentaje(calcularPorcentaje(totalGastado, totalPresupuesto))
                .presupuestoGeneral(presupuestoGeneral)
                .gastadoGeneral(gastadoGeneral)
                .disponibleGeneral(presupuestoGeneral != null
                        ? presupuestoGeneral.subtract(gastadoGeneral) : null)
                .porcentajeGeneral(porcentajeGeneral)
                .estadoGeneral(porcentajeGeneral != null
                        ? estado(gastadoGeneral, presupuestoGeneral, porcentajeGeneral) : null)
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
    public void guardarGeneral(String email, String mes, GeneralBudgetRequest request) {
        User user = obtenerUsuario(email);
        YearMonth periodo = parseMes(mes, user);
        GeneralBudget budget = generalBudgetRepository
                .findByUserIdAndPeriodo(user.getId(), periodo.atDay(1))
                .orElseGet(GeneralBudget::new);

        budget.setUser(user);
        budget.setPeriodo(periodo.atDay(1));
        budget.setMonto(request.getMonto());
        generalBudgetRepository.save(budget);
    }

    @Transactional
    public void eliminarGeneral(String email, String mes) {
        User user = obtenerUsuario(email);
        YearMonth periodo = parseMes(mes, user);
        GeneralBudget budget = generalBudgetRepository
                .findByUserIdAndPeriodo(user.getId(), periodo.atDay(1))
                .orElseThrow(() -> new ResourceNotFoundException("Presupuesto general no encontrado"));
        generalBudgetRepository.delete(budget);
    }

    @Transactional
    public BudgetCopyResponse copiarMesAnterior(String email, String mes) {
        User user = obtenerUsuario(email);
        YearMonth periodo = parseMes(mes, user);
        YearMonth anterior = periodo.minusMonths(1);

        generalBudgetRepository.findByUserIdAndPeriodo(user.getId(), anterior.atDay(1))
                .ifPresent(previo -> {
                    if (generalBudgetRepository.findByUserIdAndPeriodo(user.getId(), periodo.atDay(1)).isEmpty()) {
                        generalBudgetRepository.save(GeneralBudget.builder()
                                .user(user)
                                .periodo(periodo.atDay(1))
                                .monto(previo.getMonto())
                                .build());
                    }
                });

        List<Budget> previos = budgetRepository.findByUserIdAndPeriodo(
                user.getId(), anterior.atDay(1));
        int copiados = 0;

        for (Budget previo : previos) {
            boolean existe = budgetRepository
                    .findByUserIdAndCategoryIdAndPeriodo(user.getId(), previo.getCategory().getId(),
                            periodo.atDay(1))
                    .isPresent();
            if (existe) {
                continue;
            }

            Budget nuevo = Budget.builder()
                    .user(user)
                    .category(previo.getCategory())
                    .periodo(periodo.atDay(1))
                    .monto(previo.getMonto())
                    .build();
            budgetRepository.save(nuevo);
            copiados++;
        }

        return new BudgetCopyResponse(copiados, periodo.toString());
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
        String estado = estado(gastado, monto, porcentaje);

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

    private static String estado(BigDecimal gastado, BigDecimal monto, BigDecimal porcentaje) {
        return gastado.compareTo(monto) > 0
                ? "EXCEDIDO"
                : porcentaje.compareTo(UMBRAL_ALERTA) >= 0 ? "ALERTA" : "OK";
    }

    private static BigDecimal calcularPorcentaje(BigDecimal gastado, BigDecimal presupuesto) {
        if (presupuesto == null || presupuesto.signum() == 0) {
            return BigDecimal.ZERO;
        }
        return gastado.multiply(CIEN).divide(presupuesto, 1, RoundingMode.HALF_UP);
    }

    private static YearMonth parseMes(String mes, User user) {
        if (mes == null || mes.isBlank()) {
            return YearMonth.now(UserTime.zoneOf(user));
        }
        try {
            return YearMonth.parse(mes);
        } catch (DateTimeParseException e) {
            throw new IllegalArgumentException("El mes debe tener formato YYYY-MM");
        }
    }

    private User obtenerUsuario(String email) {
        return userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
    }
}
