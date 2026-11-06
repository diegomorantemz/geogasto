package GeoGasto.domain.service;

import GeoGasto.config.JwtService;
import GeoGasto.domain.dto.auth.AuthResponse;
import GeoGasto.domain.dto.auth.LoginRequest;
import GeoGasto.domain.dto.auth.RegisterRequest;
import GeoGasto.domain.dto.auth.UpdateProfileRequest;
import GeoGasto.domain.dto.auth.UserProfileResponse;
import GeoGasto.domain.exception.ResourceNotFoundException;
import GeoGasto.persistence.entity.User;
import GeoGasto.persistence.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;

    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new IllegalArgumentException("El email ya está registrado");
        }

        User user = User.builder()
                .email(request.getEmail())
                .password(passwordEncoder.encode(request.getPassword()))
                .nombre(request.getNombre())
                .moneda(request.getMoneda() != null ? request.getMoneda() : "USD")
                .timezone(request.getTimezone() != null ? request.getTimezone() : "America/Lima")
                .build();

        userRepository.save(user);

        UserDetails userDetails = org.springframework.security.core.userdetails.User.builder()
                .username(user.getEmail())
                .password(user.getPassword())
                .authorities(java.util.Collections.emptyList())
                .build();

        String token = jwtService.generateToken(userDetails);
        return AuthResponse.builder().token(token).moneda(user.getMoneda()).build();
    }

    public AuthResponse login(LoginRequest request) {
        authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(request.getEmail(), request.getPassword())
        );

        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));

        UserDetails userDetails = org.springframework.security.core.userdetails.User.builder()
                .username(user.getEmail())
                .password(user.getPassword())
                .authorities(java.util.Collections.emptyList())
                .build();

        String token = jwtService.generateToken(userDetails);
        return AuthResponse.builder().token(token).moneda(user.getMoneda()).build();
    }

    @Transactional(readOnly = true)
    public UserProfileResponse perfil(String email) {
        return toProfile(obtenerUsuario(email));
    }

    @Transactional
    public UserProfileResponse actualizarPerfil(String email, UpdateProfileRequest request) {
        User user = obtenerUsuario(email);

        if (request.getNombre() != null && !request.getNombre().isBlank()) {
            user.setNombre(request.getNombre());
        }
        if (request.getMoneda() != null) {
            user.setMoneda(request.getMoneda());
        }
        if (request.getTimezone() != null && !request.getTimezone().isBlank()) {
            user.setTimezone(request.getTimezone());
        }

        userRepository.save(user);
        return toProfile(user);
    }

    private User obtenerUsuario(String email) {
        return userRepository.findByEmail(email)
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
}