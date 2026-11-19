package GeoGasto.domain.service;

import GeoGasto.domain.dto.recurring.RecurringExpenseRequest;
import GeoGasto.domain.dto.recurring.RecurringExpenseResponse;
import GeoGasto.domain.exception.ResourceNotFoundException;
import GeoGasto.persistence.entity.Category;
import GeoGasto.persistence.entity.Expense;
import GeoGasto.persistence.entity.Location;
import GeoGasto.persistence.entity.RecurringExpense;
import GeoGasto.persistence.entity.RecurringFrequency;
import GeoGasto.persistence.entity.User;
import GeoGasto.persistence.repository.CategoryRepository;
import GeoGasto.persistence.repository.ExpenseRepository;
import GeoGasto.persistence.repository.LocationRepository;
import GeoGasto.persistence.repository.RecurringExpenseRepository;
import GeoGasto.persistence.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class RecurringExpenseService {

    private final RecurringExpenseRepository recurringRepository;
    private final ExpenseRepository expenseRepository;
    private final CategoryRepository categoryRepository;
    private final LocationRepository locationRepository;
    private final UserRepository userRepository;

    @Value("${app.recurring.max-per-run:120}")
    private int maxPorEjecucion;

    @Transactional(readOnly = true)
    public List<RecurringExpenseResponse> listar(String email) {
        User user = obtenerUsuario(email);
        return recurringRepository.findByUserIdOrderByProximaFechaAsc(user.getId())
                .stream().map(this::toResponse).toList();
    }

    @Transactional
    public RecurringExpenseResponse crear(String email, RecurringExpenseRequest request) {
        User user = obtenerUsuario(email);
        validarFechas(request);

        RecurringExpense recurring = RecurringExpense.builder()
                .user(user)
                .category(obtenerCategoria(user, request.getCategoryId()))
                .location(obtenerUbicacion(user, request.getLocationId()))
                .monto(request.getMonto())
                .fechaInicio(request.getFechaInicio())
                .proximaFecha(request.getFechaInicio())
                .fechaFin(request.getFechaFin())
                .frecuencia(request.getFrecuencia())
                .descripcion(limpiar(request.getDescripcion()))
                .activo(request.getActivo() == null || request.getActivo())
                .build();

        recurringRepository.save(recurring);
        if (Boolean.TRUE.equals(recurring.getActivo())) {
            generarHasta(recurring, hoyDe(user));
        }
        return toResponse(recurring);
    }

    @Transactional
    public RecurringExpenseResponse actualizar(String email, Long id, RecurringExpenseRequest request) {
        User user = obtenerUsuario(email);
        RecurringExpense recurring = obtenerPropio(user, id);
        validarFechas(request);

        if (!recurring.getFechaInicio().equals(request.getFechaInicio())
                || recurring.getFrecuencia() != request.getFrecuencia()) {
            throw new IllegalArgumentException(
                    "Para cambiar la fecha de inicio o la frecuencia, elimina y crea una nueva recurrencia");
        }

        recurring.setMonto(request.getMonto());
        recurring.setFechaFin(request.getFechaFin());
        recurring.setDescripcion(limpiar(request.getDescripcion()));
        recurring.setCategory(obtenerCategoria(user, request.getCategoryId()));
        recurring.setLocation(obtenerUbicacion(user, request.getLocationId()));
        recurring.setActivo(request.getActivo() == null ? Boolean.TRUE : request.getActivo());

        recurringRepository.save(recurring);
        if (Boolean.TRUE.equals(recurring.getActivo())) {
            generarHasta(recurring, hoyDe(user));
        }
        return toResponse(recurring);
    }

    @Transactional
    public RecurringExpenseResponse omitirSiguiente(String email, Long id) {
        User user = obtenerUsuario(email);
        RecurringExpense recurring = obtenerPropio(user, id);

        LocalDate siguiente = siguiente(recurring.getProximaFecha(), recurring.getFrecuencia());
        recurring.setProximaFecha(siguiente);
        if (recurring.getFechaFin() != null && siguiente.isAfter(recurring.getFechaFin())) {
            recurring.setActivo(false);
        }
        recurringRepository.save(recurring);
        return toResponse(recurring);
    }

    @Transactional
    public void eliminar(String email, Long id) {
        User user = obtenerUsuario(email);
        recurringRepository.delete(obtenerPropio(user, id));
    }

    @Transactional
    public int generarPendientes(String email) {
        User user = obtenerUsuario(email);
        LocalDate hoy = hoyDe(user);
        int generados = 0;

        for (RecurringExpense recurring : recurringRepository.findByUserIdOrderByProximaFechaAsc(user.getId())) {
            if (Boolean.TRUE.equals(recurring.getActivo())) {
                generados += generarHasta(recurring, hoy);
            }
        }
        return generados;
    }

    /**
     * Lo llama el scheduler. Procesa todos los usuarios y respeta la zona horaria de cada uno.
     */
    @Transactional
    public int generarTodosPendientes() {
        int generados = 0;

        for (RecurringExpense recurring : recurringRepository.findByActivoTrue()) {
            generados += generarHasta(recurring, hoyDe(recurring.getUser()));
        }

        if (generados > 0) {
            log.info("Se generaron {} gastos recurrentes", generados);
        }
        return generados;
    }

    private int generarHasta(RecurringExpense recurring, LocalDate hoy) {
        if (!Boolean.TRUE.equals(recurring.getActivo())) {
            return 0;
        }

        LocalDate fecha = recurring.getProximaFecha() != null
                ? recurring.getProximaFecha()
                : recurring.getFechaInicio();
        int generados = 0;

        while (!fecha.isAfter(hoy) && generados < maxPorEjecucion) {
            if (recurring.getFechaFin() != null && fecha.isAfter(recurring.getFechaFin())) {
                break;
            }

            Expense expense = new Expense();
            expense.setUser(recurring.getUser());
            expense.setMonto(recurring.getMonto());
            expense.setFecha(fecha);
            expense.setDescripcion(recurring.getDescripcion());
            expense.setCategory(recurring.getCategory());
            expense.setLocation(recurring.getLocation());
            expenseRepository.save(expense);

            fecha = siguiente(fecha, recurring.getFrecuencia());
            generados++;
        }

        recurring.setProximaFecha(fecha);
        if (recurring.getFechaFin() != null && fecha.isAfter(recurring.getFechaFin())) {
            recurring.setActivo(false);
        }
        recurringRepository.save(recurring);
        return generados;
    }

    private static LocalDate siguiente(LocalDate fecha, RecurringFrequency frecuencia) {
        return switch (frecuencia) {
            case DAILY -> fecha.plusDays(1);
            case WEEKLY -> fecha.plusWeeks(1);
            case MONTHLY -> fecha.plusMonths(1);
            case YEARLY -> fecha.plusYears(1);
        };
    }

    private static void validarFechas(RecurringExpenseRequest request) {
        if (request.getFechaFin() != null && request.getFechaFin().isBefore(request.getFechaInicio())) {
            throw new IllegalArgumentException("La fecha de fin no puede ser anterior a la fecha de inicio");
        }
    }

    private Category obtenerCategoria(User user, Long categoryId) {
        if (categoryId == null) {
            return null;
        }
        Category category = categoryRepository.findById(categoryId)
                .orElseThrow(() -> new ResourceNotFoundException("Categoría no encontrada"));
        if (!category.getUser().getId().equals(user.getId())) {
            throw new ResourceNotFoundException("Categoría no encontrada");
        }
        return category;
    }

    private Location obtenerUbicacion(User user, Long locationId) {
        if (locationId == null) {
            return null;
        }
        return locationRepository.findByIdAndUserId(locationId, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Ubicación no encontrada"));
    }

    private RecurringExpense obtenerPropio(User user, Long id) {
        return recurringRepository.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new ResourceNotFoundException("Gasto recurrente no encontrado"));
    }

    private User obtenerUsuario(String email) {
        return userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
    }

    private static LocalDate hoyDe(User user) {
        try {
            return LocalDate.now(ZoneId.of(user.getTimezone()));
        } catch (DateTimeException | NullPointerException e) {
            return LocalDate.now(ZoneId.of("America/Lima"));
        }
    }

    private static String limpiar(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private RecurringExpenseResponse toResponse(RecurringExpense r) {
        Category category = r.getCategory();
        Location location = r.getLocation();
        return RecurringExpenseResponse.builder()
                .id(r.getId())
                .monto(r.getMonto())
                .descripcion(r.getDescripcion())
                .frecuencia(r.getFrecuencia())
                .fechaInicio(r.getFechaInicio())
                .proximaFecha(r.getProximaFecha())
                .fechaFin(r.getFechaFin())
                .activo(r.getActivo())
                .categoryId(category != null ? category.getId() : null)
                .categoryNombre(category != null ? category.getNombre() : null)
                .categoryColor(category != null ? category.getColor() : null)
                .locationId(location != null ? location.getId() : null)
                .locationDireccion(location != null ? location.getDireccion() : null)
                .createdAt(r.getCreatedAt())
                .build();
    }
}
