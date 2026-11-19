package GeoGasto.domain.service;

import GeoGasto.domain.dto.category.CategoryRequest;
import GeoGasto.domain.dto.category.CategoryResponse;
import GeoGasto.domain.exception.ResourceNotFoundException;
import GeoGasto.persistence.entity.Category;
import GeoGasto.persistence.entity.User;
import GeoGasto.persistence.repository.CategoryRepository;
import GeoGasto.persistence.repository.ExpenseRepository;
import GeoGasto.persistence.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CategoryService {

    private final CategoryRepository categoryRepository;
    private final ExpenseRepository expenseRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public List<CategoryResponse> listar(String email) {
        User user = obtenerUsuario(email);
        return categoryRepository.findByUserId(user.getId()).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public CategoryResponse obtener(String email, Long id) {
        User user = obtenerUsuario(email);
        return toResponse(obtenerPropia(user, id));
    }

    @Transactional
    public CategoryResponse crear(String email, CategoryRequest request) {
        User user = obtenerUsuario(email);
        String nombre = request.getNombre().trim();

        if (categoryRepository.existsByNombreIgnoreCaseAndUserId(nombre, user.getId())) {
            throw new IllegalArgumentException("Ya existe una categoría con ese nombre");
        }

        Category category = Category.builder()
                .nombre(nombre)
                .icono(request.getIcono())
                .color(request.getColor())
                .user(user)
                .build();

        return toResponse(categoryRepository.save(category));
    }

    @Transactional
    public CategoryResponse actualizar(String email, Long id, CategoryRequest request) {
        User user = obtenerUsuario(email);
        Category category = obtenerPropia(user, id);
        String nombre = request.getNombre().trim();

        if (categoryRepository.existsByNombreIgnoreCaseAndUserIdAndIdNot(nombre, user.getId(), id)) {
            throw new IllegalArgumentException("Ya existe una categoría con ese nombre");
        }

        category.setNombre(nombre);
        category.setIcono(request.getIcono());
        category.setColor(request.getColor());

        return toResponse(categoryRepository.save(category));
    }

    @Transactional
    public void eliminar(String email, Long id) {
        User user = obtenerUsuario(email);
        Category category = obtenerPropia(user, id);

        if (expenseRepository.existsByCategoryId(id)) {
            throw new IllegalArgumentException(
                    "No se puede eliminar una categoría que tiene gastos asociados");
        }

        categoryRepository.delete(category);
    }

    private Category obtenerPropia(User user, Long id) {
        Category category = categoryRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Categoría no encontrada"));

        if (!category.getUser().getId().equals(user.getId())) {
            throw new ResourceNotFoundException("Categoría no encontrada");
        }
        return category;
    }

    private User obtenerUsuario(String email) {
        return userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
    }

    private CategoryResponse toResponse(Category category) {
        return CategoryResponse.builder()
                .id(category.getId())
                .nombre(category.getNombre())
                .icono(category.getIcono())
                .color(category.getColor())
                .createdAt(category.getCreatedAt())
                .build();
    }
}
