package pe.edu.aulacita.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.security.crypto.password.PasswordEncoder;
import pe.edu.aulacita.appointments.AppointmentException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

class PasswordRecoveryServiceTest {
    @Test
    void recoveryIsExplicitlyUnavailableWhenMailIsNotConfigured() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        PasswordRecoveryMailService mail = unconfiguredMail();
        PasswordRecoveryService service = new PasswordRecoveryService(
                jdbc, mock(PasswordEncoder.class), mail, 30);

        assertThatThrownBy(() -> service.requestReset("user@example.test"))
                .isInstanceOf(AppointmentException.class)
                .satisfies(error -> assertThat(((AppointmentException) error).getStatus())
                        .isEqualTo(HttpStatus.SERVICE_UNAVAILABLE));
        verifyNoInteractions(jdbc);
    }

    @Test
    void expiredOrUnknownTokenCannotChangePassword() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        PasswordRecoveryService service = new PasswordRecoveryService(
                jdbc, encoder, unconfiguredMail(), 30);
        doThrow(new EmptyResultDataAccessException(1))
                .when(jdbc).queryForObject(anyString(), eq(Integer.class), anyString());

        assertThatThrownBy(() -> service.resetPassword(
                new PasswordResetRequest("abcdefghijklmnopqrstuvwxABCDEFGHIJKLMNOPQ", "long-enough-password")))
                .isInstanceOf(AppointmentException.class)
                .hasMessageContaining("no es válido o expiró");

        verify(jdbc).queryForObject(anyString(), eq(Integer.class), anyString());
        verifyNoInteractions(encoder);
    }

    private PasswordRecoveryMailService unconfiguredMail() {
        ObjectProvider<JavaMailSender> sender = mock(ObjectProvider.class);
        return new PasswordRecoveryMailService(sender, "", "http://localhost:8080", 30);
    }
}
