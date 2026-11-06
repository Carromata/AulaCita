package pe.edu.aulacita.appointments;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import pe.edu.aulacita.security.AccountService;
import pe.edu.aulacita.security.PasswordChangeRequest;
import pe.edu.aulacita.security.PasswordRecoveryRequest;
import pe.edu.aulacita.security.PasswordRecoveryService;
import pe.edu.aulacita.security.PasswordResetRequest;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static pe.edu.aulacita.appointments.AppointmentDtos.*;

@RestController
@RequestMapping("/api/v1")
public class AppointmentApiController {
    private final AppointmentService appointments;
    private final AccountService accounts;
    private final PasswordRecoveryService passwordRecovery;

    public AppointmentApiController(AppointmentService appointments, AccountService accounts,
                                    PasswordRecoveryService passwordRecovery) {
        this.appointments = appointments;
        this.accounts = accounts;
        this.passwordRecovery = passwordRecovery;
    }

    @GetMapping("/auth/me")
    public ApiResponse<Map<String, Object>> me(@AuthenticationPrincipal UserDetails user) {
        Map<String, Object> data = new java.util.LinkedHashMap<>();
        data.put("email", user.getUsername());
        data.put("roles", user.getAuthorities().stream().map(authority -> authority.getAuthority()).toList());
        data.put("teacherId", appointments.teacherIdFor(user.getUsername()));
        data.put("guardianId", appointments.guardianIdFor(user.getUsername()));
        return ApiResponse.of(data);
    }

    @GetMapping("/sistema/salud")
    public ApiResponse<Map<String, String>> databaseHealth() {
        appointments.checkDatabaseConnection();
        return ApiResponse.of(Map.of("status", "ok", "database", "connected"));
    }

    @PostMapping("/auth/cambiar-contrasena")
    public ApiResponse<Map<String, String>> changePassword(
            @AuthenticationPrincipal UserDetails user,
            @Valid @RequestBody PasswordChangeRequest request) {
        accounts.changePassword(user.getUsername(), request);
        return ApiResponse.of(Map.of("message", "La contraseña se actualizó correctamente."));
    }

    @PostMapping("/auth/recuperar-contrasena")
    public ApiResponse<Map<String, String>> requestPasswordReset(
            @Valid @RequestBody PasswordRecoveryRequest request) {
        passwordRecovery.requestReset(request.email());
        return ApiResponse.of(Map.of("message",
                "Si existe una cuenta activa con ese correo, recibirás instrucciones para recuperar el acceso."));
    }

    @PostMapping("/auth/restablecer-contrasena")
    public ApiResponse<Map<String, String>> resetPassword(
            @Valid @RequestBody PasswordResetRequest request) {
        passwordRecovery.resetPassword(request);
        return ApiResponse.of(Map.of("message", "La contraseña se actualizó. Ya puedes iniciar sesión."));
    }

    @GetMapping("/mis-estudiantes")
    @PreAuthorize("hasRole('APODERADO')")
    public ApiResponse<List<StudentView>> myStudents(@AuthenticationPrincipal UserDetails user) {
        return ApiResponse.of(appointments.myStudents(user.getUsername()));
    }

    @GetMapping("/relaciones")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','SUPERADMINISTRADOR','SECRETARIA','DOCENTE','DIRECTOR')")
    public ApiResponse<List<StudentView>> relations() {
        return ApiResponse.of(appointments.bookableRelations());
    }

    @GetMapping("/docentes")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','SUPERADMINISTRADOR','SECRETARIA','DOCENTE','APODERADO')")
    public ApiResponse<List<TeacherView>> teachers() {
        return ApiResponse.of(appointments.teachers());
    }

    @GetMapping("/disponibilidades")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','SUPERADMINISTRADOR','SECRETARIA','DOCENTE','APODERADO')")
    public ApiResponse<List<AvailableSlot>> available(
            @AuthenticationPrincipal UserDetails user,
            @RequestParam int docenteId,
            @RequestParam LocalDate fecha,
            @RequestParam(required = false) Integer relacionId) {
        return ApiResponse.of(appointments.availableSlots(docenteId, fecha, user.getUsername(), role(user), relacionId));
    }

    @GetMapping("/disponibilidades/mias")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','SUPERADMINISTRADOR','SECRETARIA','DOCENTE')")
    public ApiResponse<List<AvailabilityView>> myAvailability(@AuthenticationPrincipal UserDetails user) {
        return ApiResponse.of(appointments.myAvailability(user.getUsername(), role(user)));
    }

    @PostMapping("/disponibilidades")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','SUPERADMINISTRADOR','SECRETARIA','DOCENTE')")
    public ApiResponse<AvailabilityView> createAvailability(
            @AuthenticationPrincipal UserDetails user, @Valid @RequestBody AvailabilityRequest request) {
        return ApiResponse.of(appointments.createAvailability(user.getUsername(), role(user), request));
    }

    @PutMapping("/disponibilidades/{id}")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','SUPERADMINISTRADOR','SECRETARIA','DOCENTE')")
    public ApiResponse<AvailabilityView> updateAvailability(
            @AuthenticationPrincipal UserDetails user, @PathVariable int id,
            @Valid @RequestBody AvailabilityUpdateRequest request) {
        return ApiResponse.of(appointments.updateAvailability(user.getUsername(), role(user), id, request));
    }

    @GetMapping("/bloqueos")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','SUPERADMINISTRADOR','SECRETARIA','DOCENTE')")
    public ApiResponse<List<BlockView>> blocks(@AuthenticationPrincipal UserDetails user,
                                               @RequestParam LocalDate desde,
                                               @RequestParam LocalDate hasta) {
        return ApiResponse.of(appointments.blocks(user.getUsername(), role(user), desde, hasta));
    }

    @PostMapping("/bloqueos")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','SUPERADMINISTRADOR','SECRETARIA','DOCENTE')")
    public ApiResponse<BlockView> createBlock(
            @AuthenticationPrincipal UserDetails user, @Valid @RequestBody BlockRequest request) {
        return ApiResponse.of(appointments.createBlock(user.getUsername(), role(user), request));
    }

    @GetMapping("/citas")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','SUPERADMINISTRADOR','SECRETARIA','DOCENTE','APODERADO')")
    public ApiResponse<List<AppointmentView>> list(
            @AuthenticationPrincipal UserDetails user,
            @RequestParam(defaultValue = "0") @Min(0) int pagina,
            @RequestParam(defaultValue = "20") @Min(1) @Max(100) int limite) {
        if (pagina < 0 || pagina > 100_000 || limite < 1 || limite > 100
                || (long) pagina * limite > Integer.MAX_VALUE) {
            throw new AppointmentException(HttpStatus.BAD_REQUEST, "Los valores de paginación no son válidos.");
        }
        String role = role(user);
        return ApiResponse.of(appointments.appointments(user.getUsername(), role, pagina, limite));
    }

    @PostMapping("/citas")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','SUPERADMINISTRADOR','SECRETARIA','DOCENTE','APODERADO')")
    public ApiResponse<AppointmentView> reserve(
            @AuthenticationPrincipal UserDetails user, @Valid @RequestBody BookingRequest request) {
        return ApiResponse.of(appointments.reserve(user.getUsername(), role(user), request));
    }

    @PostMapping("/citas/{id}/confirmar")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','SUPERADMINISTRADOR','SECRETARIA','DOCENTE')")
    public ApiResponse<AppointmentView> confirm(@AuthenticationPrincipal UserDetails user,
                                                @PathVariable int id,
                                                @RequestHeader("If-Match") String version) {
        return ApiResponse.of(appointments.confirm(user.getUsername(), role(user), id, version));
    }

    @PostMapping("/citas/{id}/cancelar")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','SUPERADMINISTRADOR','SECRETARIA','DOCENTE','APODERADO')")
    public ApiResponse<AppointmentView> cancel(@AuthenticationPrincipal UserDetails user,
                                               @PathVariable int id,
                                               @Valid @RequestBody CancellationRequest request) {
        return ApiResponse.of(appointments.cancel(user.getUsername(), role(user), id, request));
    }

    @PostMapping("/citas/{id}/reprogramar")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','SUPERADMINISTRADOR','SECRETARIA','DOCENTE','APODERADO')")
    public ApiResponse<AppointmentView> reschedule(@AuthenticationPrincipal UserDetails user,
                                                   @PathVariable int id,
                                                   @Valid @RequestBody RescheduleRequest request) {
        return ApiResponse.of(appointments.reschedule(user.getUsername(), role(user), id, request));
    }

    @PostMapping("/citas/{id}/asistencia")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','SUPERADMINISTRADOR','SECRETARIA','DOCENTE')")
    public ApiResponse<AppointmentView> attendance(@AuthenticationPrincipal UserDetails user,
                                                   @PathVariable int id,
                                                   @Valid @RequestBody AttendanceRequest request) {
        return ApiResponse.of(appointments.registerAttendance(user.getUsername(), role(user), id, request));
    }

    @GetMapping("/citas/{id}/historial")
    public ApiResponse<List<HistoryView>> history(@AuthenticationPrincipal UserDetails user, @PathVariable int id) {
        return ApiResponse.of(appointments.history(user.getUsername(), role(user), id));
    }

    private String role(UserDetails user) {
        return user.getAuthorities().stream()
                .map(authority -> authority.getAuthority().replaceFirst("^ROLE_", ""))
                .filter(value -> !value.isBlank())
                .findFirst()
                .orElseThrow(() -> new AppointmentException(HttpStatus.FORBIDDEN, "La cuenta no tiene un rol."));
    }

    public record ApiResponse<T>(T data) {
        public static <T> ApiResponse<T> of(T data) {
            return new ApiResponse<>(data);
        }
    }
}
