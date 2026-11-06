package GeoGasto;

import GeoGasto.persistence.entity.User;
import GeoGasto.persistence.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.annotation.DirtiesContext;

import static org.assertj.core.api.Assertions.assertThat;

@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RepositorySmokeTest extends AbstractIntegrationTest {

    @Autowired
    private UserRepository userRepository;

    @Test
    void puedeGuardarYLeerUsuario() {
        User u = User.builder()
                .email("test@geogasto.com")
                .password("hash_de_prueba")
                .nombre("Usuario Test")
                .build();

        User guardado = userRepository.save(u);
        assertThat(guardado.getId()).isNotNull();

        var encontrado = userRepository.findByEmail("test@geogasto.com");
        assertThat(encontrado).isPresent();

        // limpieza
        userRepository.delete(guardado);
    }
}
