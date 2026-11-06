package pe.edu.aulacita.appointments;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.time.LocalDate;
import java.time.LocalTime;

public final class AppointmentDtos {
    private AppointmentDtos() {
    }

    public record BookingRequest(
            @NotNull Integer studentGuardianRelationId,
            @NotNull Integer teacherId,
            @NotNull LocalDate date,
            @NotNull LocalTime startTime,
            @NotBlank @Size(max = 20) String modality,
            @NotBlank @Size(max = 500) String reason,
            @Size(max = 1000) String comment) {
    }

    public record CancellationRequest(
            @NotBlank @Size(max = 500) String reason,
            @NotBlank String version) {
    }

    public record RescheduleRequest(
            @NotBlank String version,
            @NotNull BookingRequest replacement) {
    }

    public record AttendanceRequest(
            @NotNull Boolean attended,
            @Size(max = 1000) String observation,
            @NotBlank String version) {
    }

    public record AvailabilityRequest(
            Integer teacherId,
            @NotNull @Min(1) @Max(7) Integer dayOfWeek,
            @NotNull LocalTime startTime,
            @NotNull LocalTime endTime,
            @NotNull LocalDate validFrom,
            LocalDate validUntil,
            @NotNull @Min(5) @Max(240) Integer durationMinutes) {
    }

    public record AvailabilityUpdateRequest(
            Integer teacherId,
            @NotNull @Min(1) @Max(7) Integer dayOfWeek,
            @NotNull LocalTime startTime,
            @NotNull LocalTime endTime,
            @NotNull LocalDate validFrom,
            LocalDate validUntil,
            @NotNull @Min(5) @Max(240) Integer durationMinutes,
            @NotNull Boolean active) {
    }

    public record BlockRequest(
            Integer teacherId,
            @NotNull LocalDate date,
            @NotNull LocalTime startTime,
            @NotNull LocalTime endTime,
            @NotBlank @Size(max = 300) String reason) {
    }

    public record AvailabilityView(int id, int teacherId, int dayOfWeek, LocalTime startTime,
                                   LocalTime endTime, LocalDate validFrom, LocalDate validUntil,
                                   int durationMinutes, boolean active) {
    }

    public record BlockView(int id, int teacherId, LocalDate date, LocalTime startTime,
                            LocalTime endTime, String reason) {
    }

    public record AvailableSlot(LocalDate date, LocalTime startTime, LocalTime endTime) {
    }

    public record AppointmentView(
            int id,
            String code,
            int studentGuardianRelationId,
            int teacherId,
            String student,
            String guardian,
            String teacher,
            LocalDate date,
            LocalTime startTime,
            LocalTime endTime,
            String modality,
            String reason,
            String status,
            String version) {
    }

    public record StudentView(int relationId, int studentId, String student, String grade, boolean canBook) {
    }

    public record TeacherView(int id, String name) {
    }

    public record HistoryView(String event, String previousStatus, String newStatus, String detail, String occurredAt) {
    }
}
