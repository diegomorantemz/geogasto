package GeoGasto.domain.service;

import GeoGasto.config.JwtService;
import GeoGasto.domain.dto.auth.AuthResponse;
import GeoGasto.domain.dto.auth.ChangePasswordRequest;
import GeoGasto.domain.dto.auth.LoginRequest;
import GeoGasto.domain.dto.auth.RegisterRequest;
import GeoGasto.domain.dto.auth.UpdateProfileRequest;
import GeoGasto.domain.dto.auth.UserProfileResponse;
import GeoGasto.domain.exception.ResourceNotFoundException;
import GeoGasto.persistence.entity.Category;
import GeoGasto.persistence.entity.User;
import GeoGasto.persistence.repository.CategoryRepository;
import GeoGasto.persistence.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DateTimeException;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final List<DefaultCategory> DEFAULT_CATEGORIES = List.of(
            new DefaultCategory("Comida", "bi-cart", "#8c6a5d"),
            new DefaultCategory("Transporte", "bi-car-front", "#7d7f72"),
            new DefaultCategory("Hogar", "bi-house", "#6f7f6a"),
            new DefaultCategory("Ocio", "bi-controller", "#8a7a5c"),
            new DefaultCategory("Salud", "bi-heart-pulse", "#8c6b6b"),
            new DefaultCategory("Educación", "bi-mortarboard", "#5f7482"),
            new DefaultCategory("Otros", "bi-receipt", "#8a8378")
    );

    private final UserRepository userRepository;
    private final CategoryRepository categoryRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    @Value("${app.seed-default-categories:true}")
    private boolean seedDefaultCategories;

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        String email = normalizarEmail(request.getEmail());

        if (userRepository.existsByEmailIgnoreCase(email)) {
            throw new IllegalArgumentException("El email ya está registrado");
        }

        String timezone = request.getTimezone() != null ? request.getTimezone() : "America/Lima";
        validarTimezone(timezone);

        User user = User.builder()
                .email(email)
                .password(passwordEncoder.encode(request.getPassword()))
                .nombre(request.getNombre().trim())
                .moneda(request.getMoneda() != null ? request.getMoneda() : "USD")
                .timezone(timezone)
                .build();

        userRepository.save(user);

        if (seedDefaultCategories) {
            categoryRepository.saveAll(DEFAULT_CATEGORIES.stream()
                    .map(c -> Category.builder()
                            .nombre(c.nombre())
                            .icono(c.icono())
                            .color(c.color())
                            .user(user)
                            .build())
                    .toList());
        }

        return authResponse(user, generateToken(user));
    }

    public AuthResponse login(LoginRequest request) {
        String email = normalizarEmail(request.getEmail());

        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(email, request.getPassword())
        );

        User user = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));

        return authResponse(user, generateToken(user));
    }

    @Transactional(readOnly = true)
    public UserProfileResponse perfil(String email) {
        return toProfile(obtenerUsuario(email));
    }

    @Transactional
    public UserProfileResponse actualizarPerfil(String email, UpdateProfileRequest request) {
        User user = obtenerUsuario(email);

        if (request.getNombre() != null && !request.getNombre().isBlank()) {
            user.setNombre(request.getNombre().trim());
        }
        if (request.getMoneda() != null) {
            user.setMoneda(request.getMoneda());
        }
        if (request.getTimezone() != null && !request.getTimezone().isBlank()) {
            validarTimezone(request.getTimezone());
            user.setTimezone(request.getTimezone());
        }

        userRepository.save(user);
        return toProfile(user);
    }

    @Transactional
    public void cambiarPassword(String email, ChangePasswordRequest request) {
        User user = obtenerUsuario(email);

        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPassword())) {
            throw new IllegalArgumentException("La contraseña actual no es correcta");
        }

        user.setPassword(passwordEncoder.encode(request.getNewPassword()));
        userRepository.save(user);
    }

    private String generateToken(User user) {
        UserDetails userDetails = org.springframework.security.core.userdetails.User.builder()
                .username(user.getEmail())
                .password(user.getPassword())
                .authorities(java.util.Collections.emptyList())
                .build();

        return jwtService.generateToken(userDetails);
    }

    private AuthResponse authResponse(User user, String token) {
        return AuthResponse.builder()
                .token(token)
                .email(user.getEmail())
                .nombre(user.getNombre())
                .moneda(user.getMoneda())
                .timezone(user.getTimezone())
                .build();
    }

    private User obtenerUsuario(String email) {
        return userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
    }

    private UserProfileResponse toProfile(User user) {
        return UserProfileResponse.builder()
                .email(user.getEmail())
                .nombre(user.getNombre())
                .moneda(user.getMoneda())
                .timezone(user.getTimezone())
                .build();
    }

    private static String normalizarEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    private static void validarTimezone(String timezone) {
        try {
            ZoneId.of(timezone);
        } catch (DateTimeException e) {
            throw new IllegalArgumentException("Zona horaria inválida: " + timezone);
        }
    }

    private record DefaultCategory(String nombre, String icono, String color) {
    }
}
