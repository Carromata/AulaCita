package pe.edu.aulacita.school;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import pe.edu.aulacita.appointments.AppointmentException;
import pe.edu.aulacita.school.SchoolDtos.UserCreateRequest;
import pe.edu.aulacita.school.SchoolDtos.UserUpdateRequest;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class SchoolServiceTest {
    @TempDir
    Path tempDir;

    @ParameterizedTest
    @ValueSource(strings = {"Administrador", "Secretaria", "Director"})
    void administratorCannotCreatePrivilegedOrAdministrativeAccounts(String requestedRole) throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        SchoolService service = service(jdbc);
        UserCreateRequest request = new UserCreateRequest(
                "user@example.test", "a-very-long-password", "Nombre", "Apellido", null, requestedRole,
                null, null, null);

        assertThatThrownBy(() -> service.createUser("ADMINISTRADOR", request))
                .isInstanceOf(AppointmentException.class)
                .hasMessageContaining("asignar ese rol");

        verifyNoInteractions(jdbc);
    }

    @Test
    void administratorCannotPromoteAnAccountToSuperAdministrator() throws Exception {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        SchoolService service = service(jdbc);
        UserUpdateRequest request = new UserUpdateRequest(
                "Nombre", "Apellido", null, "SuperAdministrador", true,
                null, null, null);

        assertThatThrownBy(() -> service.updateUser("ADMINISTRADOR", "admin@example.test", 7, request))
                .isInstanceOf(AppointmentException.class)
                .hasMessageContaining("asignar ese rol");

        verifyNoInteractions(jdbc);
    }

    private SchoolService service(JdbcTemplate jdbc) throws Exception {
        return new SchoolService(jdbc, mock(PasswordEncoder.class), tempDir.toString());
    }
}
