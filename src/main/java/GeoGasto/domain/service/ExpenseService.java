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
import GeoGasto.persistence.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ExpenseService {

    private final ExpenseRepository expenseRepository;
    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final LocationRepository locationRepository;

    @Transactional(readOnly = true)
    public Page<ExpenseResponse> listar(String email, Pageable pageable) {
        User user = obtenerUsuario(email);
        return expenseRepository.findByUserId(user.getId(), pageable)
                .map(this::toResponse);
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

        aplicarDatos(expense, request, user);
        return toResponse(expenseRepository.save(expense));
    }

    @Transactional
    public void eliminar(String email, Long id) {
        User user = obtenerUsuario(email);
        Expense expense = expenseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Gasto no encontrado"));

        if (!expense.getUser().getId().equals(user.getId())) {
            throw new ResourceNotFoundException("Gasto no encontrado");
        }
        expenseRepository.delete(expense);
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
            Location location = locationRepository.findById(request.getLocationId())
                    .orElseThrow(() -> new ResourceNotFoundException("Ubicación no encontrada"));
            expense.setLocation(location);
        } else {
            expense.setLocation(null);
        }
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