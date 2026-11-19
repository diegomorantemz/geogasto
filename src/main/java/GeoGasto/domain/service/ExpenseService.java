package GeoGasto.domain.service;

import GeoGasto.domain.dto.expense.ExpenseMapResponse;
import GeoGasto.domain.dto.expense.ExpenseRequest;
import GeoGasto.domain.dto.expense.ExpenseResponse;
import GeoGasto.domain.dto.expense.ExpenseWithLocationRequest;
import GeoGasto.domain.exception.ResourceNotFoundException;
import GeoGasto.persistence.entity.Category;
import GeoGasto.persistence.entity.Expense;
import GeoGasto.persistence.entity.Location;
import GeoGasto.persistence.entity.Receipt;
import GeoGasto.persistence.entity.User;
import GeoGasto.persistence.repository.CategoryRepository;
import GeoGasto.persistence.repository.ExpenseRepository;
import GeoGasto.persistence.repository.LocationRepository;
import GeoGasto.persistence.repository.RecurringExpenseRepository;
import GeoGasto.persistence.repository.SavedPlaceRepository;
import GeoGasto.persistence.repository.UserRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class ExpenseService {

    private static final int LIMITE_MAPA = 5000;

    private final ExpenseRepository expenseRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final LocationRepository locationRepository;
    private final SavedPlaceRepository savedPlaceRepository;
    private final RecurringExpenseRepository recurringExpenseRepository;
    private final ReceiptService receiptService;

    @Transactional(readOnly = true)
    public Page<ExpenseResponse> listar(String email, Pageable pageable) {
        return listar(email, pageable, null, null, null, null);
    }

    @Transactional(readOnly = true)
    public Page<ExpenseResponse> listar(String email, Pageable pageable,
                                        LocalDate desde, LocalDate hasta, Long categoryId) {
        return listar(email, pageable, desde, hasta, categoryId, null);
    }

    @Transactional(readOnly = true)
    public Page<ExpenseResponse> listar(String email, Pageable pageable,
                                        LocalDate desde, LocalDate hasta, Long categoryId, String q) {
        User user = obtenerUsuario(email);
        return expenseRepository.findAll(filtros(user.getId(), desde, hasta, categoryId, q), pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public List<ExpenseMapResponse> listarParaMapa(String email, LocalDate desde, LocalDate hasta,
                                                   Long categoryId, String q) {
        User user = obtenerUsuario(email);
        Specification<Expense> spec = filtros(user.getId(), desde, hasta, categoryId, q)
                .and((root, query, cb) -> cb.isNotNull(root.get("location")));

        Pageable limite = PageRequest.of(0, LIMITE_MAPA, Sort.by(Sort.Direction.DESC, "fecha"));
        return expenseRepository.findAll(spec, limite).stream()
                .map(this::toMapResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public byte[] exportarCsv(String email, LocalDate desde, LocalDate hasta, Long categoryId) {
        return exportarCsv(email, desde, hasta, categoryId, null);
    }

    @Transactional(readOnly = true)
    public byte[] exportarCsv(String email, LocalDate desde, LocalDate hasta,
                              Long categoryId, String q) {
        User user = obtenerUsuario(email);
        List<Expense> gastos = expenseRepository.findAll(
                filtros(user.getId(), desde, hasta, categoryId, q),
                Sort.by(Sort.Direction.DESC, "fecha"));

        StringBuilder sb = new StringBuilder();
        sb.append('\uFEFF'); // BOM para que Excel reconozca UTF-8
        sb.append("Fecha,Descripcion,Categoria,Monto,Ubicacion,Ciudad,Pais,Latitud,Longitud\n");

        for (Expense e : gastos) {
            Location l = e.getLocation();
            sb.append(csv(e.getFecha().toString())).append(',')
                    .append(csv(e.getDescripcion())).append(',')
                    .append(csv(e.getCategory() != null ? e.getCategory().getNombre() : "")).append(',')
                    .append(csv(e.getMonto().toPlainString())).append(',')
                    .append(csv(l != null ? l.getDireccion() : "")).append(',')
                    .append(csv(l != null ? l.getCiudad() : "")).append(',')
                    .append(csv(l != null ? l.getPais() : "")).append(',')
                    .append(csv(l != null && l.getLatitud() != null ? l.getLatitud().toString() : "")).append(',')
                    .append(csv(l != null && l.getLongitud() != null ? l.getLongitud().toString() : ""))
                    .append('\n');
        }

        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Transactional(readOnly = true)
    public ExpenseResponse obtener(String email, Long id) {
        User user = obtenerUsuario(email);
        return toResponse(obtenerPropio(user, id));
    }

    @Transactional
    public ExpenseResponse crear(String email, ExpenseRequest request) {
        User user = obtenerUsuario(email);
        Expense expense = new Expense();
        expense.setUser(user);
        aplicarDatos(expense, request, user);
        return toResponse(expenseRepository.save(expense));
    }

    @Transactional
    public ExpenseResponse crearConUbicacion(String email, ExpenseWithLocationRequest request) {
        User user = obtenerUsuario(email);
        Location location = obtenerOCrearUbicacion(user, request);

        Expense expense = new Expense();
        expense.setUser(user);
        expense.setMonto(request.getMonto());
        expense.setFecha(request.getFecha());
        expense.setDescripcion(request.getDescripcion());
        expense.setCategory(obtenerCategoria(user, request.getCategoryId()));
        expense.setLocation(location);

        return toResponse(expenseRepository.save(expense));
    }

    @Transactional
    public ExpenseResponse actualizar(String email, Long id, ExpenseRequest request) {
        User user = obtenerUsuario(email);
        Expense expense = obtenerPropio(user, id);
        Location ubicacionAnterior = expense.getLocation();

        aplicarDatos(expense, request, user);
        ExpenseResponse response = toResponse(expenseRepository.save(expense));
        expenseRepository.flush();
        eliminarUbicacionSiHuerfana(ubicacionAnterior);
        return response;
    }

    @Transactional
    public ExpenseResponse actualizarConUbicacion(String email, Long id, ExpenseWithLocationRequest request) {
        User user = obtenerUsuario(email);
        Expense expense = obtenerPropio(user, id);
        Location ubicacionAnterior = expense.getLocation();

        expense.setMonto(request.getMonto());
        expense.setFecha(request.getFecha());
        expense.setDescripcion(request.getDescripcion());
        expense.setCategory(obtenerCategoria(user, request.getCategoryId()));
        expense.setLocation(obtenerOCrearUbicacion(user, request));

        ExpenseResponse response = toResponse(expenseRepository.save(expense));
        expenseRepository.flush();
        eliminarUbicacionSiHuerfana(ubicacionAnterior);
        return response;
    }

    @Transactional
    public void eliminar(String email, Long id) {
        User user = obtenerUsuario(email);
        Expense expense = obtenerPropio(user, id);

        Location ubicacion = expense.getLocation();
        receiptService.eliminarArchivoDeGasto(expense.getId());
        expenseRepository.delete(expense);
        expenseRepository.flush();
        eliminarUbicacionSiHuerfana(ubicacion);
    }

    private Location obtenerOCrearUbicacion(User user, ExpenseWithLocationRequest request) {
        if (request.getLocationLatitud() == null || request.getLocationLongitud() == null) {
            return null;
        }

        String placeId = request.getLocationPlaceId();
        if (placeId != null && !placeId.isBlank()) {
            var existente = locationRepository
                    .findFirstByUserIdAndPlaceIdOrderByIdAsc(user.getId(), placeId);
            if (existente.isPresent()) {
                return existente.get();
            }
        }

        return locationRepository.save(Location.builder()
                .direccion(request.getLocationDireccion())
                .latitud(request.getLocationLatitud())
                .longitud(request.getLocationLongitud())
                .ciudad(request.getLocationCiudad())
                .pais(request.getLocationPais())
                .placeId(placeId)
                .user(user)
                .build());
    }

    private void eliminarUbicacionSiHuerfana(Location location) {
        if (location == null) {
            return;
        }

        boolean usadaPorGasto = expenseRepository.existsByLocationId(location.getId());
        boolean usadaPorLugar = savedPlaceRepository.existsByLocationId(location.getId());
        boolean usadaPorRecurrente = recurringExpenseRepository.existsByLocationId(location.getId());
        if (!usadaPorGasto && !usadaPorLugar && !usadaPorRecurrente) {
            locationRepository.delete(location);
        }
    }

    private void aplicarDatos(Expense expense, ExpenseRequest request, User user) {
        expense.setMonto(request.getMonto());
        expense.setFecha(request.getFecha());
        expense.setDescripcion(request.getDescripcion());
        expense.setCategory(obtenerCategoria(user, request.getCategoryId()));

        if (request.getLocationId() != null) {
            Location location = locationRepository.findByIdAndUserId(request.getLocationId(), user.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Ubicación no encontrada"));
            expense.setLocation(location);
        } else {
            expense.setLocation(null);
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

    private Specification<Expense> filtros(Long userId, LocalDate desde, LocalDate hasta,
                                           Long categoryId, String q) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("user").get("id"), userId));

            if (desde != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("fecha"), desde));
            }
            if (hasta != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("fecha"), hasta));
            }
            if (categoryId != null) {
                predicates.add(cb.equal(root.get("category").get("id"), categoryId));
            }
            if (q != null && !q.isBlank()) {
                String texto = q.trim().toLowerCase(Locale.ROOT)
                        .replace("\\", "\\\\")
                        .replace("%", "\\%")
                        .replace("_", "\\_");
                predicates.add(cb.like(cb.lower(root.get("descripcion")), "%" + texto + "%", '\\'));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static String csv(String value) {
        if (value == null) {
            return "";
        }

        // Mitiga CSV injection en Excel/Sheets.
        String safe = value;
        if (!safe.isEmpty() && "=+-@\t\r".indexOf(safe.charAt(0)) >= 0) {
            safe = "'" + safe;
        }

        String escaped = safe.replace("\"", "\"\"");
        if (escaped.contains(",") || escaped.contains("\"")
                || escaped.contains("\n") || escaped.contains("\r")) {
            return "\"" + escaped + "\"";
        }
        return escaped;
    }

    private User obtenerUsuario(String email) {
        return userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
    }

    private Expense obtenerPropio(User user, Long id) {
        Expense expense = expenseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Gasto no encontrado"));

        if (!expense.getUser().getId().equals(user.getId())) {
            throw new ResourceNotFoundException("Gasto no encontrado");
        }
        return expense;
    }

    private ExpenseResponse toResponse(Expense expense) {
        Location l = expense.getLocation();
        Receipt r = expense.getReceipt();
        return ExpenseResponse.builder()
                .id(expense.getId())
                .monto(expense.getMonto())
                .fecha(expense.getFecha())
                .descripcion(expense.getDescripcion())
                .categoryId(expense.getCategory() != null ? expense.getCategory().getId() : null)
                .categoryNombre(expense.getCategory() != null ? expense.getCategory().getNombre() : null)
                .locationId(l != null ? l.getId() : null)
                .locationDireccion(l != null ? l.getDireccion() : null)
                .locationLatitud(l != null ? l.getLatitud() : null)
                .locationLongitud(l != null ? l.getLongitud() : null)
                .locationCiudad(l != null ? l.getCiudad() : null)
                .locationPais(l != null ? l.getPais() : null)
                .receiptId(r != null ? r.getId() : null)
                .receiptNombre(r != null ? r.getNombreOriginal() : null)
                .receiptContentType(r != null ? r.getContentType() : null)
                .receiptTamano(r != null ? r.getTamano() : null)
                .createdAt(expense.getCreatedAt())
                .build();
    }

    private ExpenseMapResponse toMapResponse(Expense expense) {
        Location l = expense.getLocation();
        return ExpenseMapResponse.builder()
                .id(expense.getId())
                .monto(expense.getMonto())
                .fecha(expense.getFecha())
                .descripcion(expense.getDescripcion())
                .categoryNombre(expense.getCategory() != null ? expense.getCategory().getNombre() : null)
                .categoryColor(expense.getCategory() != null ? expense.getCategory().getColor() : null)
                .locationId(l.getId())
                .direccion(l.getDireccion())
                .latitud(l.getLatitud())
                .longitud(l.getLongitud())
                .ciudad(l.getCiudad())
                .pais(l.getPais())
                .build();
    }
}
