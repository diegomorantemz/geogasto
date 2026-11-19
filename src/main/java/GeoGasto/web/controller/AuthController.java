package GeoGasto.web.controller;

import GeoGasto.domain.dto.auth.AuthResponse;
import GeoGasto.domain.dto.auth.ChangePasswordRequest;
import GeoGasto.domain.dto.auth.LoginRequest;
import GeoGasto.domain.dto.auth.RegisterRequest;
import GeoGasto.domain.dto.auth.UpdateProfileRequest;
import GeoGasto.domain.dto.auth.UserProfileResponse;
import GeoGasto.domain.service.AuthService;
import io.swagger.v3.oas.annotations.security.SecurityRequirements;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;

    @SecurityRequirements
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    @SecurityRequirements
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> perfil(Authentication auth) {
        return ResponseEntity.ok(authService.perfil(auth.getName()));
    }

    @PutMapping("/me")
    public ResponseEntity<UserProfileResponse> actualizarPerfil(
            Authentication auth,
            @Valid @RequestBody UpdateProfileRequest request
    ) {
        return ResponseEntity.ok(authService.actualizarPerfil(auth.getName(), request));
    }

    @PutMapping("/me/password")
    public ResponseEntity<Void> cambiarPassword(
            Authentication auth,
            @Valid @RequestBody ChangePasswordRequest request
    ) {
        authService.cambiarPassword(auth.getName(), request);
        return ResponseEntity.noContent().build();
    }
}
