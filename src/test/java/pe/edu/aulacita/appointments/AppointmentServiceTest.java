package pe.edu.aulacita.appointments;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import pe.edu.aulacita.notifications.NotificationService;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class AppointmentServiceTest {
    @Test
    void rejectsPastDatesBeforeQueryingTheDatabase() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        AppointmentService service = new AppointmentService(jdbc, mock(NotificationService.class));

        assertThatThrownBy(() -> service.availableSlots(1,
                LocalDate.now(ZoneId.of("America/Lima")).minusDays(1), "secretaria@example.test", "SECRETARIA", null))
                .isInstanceOf(AppointmentException.class)
                .hasMessageContaining("futura");

        verifyNoInteractions(jdbc);
    }

    @Test
    void returnsNoSlotsWhenTheTeacherHasNoActiveWindow() {
        JdbcTemplate jdbc = mock(JdbcTemplate.class);
        when(jdbc.queryForObject(anyString(), eq(Integer.class), any(Object[].class))).thenReturn(1);
        when(jdbc.queryForList(anyString(), any(Object[].class))).thenReturn(List.of());
        AppointmentService service = new AppointmentService(jdbc, mock(NotificationService.class));

        List<AppointmentDtos.AvailableSlot> slots = service.availableSlots(
                1, LocalDate.now(ZoneId.of("America/Lima")).plusDays(7),
                "secretaria@example.test", "SECRETARIA", null);

        assertThat(slots).isEmpty();
    }
}
