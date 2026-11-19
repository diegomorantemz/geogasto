package GeoGasto.domain.service;

import GeoGasto.domain.dto.income.IncomeRequest;
import GeoGasto.domain.dto.income.IncomeResponse;
import GeoGasto.domain.exception.ResourceNotFoundException;
import GeoGasto.domain.util.UserTime;
import GeoGasto.persistence.entity.Income;
import GeoGasto.persistence.entity.User;
import GeoGasto.persistence.repository.IncomeRepository;
import GeoGasto.persistence.repository.UserRepository;
import jakarta.persistence.criteria.Predicate;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class IncomeService {

    private final IncomeRepository incomeRepository;
    private final UserRepository userRepository;

    @Transactional(readOnly = true)
    public Page<IncomeResponse> listar(String email, Pageable pageable,
                                       LocalDate desde, LocalDate hasta, String q) {
        User user = obtenerUsuario(email);
        return incomeRepository.findAll(filtros(user.getId(), desde, hasta, q), pageable)
                .map(this::toResponse);
    }

    @Transactional(readOnly = true)
    public IncomeResponse obtener(String email, Long id) {
        User user = obtenerUsuario(email);
        return toResponse(obtenerPropio(user, id));
    }

    @Transactional
    public IncomeResponse crear(String email, IncomeRequest request) {
        User user = obtenerUsuario(email);
        UserTime.requireNotFuture(user, request.getFecha());
        Income income = Income.builder()
                .user(user)
                .monto(request.getMonto())
                .fecha(request.getFecha())
                .descripcion(limpiar(request.getDescripcion()))
                .fuente(limpiar(request.getFuente()))
                .build();
        return toResponse(incomeRepository.save(income));
    }

    @Transactional
    public IncomeResponse actualizar(String email, Long id, IncomeRequest request) {
        User user = obtenerUsuario(email);
        UserTime.requireNotFuture(user, request.getFecha());
        Income income = obtenerPropio(user, id);

        income.setMonto(request.getMonto());
        income.setFecha(request.getFecha());
        income.setDescripcion(limpiar(request.getDescripcion()));
        income.setFuente(limpiar(request.getFuente()));

        return toResponse(incomeRepository.save(income));
    }

    @Transactional
    public void eliminar(String email, Long id) {
        User user = obtenerUsuario(email);
        incomeRepository.delete(obtenerPropio(user, id));
    }

    @Transactional(readOnly = true)
    public BigDecimal sumar(String email, LocalDate desde, LocalDate hasta) {
        User user = obtenerUsuario(email);
        BigDecimal total = incomeRepository.sumarEntreFechas(user.getId(), desde, hasta);
        return total == null ? BigDecimal.ZERO : total;
    }

    private Specification<Income> filtros(Long userId, LocalDate desde, LocalDate hasta, String q) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            predicates.add(cb.equal(root.get("user").get("id"), userId));

            if (desde != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("fecha"), desde));
            }
            if (hasta != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("fecha"), hasta));
            }
            if (q != null && !q.isBlank()) {
                String texto = q.trim().toLowerCase(Locale.ROOT)
                        .replace("\\", "\\\\")
                        .replace("%", "\\%")
                        .replace("_", "\\_");
                String patron = "%" + texto + "%";
                predicates.add(cb.or(
                        cb.like(cb.lower(root.get("descripcion")), patron, '\\'),
                        cb.like(cb.lower(root.get("fuente")), patron, '\\')));
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };
    }

    private Income obtenerPropio(User user, Long id) {
        Income income = incomeRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Ingreso no encontrado"));

        if (!income.getUser().getId().equals(user.getId())) {
            throw new ResourceNotFoundException("Ingreso no encontrado");
        }
        return income;
    }

    private User obtenerUsuario(String email) {
        return userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
    }

    private static String limpiar(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private IncomeResponse toResponse(Income income) {
        return IncomeResponse.builder()
                .id(income.getId())
                .monto(income.getMonto())
                .fecha(income.getFecha())
                .descripcion(income.getDescripcion())
                .fuente(income.getFuente())
                .createdAt(income.getCreatedAt())
                .build();
    }
}
