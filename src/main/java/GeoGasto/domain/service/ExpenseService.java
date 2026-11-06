package GeoGasto.domain.service;

import GeoGasto.domain.dto.expense.ExpenseRequest;
import GeoGasto.domain.dto.expense.ExpenseResponse;
import GeoGasto.domain.dto.expense.ExpenseWithLocationRequest;
import GeoGasto.domain.exception.ResourceNotFoundException;
import GeoGasto.persistence.entity.Category;
import GeoGasto.persistence.entity.Expense;
import GeoGasto.persistence.entity.Location;
import GeoGasto.persistence.entity.User;
import GeoGasto.persistence.repository.CategoryRepository;
import GeoGasto.persistence.repository.ExpenseRepository;
import GeoGasto.persistence.repository.LocationRepository;
import GeoGasto.persistence.repository.SavedPlaceRepository;
import GeoGasto.persistence.repository.UserRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final LocationRepository locationRepository;
    private final SavedPlaceRepository savedPlaceRepository;

    @Transactional(readOnly = true)
    public Page<ExpenseResponse> listar(String email, Pageable pageable) {
        return listar(email, pageable, null, null, null);
    }

    @Transactional(readOnly = true)
    public Page<ExpenseResponse> listar(String email, Pageable pageable,
                                        LocalDate desde, LocalDate hasta, Long categoryId) {
        User user = obtenerUsuario(email);
        return expenseRepository.findAll(filtros(user.getId(), desde, hasta, categoryId), pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public byte[] exportarCsv(String email, LocalDate desde, LocalDate hasta, Long categoryId) {
        User user = obtenerUsuario(email);
        List<Expense> gastos = expenseRepository.findAll(
                filtros(user.getId(), desde, hasta, categoryId),
                Sort.by(Sort.Direction.DESC, "fecha"));

        StringBuilder sb = new StringBuilder();
        sb.append('\uFEFF'); // BOM para que Excel reconozca UTF-8
        sb.append("Fecha,Descripcion,Categoria,Monto,Ubicacion,Ciudad,Pais\n");

        for (Expense e : gastos) {
            Location l = e.getLocation();
            sb.append(csv(e.getFecha().toString())).append(',')
                    .append(csv(e.getDescripcion())).append(',')
                    .append(csv(e.getCategory() != null ? e.getCategory().getNombre() : "")).append(',')
                    .append(csv(e.getMonto().toPlainString())).append(',')
                    .append(csv(l != null ? l.getDireccion() : "")).append(',')
                    .append(csv(l != null ? l.getCiudad() : "")).append(',')
                    .append(csv(l != null ? l.getPais() : ""))
                    .append('\n');
        }

        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Transactional(readOnly = true)
    public ExpenseResponse obtener(String email, Long id) {
        User user = obtenerUsuario(email);
        Expense expense = expenseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Gasto no encontrado"));

        if (!expense.getUser().getId().equals(user.getId())) {
            throw new ResourceNotFoundException("Gasto no encontrado");
        }
        return toResponse(expense);
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

        Location location = null;
        if (request.getLocationLatitud() != null && request.getLocationLongitud() != null) {
            location = Location.builder()
                    .direccion(request.getLocationDireccion())
                    .latitud(request.getLocationLatitud())
                    .longitud(request.getLocationLongitud())
                    .ciudad(request.getLocationCiudad())
                    .pais(request.getLocationPais())
                    .placeId(request.getLocationPlaceId())
                    .user(user)
                    .build();
            location = locationRepository.save(location);
        }

        Expense expense = new Expense();
        expense.setUser(user);
        expense.setMonto(request.getMonto());
        expense.setFecha(request.getFecha());
        expense.setDescripcion(request.getDescripcion());

        if (request.getCategoryId() != null) {
            Category category = categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("Categoría no encontrada"));
            if (!category.getUser().getId().equals(user.getId())) {
                throw new ResourceNotFoundException("Categoría no encontrada");
            }
            expense.setCategory(category);
        }

        expense.setLocation(location);

        return toResponse(expenseRepository.save(expense));
    }

    @Transactional
    public ExpenseResponse actualizar(String email, Long id, ExpenseRequest request) {
        User user = obtenerUsuario(email);
        Expense expense = expenseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Gasto no encontrado"));

        if (!expense.getUser().getId().equals(user.getId())) {
            throw new ResourceNotFoundException("Gasto no encontrado");
        }

        Location ubicacionAnterior = expense.getLocation();

        aplicarDatos(expense, request, user);
        ExpenseResponse response = toResponse(expenseRepository.save(expense));
        expenseRepository.flush();
        eliminarUbicacionSiHuerfana(ubicacionAnterior);
        return response;
    }

    @Transactional
    public void eliminar(String email, Long id) {
        User user = obtenerUsuario(email);
        Expense expense = expenseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Gasto no encontrado"));

        if (!expense.getUser().getId().equals(user.getId())) {
            throw new ResourceNotFoundException("Gasto no encontrado");
        }

        Location ubicacion = expense.getLocation();
        expenseRepository.delete(expense);
        expenseRepository.flush();
        eliminarUbicacionSiHuerfana(ubicacion);
    }

    private void eliminarUbicacionSiHuerfana(Location location) {
        if (location == null) {
            return;
        }

        boolean usadaPorGasto = expenseRepository.existsByLocationId(location.getId());
        boolean usadaPorLugar = savedPlaceRepository.existsByLocationId(location.getId());
        if (!usadaPorGasto && !usadaPorLugar) {
            locationRepository.delete(location);
        }
    }

    private void aplicarDatos(Expense expense, ExpenseRequest request, User user) {
        expense.setMonto(request.getMonto());
        expense.setFecha(request.getFecha());
        expense.setDescripcion(request.getDescripcion());

        if (request.getCategoryId() != null) {
            Category category = categoryRepository.findById(request.getCategoryId())
                    .orElseThrow(() -> new ResourceNotFoundException("Categoría no encontrada"));
            if (!category.getUser().getId().equals(user.getId())) {
                throw new ResourceNotFoundException("Categoría no encontrada");
            }
            expense.setCategory(category);
        } else {
            expense.setCategory(null);
        }

        if (request.getLocationId() != null) {
            Location location = locationRepository.findByIdAndUserId(request.getLocationId(), user.getId())
                    .orElseThrow(() -> new ResourceNotFoundException("Ubicación no encontrada"));
            expense.setLocation(location);
        } else {
            expense.setLocation(null);
        }
    }

    private Specification<Expense> filtros(Long userId, LocalDate desde, LocalDate hasta, Long categoryId) {
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

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private static String csv(String value) {
        if (value == null) {
            return "";
        }
        String escaped = value.replace("\"", "\"\"");
        if (escaped.contains(",") || escaped.contains("\"")
                || escaped.contains("\n") || escaped.contains("\r")) {
            return "\"" + escaped + "\"";
        }
        return escaped;
    }

    private User obtenerUsuario(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
    }

    private ExpenseResponse toResponse(Expense expense) {
        return ExpenseResponse.builder()
                .id(expense.getId())
                .monto(expense.getMonto())
                .fecha(expense.getFecha())
                .descripcion(expense.getDescripcion())
                .categoryId(expense.getCategory() != null ? expense.getCategory().getId() : null)
                .categoryNombre(expense.getCategory() != null ? expense.getCategory().getNombre() : null)
                .locationId(expense.getLocation() != null ? expense.getLocation().getId() : null)
                .locationDireccion(expense.getLocation() != null ? expense.getLocation().getDireccion() : null)
                .createdAt(expense.getCreatedAt())
                .build();
    }
}