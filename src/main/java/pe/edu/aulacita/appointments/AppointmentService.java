package pe.edu.aulacita.appointments;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.aulacita.notifications.NotificationService;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneOffset;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static pe.edu.aulacita.appointments.AppointmentDtos.*;

@Service
public class AppointmentService {
    private static final ZoneId SCHOOL_ZONE = ZoneId.of("America/Lima");
    private static final Set<String> ACTIVE_STATES = Set.of("Pendiente", "Confirmada");
    private final JdbcTemplate jdbc;
    private final NotificationService notifications;

    public AppointmentService(JdbcTemplate jdbc, NotificationService notifications) {
        this.jdbc = jdbc;
        this.notifications = notifications;
    }

    public void checkDatabaseConnection() {
        Integer result = jdbc.queryForObject("SELECT 1", Integer.class);
        if (!Integer.valueOf(1).equals(result)) {
            throw new IllegalStateException("La comprobación de la base de datos no devolvió el resultado esperado.");
        }
    }

    public List<StudentView> myStudents(String email) {
        return jdbc.query("""
                SELECT ea.IdEstudianteApoderado, e.IdEstudiante,
                       CONCAT(e.Nombres, ' ', e.ApellidoPaterno, COALESCE(' ' + e.ApellidoMaterno, '')) AS Estudiante,
                       CONCAT(g.Nivel, ' ', g.Grado, ' ', g.Seccion) AS Grado,
                       ea.PuedeReservar
                FROM dbo.Apoderado a
                JOIN dbo.Usuario u ON u.IdUsuario = a.IdUsuario
                JOIN dbo.EstudianteApoderado ea ON ea.IdApoderado = a.IdApoderado AND ea.Activo = 1
                JOIN dbo.Estudiante e ON e.IdEstudiante = ea.IdEstudiante AND e.Activo = 1
                JOIN dbo.GradoSeccion g ON g.IdGradoSeccion = e.IdGradoSeccion
                WHERE u.Correo = ? AND u.Activo = 1 AND a.Activo = 1
                ORDER BY Estudiante
                """, (rs, row) -> new StudentView(rs.getInt(1), rs.getInt(2),
                rs.getString(3), rs.getString(4), rs.getBoolean(5)), email);
    }

    public List<TeacherView> teachers() {
        return jdbc.query("""
                SELECT d.IdDocente,
                       CONCAT(u.Nombres, ' ', u.ApellidoPaterno, COALESCE(' ' + u.ApellidoMaterno, '')) AS Nombre
                FROM dbo.Docente d
                JOIN dbo.Usuario u ON u.IdUsuario = d.IdUsuario
                WHERE d.Activo = 1 AND u.Activo = 1
                ORDER BY Nombre
                """, (rs, row) -> new TeacherView(rs.getInt(1), rs.getString(2)));
    }

    public List<StudentView> bookableRelations() {
        return jdbc.query("""
                SELECT ea.IdEstudianteApoderado, e.IdEstudiante,
                       CONCAT(e.Nombres, ' ', e.ApellidoPaterno, COALESCE(' ' + e.ApellidoMaterno, '')) AS Estudiante,
                       CONCAT(g.Nivel, ' ', g.Grado, ' ', g.Seccion) AS Grado,
                       ea.PuedeReservar
                FROM dbo.EstudianteApoderado ea
                JOIN dbo.Estudiante e ON e.IdEstudiante = ea.IdEstudiante AND e.Activo = 1
                JOIN dbo.GradoSeccion g ON g.IdGradoSeccion = e.IdGradoSeccion
                JOIN dbo.Apoderado a ON a.IdApoderado = ea.IdApoderado AND a.Activo = 1
                JOIN dbo.Usuario u ON u.IdUsuario = a.IdUsuario AND u.Activo = 1
                WHERE ea.Activo = 1
                ORDER BY Estudiante
                """, (rs, row) -> new StudentView(rs.getInt(1), rs.getInt(2),
                rs.getString(3), rs.getString(4), rs.getBoolean(5)));
    }

    public Integer teacherIdFor(String email) {
        return jdbc.query("""
                SELECT d.IdDocente FROM dbo.Docente d
                JOIN dbo.Usuario u ON u.IdUsuario = d.IdUsuario
                WHERE LOWER(u.Correo) = LOWER(?) AND d.Activo = 1 AND u.Activo = 1
                """, rs -> rs.next() ? rs.getInt(1) : null, email);
    }

    public Integer guardianIdFor(String email) {
        return jdbc.query("""
                SELECT a.IdApoderado FROM dbo.Apoderado a
                JOIN dbo.Usuario u ON u.IdUsuario = a.IdUsuario
                WHERE LOWER(u.Correo) = LOWER(?) AND a.Activo = 1 AND u.Activo = 1
                """, rs -> rs.next() ? rs.getInt(1) : null, email);
    }

    public void authorizeAccess(String email, String role, int appointmentId) {
        checkOwnerOrStaff(userId(email), role, appointmentId);
    }

    public List<AvailableSlot> availableSlots(int teacherId, LocalDate date, String email, String role, Integer relationId) {
        if (date == null || date.isBefore(LocalDate.now(SCHOOL_ZONE))) {
            throw new AppointmentException(HttpStatus.BAD_REQUEST, "La fecha debe ser hoy o una fecha futura.");
        }
        if ("DOCENTE".equals(role)) {
            permittedTeacherId(email, role, teacherId);
        }
        Integer checkedRelation = null;
        if ("APODERADO".equals(role)) {
            if (relationId == null) {
                throw new AppointmentException(HttpStatus.BAD_REQUEST, "Seleccione el estudiante para consultar horarios.");
            }
            Integer guardianId = jdbc.query("""
                    SELECT IdApoderado FROM dbo.Apoderado a
                    JOIN dbo.Usuario u ON u.IdUsuario = a.IdUsuario
                    WHERE u.Correo = ? AND u.Activo = 1 AND a.Activo = 1
                    """, rs -> rs.next() ? rs.getInt(1) : null, email);
            Integer ownedRelation = guardianId == null ? null : jdbc.query("""
                    SELECT IdEstudianteApoderado FROM dbo.EstudianteApoderado
                    WHERE IdEstudianteApoderado = ? AND IdApoderado = ? AND Activo = 1 AND PuedeReservar = 1
                    """, rs -> rs.next() ? rs.getInt(1) : null, relationId, guardianId);
            if (ownedRelation == null) {
                throw new AppointmentException(HttpStatus.FORBIDDEN, "No tiene permiso para consultar ese estudiante.");
            }
            checkedRelation = ownedRelation;
        }
        return buildSlots(teacherId, date, checkedRelation);
    }

    @Transactional
    public AvailabilityView createAvailability(String email, String role, AvailabilityRequest request) {
        int teacherId = permittedTeacherId(email, role, request.teacherId());
        if (!request.startTime().isBefore(request.endTime())
                || request.durationMinutes() > java.time.Duration.between(request.startTime(), request.endTime()).toMinutes()
                || java.time.Duration.between(request.startTime(), request.endTime()).toMinutes()
                % request.durationMinutes() != 0
                || (request.validUntil() != null && request.validUntil().isBefore(request.validFrom()))) {
            throw new AppointmentException(HttpStatus.BAD_REQUEST,
                    "Revise el rango, la vigencia y la duración del turno; deben formar bloques completos.");
        }
        if (request.validUntil() != null && request.validUntil().isBefore(LocalDate.now(SCHOOL_ZONE))) {
            throw new AppointmentException(HttpStatus.BAD_REQUEST, "La vigencia no puede terminar en el pasado.");
        }
        acquireAvailabilityLock(teacherId, request.dayOfWeek());
        Integer overlaps = jdbc.queryForObject("""
                SELECT COUNT(*) FROM dbo.DisponibilidadDocente
                WHERE IdDocente = ? AND DiaSemana = ? AND Activo = 1
                  AND HoraInicio < ? AND HoraFin > ?
                  AND VigenteDesde <= COALESCE(?, CONVERT(date, '99991231'))
                  AND COALESCE(VigenteHasta, CONVERT(date, '99991231')) >= ?
                """, Integer.class, teacherId, request.dayOfWeek(), request.endTime(), request.startTime(),
                request.validUntil(), request.validFrom());
        if (overlaps != null && overlaps > 0) {
            throw conflict("La nueva disponibilidad se superpone con otra vigente.");
        }
        jdbc.update("""
                INSERT INTO dbo.DisponibilidadDocente
                    (IdDocente, DiaSemana, HoraInicio, HoraFin, VigenteDesde, VigenteHasta, DuracionMinutos)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """, teacherId, request.dayOfWeek(), request.startTime(), request.endTime(),
                request.validFrom(), request.validUntil(), request.durationMinutes());
        int id = jdbc.queryForObject("SELECT CONVERT(int, SCOPE_IDENTITY())", Integer.class);
        return new AvailabilityView(id, teacherId, request.dayOfWeek(), request.startTime(), request.endTime(),
                request.validFrom(), request.validUntil(), request.durationMinutes(), true);
    }

    @Transactional
    public AvailabilityView updateAvailability(String email, String role, int availabilityId,
                                               AvailabilityUpdateRequest request) {
        AvailabilityView observed = jdbc.query("""
                SELECT IdDisponibilidad, IdDocente, DiaSemana, HoraInicio, HoraFin,
                       VigenteDesde, VigenteHasta, DuracionMinutos, Activo
                FROM dbo.DisponibilidadDocente
                WHERE IdDisponibilidad = ?
                """, rs -> rs.next() ? availabilityView(rs) : null, availabilityId);
        if (observed == null) {
            throw new AppointmentException(HttpStatus.NOT_FOUND, "No se encontró la disponibilidad.");
        }
        acquireAvailabilityLocks(observed.teacherId(), observed.dayOfWeek(), request.dayOfWeek());
        AvailabilityView current = jdbc.query("""
                SELECT IdDisponibilidad, IdDocente, DiaSemana, HoraInicio, HoraFin,
                       VigenteDesde, VigenteHasta, DuracionMinutos, Activo
                FROM dbo.DisponibilidadDocente WITH (UPDLOCK, HOLDLOCK)
                WHERE IdDisponibilidad = ?
                """, rs -> rs.next() ? availabilityView(rs) : null, availabilityId);
        if (current == null) {
            throw new AppointmentException(HttpStatus.NOT_FOUND, "No se encontró la disponibilidad.");
        }
        if (current.teacherId() != observed.teacherId() || current.dayOfWeek() != observed.dayOfWeek()) {
            throw conflict("El horario cambió durante la operación. Actualice la agenda e intente nuevamente.");
        }
        int teacherId = permittedTeacherId(email, role, current.teacherId());
        if (!request.startTime().isBefore(request.endTime())
                || request.durationMinutes() > java.time.Duration.between(request.startTime(), request.endTime()).toMinutes()
                || java.time.Duration.between(request.startTime(), request.endTime()).toMinutes()
                % request.durationMinutes() != 0
                || (request.validUntil() != null && request.validUntil().isBefore(request.validFrom()))) {
            throw new AppointmentException(HttpStatus.BAD_REQUEST,
                    "Revise el rango, la vigencia y la duración del turno; deben formar bloques completos.");
        }
        if (request.validUntil() != null && request.validUntil().isBefore(LocalDate.now(SCHOOL_ZONE))) {
            throw new AppointmentException(HttpStatus.BAD_REQUEST, "La vigencia no puede terminar en el pasado.");
        }

        Integer appointmentsAffected = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM dbo.Cita c
                JOIN dbo.EstadoCita ec ON ec.IdEstadoCita = c.IdEstadoCita
                WHERE c.IdDocente = ? AND c.Activo = 1
                  AND ec.Nombre IN (N'Pendiente', N'Confirmada')
                  AND c.Fecha >= ?
                  AND c.Fecha BETWEEN ? AND COALESCE(?, CONVERT(date, '99991231'))
                  AND (DATEDIFF(day, CONVERT(date, '19000101'), c.Fecha) % 7) + 1 = ?
                  AND c.HoraInicio < ? AND c.HoraFin > ?
                """, Integer.class, teacherId, LocalDate.now(SCHOOL_ZONE),
                current.validFrom(), current.validUntil(), current.dayOfWeek(),
                current.endTime(), current.startTime());
        if (appointmentsAffected != null && appointmentsAffected > 0) {
            throw conflict("No se puede desactivar ni modificar el horario: contiene citas activas futuras.");
        }

        if (request.active()) {
            Integer overlaps = jdbc.queryForObject("""
                    SELECT COUNT(*) FROM dbo.DisponibilidadDocente
                    WHERE IdDocente = ? AND DiaSemana = ? AND Activo = 1 AND IdDisponibilidad <> ?
                      AND HoraInicio < ? AND HoraFin > ?
                      AND VigenteDesde <= COALESCE(?, CONVERT(date, '99991231'))
                      AND COALESCE(VigenteHasta, CONVERT(date, '99991231')) >= ?
                    """, Integer.class, teacherId, request.dayOfWeek(), availabilityId,
                    request.endTime(), request.startTime(), request.validUntil(), request.validFrom());
            if (overlaps != null && overlaps > 0) {
                throw conflict("La disponibilidad se superpone con otra vigente.");
            }
        }

        jdbc.update("""
                UPDATE dbo.DisponibilidadDocente
                SET DiaSemana = ?, HoraInicio = ?, HoraFin = ?, VigenteDesde = ?,
                    VigenteHasta = ?, DuracionMinutos = ?, Activo = ?
                WHERE IdDisponibilidad = ? AND IdDocente = ?
                """, request.dayOfWeek(), request.startTime(), request.endTime(), request.validFrom(),
                request.validUntil(), request.durationMinutes(), request.active(), availabilityId, teacherId);
        return new AvailabilityView(availabilityId, teacherId, request.dayOfWeek(), request.startTime(),
                request.endTime(), request.validFrom(), request.validUntil(), request.durationMinutes(),
                request.active());
    }

    @Transactional
    public BlockView createBlock(String email, String role, BlockRequest request) {
        int teacherId = permittedTeacherId(email, role, request.teacherId());
        if (!request.startTime().isBefore(request.endTime())
                || !request.date().atTime(request.startTime()).atZone(SCHOOL_ZONE)
                .isAfter(java.time.ZonedDateTime.now(SCHOOL_ZONE))) {
            throw new AppointmentException(HttpStatus.BAD_REQUEST,
                    "El bloqueo debe tener un rango válido y comenzar en el futuro.");
        }
        acquireDateLock(request.date());
        Integer appointmentCount = jdbc.queryForObject("""
                SELECT COUNT(*) FROM dbo.Cita c
                JOIN dbo.EstadoCita ec ON ec.IdEstadoCita = c.IdEstadoCita
                WHERE c.IdDocente = ? AND c.Fecha = ? AND c.Activo = 1
                  AND ec.Nombre IN (N'Pendiente', N'Confirmada')
                  AND c.HoraInicio < ? AND c.HoraFin > ?
                """, Integer.class, teacherId, request.date(), request.endTime(), request.startTime());
        if (appointmentCount != null && appointmentCount > 0) {
            throw conflict("No se puede bloquear el horario porque ya tiene citas activas.");
        }
        Integer blockCount = jdbc.queryForObject("""
                SELECT COUNT(*) FROM dbo.BloqueoDocente
                WHERE IdDocente = ? AND Inicio < ? AND Fin > ?
                """, Integer.class, teacherId, request.date().atTime(request.endTime()),
                request.date().atTime(request.startTime()));
        if (blockCount != null && blockCount > 0) {
            throw conflict("El docente ya tiene un bloqueo que se superpone.");
        }
        int actor = userId(email);
        jdbc.update("""
                INSERT INTO dbo.BloqueoDocente (IdDocente, Inicio, Fin, Motivo, RegistradoPorIdUsuario)
                VALUES (?, ?, ?, ?, ?)
                """, teacherId, request.date().atTime(request.startTime()),
                request.date().atTime(request.endTime()), request.reason().trim(), actor);
        int id = jdbc.queryForObject("SELECT CONVERT(int, SCOPE_IDENTITY())", Integer.class);
        return new BlockView(id, teacherId, request.date(), request.startTime(), request.endTime(), request.reason().trim());
    }

    public List<AvailabilityView> myAvailability(String email, String role) {
        Integer teacherId = role.equals("DOCENTE") ? permittedTeacherId(email, role, null) : null;
        String sql = """
                SELECT IdDisponibilidad, IdDocente, DiaSemana, HoraInicio, HoraFin,
                       VigenteDesde, VigenteHasta, DuracionMinutos, Activo
                FROM dbo.DisponibilidadDocente
                """;
        if (teacherId != null) {
            sql += " WHERE IdDocente = ?";
        }
        sql += """
                ORDER BY IdDocente, DiaSemana, HoraInicio
                OFFSET 0 ROWS FETCH NEXT 500 ROWS ONLY
                """;
        return jdbc.query(sql, (rs, row) -> availabilityView(rs),
                teacherId == null ? new Object[0] : new Object[]{teacherId});
    }

    private static AvailabilityView availabilityView(java.sql.ResultSet rs) throws java.sql.SQLException {
        return new AvailabilityView(rs.getInt("IdDisponibilidad"), rs.getInt("IdDocente"),
                rs.getInt("DiaSemana"), rs.getTime("HoraInicio").toLocalTime(),
                rs.getTime("HoraFin").toLocalTime(), rs.getDate("VigenteDesde").toLocalDate(),
                rs.getDate("VigenteHasta") == null ? null : rs.getDate("VigenteHasta").toLocalDate(),
                rs.getInt("DuracionMinutos"), rs.getBoolean("Activo"));
    }

    public List<BlockView> blocks(String email, String role, LocalDate from, LocalDate to) {
        Integer teacherId = role.equals("DOCENTE") ? permittedTeacherId(email, role, null) : null;
        if (from.isAfter(to) || java.time.temporal.ChronoUnit.DAYS.between(from, to) > 366) {
            throw new AppointmentException(HttpStatus.BAD_REQUEST, "El rango de búsqueda de bloqueos no es válido.");
        }
        String sql = """
                SELECT IdBloqueo, IdDocente, Inicio, Fin, Motivo
                FROM dbo.BloqueoDocente
                WHERE Inicio < DATEADD(day, 1, CONVERT(datetime2, ?))
                  AND Fin > CONVERT(datetime2, ?)
                """;
        List<Object> parameters = new ArrayList<>();
        parameters.add(to);
        parameters.add(from);
        if (teacherId != null) {
            sql += " AND IdDocente = ?";
            parameters.add(teacherId);
        }
        sql += """
                ORDER BY Inicio OFFSET 0 ROWS FETCH NEXT 500 ROWS ONLY
                """;
        return jdbc.query(sql, (rs, row) -> new BlockView(rs.getInt(1), rs.getInt(2),
                rs.getObject(3, LocalDateTime.class).toLocalDate(),
                rs.getObject(3, LocalDateTime.class).toLocalTime(),
                rs.getObject(4, LocalDateTime.class).toLocalTime(), rs.getString(5)),
                parameters.toArray());
    }

    @Transactional
    public AppointmentView reserve(String email, String role, BookingRequest request) {
        int userId = userId(email);
        acquireAvailabilityLock(request.teacherId(), request.date().getDayOfWeek().getValue());
        acquireDateLock(request.date());
        validateReservation(email, role, request);
        AppointmentView created = insertAppointment(userId, request, null);
        notifications.appointmentEvent(created.id(), userId, "Nueva cita solicitada", "CITA",
                true, true, true);
        return created;
    }

    public List<AppointmentView> appointments(String email, String role, int page, int size) {
        int userId = userId(email);
        String sql = """
                SELECT c.IdCita, c.CodigoCita, c.IdEstudianteApoderado, c.IdDocente,
                       CONCAT(e.Nombres, ' ', e.ApellidoPaterno, COALESCE(' ' + e.ApellidoMaterno, '')) AS Estudiante,
                       CONCAT(ua.Nombres, ' ', ua.ApellidoPaterno) AS Apoderado,
                       CONCAT(ud.Nombres, ' ', ud.ApellidoPaterno) AS Docente,
                       c.Fecha, c.HoraInicio, c.HoraFin, c.Modalidad, c.Motivo, ec.Nombre AS Estado, c.VersionFila
                FROM dbo.Cita c
                JOIN dbo.EstadoCita ec ON ec.IdEstadoCita = c.IdEstadoCita
                JOIN dbo.EstudianteApoderado ea ON ea.IdEstudianteApoderado = c.IdEstudianteApoderado
                JOIN dbo.Estudiante e ON e.IdEstudiante = ea.IdEstudiante
                JOIN dbo.Apoderado a ON a.IdApoderado = ea.IdApoderado
                JOIN dbo.Usuario ua ON ua.IdUsuario = a.IdUsuario
                JOIN dbo.Docente d ON d.IdDocente = c.IdDocente
                JOIN dbo.Usuario ud ON ud.IdUsuario = d.IdUsuario
                WHERE c.Activo = 1
                """;
        List<Object> args = new ArrayList<>();
        if ("APODERADO".equals(role)) {
            sql += " AND a.IdUsuario = ?";
            args.add(userId);
        } else if ("DOCENTE".equals(role)) {
            sql += " AND d.IdUsuario = ?";
            args.add(userId);
        }
        sql += " ORDER BY c.Fecha DESC, c.HoraInicio DESC OFFSET ? ROWS FETCH NEXT ? ROWS ONLY";
        args.add(page * size);
        args.add(size);
        return jdbc.query(sql, (rs, row) -> toAppointment(rs), args.toArray());
    }

    @Transactional
    public AppointmentView confirm(String email, String role, int appointmentId, String version) {
        int actor = userId(email);
        checkOwnerOrStaff(actor, role, appointmentId);
        AppointmentView confirmed = changeState(actor, appointmentId, version, "Pendiente", "Confirmada",
                "CONFIRMADA", null);
        notifications.appointmentEvent(appointmentId, actor, "Cita confirmada", "CITA",
                true, true, false);
        return confirmed;
    }

    @Transactional
    public AppointmentView cancel(String email, String role, int appointmentId, CancellationRequest request) {
        int actor = userId(email);
        AppointmentView current = requireAppointment(appointmentId);
        checkOwnerOrStaff(actor, role, current.id());
        int state = stateId(current.status());
        if (!ACTIVE_STATES.contains(current.status())) {
            throw conflict("Solo se pueden cancelar citas pendientes o confirmadas.");
        }
        if (!current.date().atTime(current.startTime()).atZone(SCHOOL_ZONE)
                .isAfter(java.time.ZonedDateTime.now(SCHOOL_ZONE))) {
            throw conflict("Una cita no se puede cancelar después de su hora de inicio.");
        }
        byte[] version = parseVersion(request.version());
        int changed = jdbc.update("""
                UPDATE dbo.Cita
                SET IdEstadoCita = ?, FechaCancelacion = SYSUTCDATETIME(),
                    FechaActualizacion = SYSUTCDATETIME(), MotivoCancelacion = ?,
                    CanceladoPorIdUsuario = ?
                WHERE IdCita = ? AND VersionFila = ? AND IdEstadoCita = ?
                """, stateId("Cancelada"), request.reason().trim(), actor, appointmentId, version, state);
        if (changed != 1) {
            throw conflict("La cita cambió desde que se abrió. Actualice la página e intente nuevamente.");
        }
        history(appointmentId, actor, "CANCELADA", current.status(), "Cancelada", request.reason().trim());
        notifications.appointmentEvent(appointmentId, actor, "Cita cancelada", "CITA",
                true, true, true);
        return requireAppointment(appointmentId);
    }

    @Transactional
    public AppointmentView reschedule(String email, String role, int appointmentId, RescheduleRequest request) {
        int actor = userId(email);
        AppointmentView current = requireAppointment(appointmentId);
        checkOwnerOrStaff(actor, role, appointmentId);
        if (!ACTIVE_STATES.contains(current.status())) {
            throw conflict("No se puede reprogramar una cita finalizada o cancelada.");
        }
        if (!current.date().atTime(current.startTime()).atZone(SCHOOL_ZONE)
                .isAfter(java.time.ZonedDateTime.now(SCHOOL_ZONE))) {
            throw conflict("Una cita no se puede reprogramar después de su hora de inicio.");
        }
        int originalState = stateId(current.status());
        byte[] version = parseVersion(request.version());
        acquireAvailabilityLock(request.replacement().teacherId(),
                request.replacement().date().getDayOfWeek().getValue());
        acquireDateLock(request.replacement().date());
        validateReservation(email, role, request.replacement());
        int changed = jdbc.update("""
                UPDATE dbo.Cita
                SET IdEstadoCita = ?, FechaCancelacion = SYSUTCDATETIME(),
                    FechaActualizacion = SYSUTCDATETIME(), MotivoCancelacion = N'Reprogramada',
                    CanceladoPorIdUsuario = ?
                WHERE IdCita = ? AND VersionFila = ? AND IdEstadoCita = ?
                """, stateId("Cancelada"), actor, appointmentId, version, originalState);
        if (changed != 1) {
            throw conflict("La cita cambió desde que se abrió. Actualice la página e intente nuevamente.");
        }
        history(appointmentId, actor, "REPROGRAMADA", current.status(), "Cancelada",
                "Se creó una nueva cita que conserva la referencia a esta reserva.");
        AppointmentView replacement = insertAppointment(actor, request.replacement(), appointmentId);
        notifications.appointmentEvent(replacement.id(), actor, "Cita reprogramada", "CITA",
                true, true, true);
        return replacement;
    }

    @Transactional
    public AppointmentView registerAttendance(String email, String role, int appointmentId, AttendanceRequest request) {
        int actor = userId(email);
        AppointmentView current = requireAppointment(appointmentId);
        checkOwnerOrStaff(actor, role, appointmentId);
        if (!"Confirmada".equals(current.status())) {
            throw conflict("La asistencia solo se registra para citas confirmadas.");
        }
        if (current.date().atTime(current.endTime()).atZone(SCHOOL_ZONE)
                .isAfter(java.time.ZonedDateTime.now(SCHOOL_ZONE))) {
            throw conflict("La cita todavía no ha terminado según la hora de Lima.");
        }
        byte[] version = parseVersion(request.version());
        String newState = request.attended() ? "Completada" : "No asistió";
        int changed = jdbc.update("""
                UPDATE dbo.Cita SET IdEstadoCita = ?, FechaActualizacion = SYSUTCDATETIME()
                WHERE IdCita = ? AND VersionFila = ? AND IdEstadoCita = ?
                """, stateId(newState), appointmentId, version, stateId("Confirmada"));
        if (changed != 1) {
            throw conflict("La cita cambió desde que se abrió. Actualice la página e intente nuevamente.");
        }
        jdbc.update("""
                INSERT INTO dbo.AsistenciaCita (IdCita, Asistio, Observacion, RegistradoPorIdUsuario)
                VALUES (?, ?, ?, ?)
                """, appointmentId, request.attended(), request.observation(), actor);
        history(appointmentId, actor, "ATENDIDA", "Confirmada", newState, request.observation());
        notifications.appointmentEvent(appointmentId, actor, "Resultado de cita registrado", "CITA",
                false, true, false);
        return requireAppointment(appointmentId);
    }

    public List<HistoryView> history(String email, String role, int appointmentId) {
        AppointmentView appointment = requireAppointment(appointmentId);
        checkOwnerOrStaff(userId(email), role, appointmentId);
        return jdbc.query("""
                SELECT Evento, EstadoAnterior, EstadoNuevo, Detalle, FechaEvento
                FROM dbo.HistorialCita WHERE IdCita = ? ORDER BY FechaEvento
                """, (rs, row) -> new HistoryView(rs.getString(1), rs.getString(2), rs.getString(3),
                rs.getString(4), rs.getObject(5, LocalDateTime.class).toInstant(ZoneOffset.UTC).toString()), appointmentId);
    }

    private List<AvailableSlot> buildSlots(int teacherId, LocalDate date, Integer relationId) {
        requireActiveTeacher(teacherId);
        int weekday = date.getDayOfWeek().getValue();
        List<Map<String, Object>> windows = jdbc.queryForList("""
                SELECT HoraInicio, HoraFin, DuracionMinutos
                FROM dbo.DisponibilidadDocente
                WHERE IdDocente = ? AND DiaSemana = ? AND Activo = 1
                  AND VigenteDesde <= ? AND (VigenteHasta IS NULL OR VigenteHasta >= ?)
                ORDER BY HoraInicio
                """, teacherId, weekday, date, date);
        ensureNoOverlappingWindows(windows);
        List<AvailableSlot> slots = new ArrayList<>();
        for (Map<String, Object> window : windows) {
            LocalTime start = ((java.sql.Time) window.get("HoraInicio")).toLocalTime();
            LocalTime end = ((java.sql.Time) window.get("HoraFin")).toLocalTime();
            int minutes = ((Number) window.get("DuracionMinutos")).intValue();
            for (LocalTime time = start; !time.plusMinutes(minutes).isAfter(end); time = time.plusMinutes(minutes)) {
                LocalTime finish = time.plusMinutes(minutes);
                if (date.atTime(time).atZone(SCHOOL_ZONE).isAfter(java.time.ZonedDateTime.now(SCHOOL_ZONE))
                        && isSlotFree(teacherId, date, time, finish, relationId == null ? -1 : relationId)) {
                    slots.add(new AvailableSlot(date, time, finish));
                }
            }
        }
        return slots;
    }

    private void validateReservation(String email, String role, BookingRequest request) {
        if (request.date() == null || request.startTime() == null
                || !request.date().atTime(request.startTime()).atZone(SCHOOL_ZONE)
                .isAfter(java.time.ZonedDateTime.now(SCHOOL_ZONE))) {
            throw new AppointmentException(HttpStatus.BAD_REQUEST, "Solo se permiten reservas futuras en hora de Lima.");
        }
        String modality = request.modality().toUpperCase(Locale.ROOT);
        if (!Set.of("PRESENCIAL", "VIRTUAL").contains(modality)) {
            throw new AppointmentException(HttpStatus.BAD_REQUEST, "La modalidad debe ser PRESENCIAL o VIRTUAL.");
        }
        Integer allowed;
        if ("APODERADO".equals(role)) {
            Integer guardianId = jdbc.query("""
                    SELECT a.IdApoderado
                    FROM dbo.Apoderado a JOIN dbo.Usuario u ON u.IdUsuario = a.IdUsuario
                    WHERE u.Correo = ? AND a.Activo = 1 AND u.Activo = 1
                    """, rs -> rs.next() ? rs.getInt(1) : null, email);
            allowed = guardianId == null ? null : jdbc.query("""
                    SELECT ea.IdEstudianteApoderado
                    FROM dbo.EstudianteApoderado ea
                    JOIN dbo.Estudiante e ON e.IdEstudiante = ea.IdEstudiante AND e.Activo = 1
                    WHERE ea.IdEstudianteApoderado = ? AND ea.IdApoderado = ?
                      AND ea.Activo = 1 AND ea.PuedeReservar = 1
                    """, rs -> rs.next() ? rs.getInt(1) : null,
                    request.studentGuardianRelationId(), guardianId);
        } else if ("DOCENTE".equals(role)) {
            Integer ownTeacher = teacherIdFor(email);
            if (ownTeacher == null || !ownTeacher.equals(request.teacherId())) {
                allowed = null;
            } else {
                allowed = jdbc.query("""
                        SELECT ea.IdEstudianteApoderado
                        FROM dbo.EstudianteApoderado ea
                        JOIN dbo.Estudiante e ON e.IdEstudiante = ea.IdEstudiante AND e.Activo = 1
                        WHERE ea.IdEstudianteApoderado = ? AND ea.Activo = 1
                        """, rs -> rs.next() ? rs.getInt(1) : null, request.studentGuardianRelationId());
            }
        } else if (Set.of("ADMINISTRADOR", "SUPERADMINISTRADOR", "SECRETARIA").contains(role)) {
            allowed = jdbc.query("""
                    SELECT ea.IdEstudianteApoderado
                    FROM dbo.EstudianteApoderado ea
                    JOIN dbo.Estudiante e ON e.IdEstudiante = ea.IdEstudiante AND e.Activo = 1
                    WHERE ea.IdEstudianteApoderado = ? AND ea.Activo = 1 AND ea.PuedeReservar = 1
                    """, rs -> rs.next() ? rs.getInt(1) : null, request.studentGuardianRelationId());
        } else {
            allowed = null;
        }
        if (allowed == null) {
            throw new AppointmentException(HttpStatus.FORBIDDEN, "No tiene permiso para reservar por ese estudiante.");
        }
        requireActiveTeacher(request.teacherId());
        List<Map<String, Object>> windows = jdbc.queryForList("""
                SELECT HoraInicio, HoraFin, DuracionMinutos
                FROM dbo.DisponibilidadDocente
                WHERE IdDocente = ? AND DiaSemana = ? AND Activo = 1
                  AND VigenteDesde <= ? AND (VigenteHasta IS NULL OR VigenteHasta >= ?)
                ORDER BY HoraInicio
                """, request.teacherId(), request.date().getDayOfWeek().getValue(), request.date(), request.date());
        ensureNoOverlappingWindows(windows);
        LocalTime requestedEnd = null;
        int matches = 0;
        for (Map<String, Object> window : windows) {
            LocalTime start = ((java.sql.Time) window.get("HoraInicio")).toLocalTime();
            LocalTime end = ((java.sql.Time) window.get("HoraFin")).toLocalTime();
            int duration = ((Number) window.get("DuracionMinutos")).intValue();
            LocalTime finish = request.startTime().plusMinutes(duration);
            if (!request.startTime().isBefore(start) && !finish.isAfter(end)
                    && java.time.Duration.between(start, request.startTime()).toMinutes() % duration == 0) {
                matches++;
                requestedEnd = finish;
            }
        }
        if (matches == 0) {
            throw conflict("El horario solicitado no coincide con una disponibilidad vigente.");
        }
        if (matches > 1) {
            throw conflict("El docente tiene disponibilidades superpuestas para ese turno; contacte a secretaría.");
        }
        if (!isSlotFree(request.teacherId(), request.date(), request.startTime(), requestedEnd,
                request.studentGuardianRelationId())) {
            throw conflict("El horario ya no está disponible.");
        }
        if (jdbc.queryForObject("""
                SELECT COUNT(*) FROM dbo.BloqueoDocente
                WHERE IdDocente = ? AND Inicio < ? AND Fin > ?
                """, Integer.class, request.teacherId(),
                request.date().atTime(requestedEnd), request.date().atTime(request.startTime())) > 0) {
            throw conflict("El docente tiene un bloqueo durante ese horario.");
        }
    }

    private boolean isSlotFree(int teacherId, LocalDate date, LocalTime start, LocalTime end, int relationId) {
        String sql = """
                SELECT COUNT(*)
                FROM dbo.Cita c
                JOIN dbo.EstadoCita ec ON ec.IdEstadoCita = c.IdEstadoCita
                JOIN dbo.EstudianteApoderado ea ON ea.IdEstudianteApoderado = c.IdEstudianteApoderado
                WHERE c.Fecha = ? AND c.Activo = 1
                  AND ec.Nombre IN (N'Pendiente', N'Confirmada')
                  AND c.HoraInicio < ? AND c.HoraFin > ?
                  AND (c.IdDocente = ? OR ea.IdEstudiante = (
                      SELECT IdEstudiante FROM dbo.EstudianteApoderado WHERE IdEstudianteApoderado = ?
                  ) OR ea.IdApoderado = (
                      SELECT IdApoderado FROM dbo.EstudianteApoderado WHERE IdEstudianteApoderado = ?
                  ))
                """;
        int count = jdbc.queryForObject(sql, Integer.class, date, end, start,
                teacherId, relationId, relationId);
        return count == 0;
    }

    private void ensureNoOverlappingWindows(List<Map<String, Object>> windows) {
        LocalTime previousEnd = null;
        for (Map<String, Object> window : windows) {
            LocalTime start = ((java.sql.Time) window.get("HoraInicio")).toLocalTime();
            LocalTime end = ((java.sql.Time) window.get("HoraFin")).toLocalTime();
            if (previousEnd != null && start.isBefore(previousEnd)) {
                throw conflict("El docente tiene disponibilidades superpuestas; contacte a secretaría.");
            }
            previousEnd = end;
        }
    }

    private AppointmentView insertAppointment(int actor, BookingRequest request, Integer originId) {
        validateReservationByActor(request);
        LocalTime end = findEndTime(request);
        jdbc.update("""
                INSERT INTO dbo.Cita
                    (IdEstudianteApoderado, IdDocente, IdEstadoCita, Fecha, HoraInicio, HoraFin,
                     Modalidad, Motivo, ComentarioAdicional, RegistradoPorIdUsuario, CitaOrigenIdCita)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """, request.studentGuardianRelationId(), request.teacherId(), stateId("Pendiente"),
                request.date(), request.startTime(), end, request.modality().toUpperCase(Locale.ROOT),
                request.reason().trim(), request.comment(), actor, originId);
        Integer id = jdbc.queryForObject("SELECT CONVERT(int, SCOPE_IDENTITY())", Integer.class);
        history(id, actor, originId == null ? "RESERVADA" : "REPROGRAMADA", null, "Pendiente",
                originId == null ? null : "Reprograma la cita " + originId);
        return requireAppointment(id);
    }

    private void validateReservationByActor(BookingRequest request) {
        requireActiveTeacher(request.teacherId());
        if (!Set.of("PRESENCIAL", "VIRTUAL").contains(request.modality().toUpperCase(Locale.ROOT))) {
            throw new AppointmentException(HttpStatus.BAD_REQUEST, "Modalidad no válida.");
        }
        if (!request.date().atTime(request.startTime()).atZone(SCHOOL_ZONE)
                .isAfter(java.time.ZonedDateTime.now(SCHOOL_ZONE))) {
            throw new AppointmentException(HttpStatus.BAD_REQUEST, "La fecha de la cita debe ser futura.");
        }
    }

    private LocalTime findEndTime(BookingRequest request) {
        List<Map<String, Object>> windows = jdbc.queryForList("""
                SELECT HoraInicio, HoraFin, DuracionMinutos
                FROM dbo.DisponibilidadDocente
                WHERE IdDocente = ? AND DiaSemana = ? AND Activo = 1
                  AND VigenteDesde <= ? AND (VigenteHasta IS NULL OR VigenteHasta >= ?)
                """, request.teacherId(), request.date().getDayOfWeek().getValue(), request.date(), request.date());
        for (Map<String, Object> window : windows) {
            LocalTime start = ((java.sql.Time) window.get("HoraInicio")).toLocalTime();
            LocalTime end = ((java.sql.Time) window.get("HoraFin")).toLocalTime();
            int duration = ((Number) window.get("DuracionMinutos")).intValue();
            LocalTime finish = request.startTime().plusMinutes(duration);
            if (!request.startTime().isBefore(start) && !finish.isAfter(end)
                    && java.time.Duration.between(start, request.startTime()).toMinutes() % duration == 0) {
                return finish;
            }
        }
        throw conflict("La disponibilidad cambió mientras se procesaba la reserva.");
    }

    private AppointmentView changeState(int actor, int id, String version, String oldState, String newState,
                                        String event, String detail) {
        AppointmentView current = requireAppointment(id);
        if (!oldState.equals(current.status())) {
            throw conflict("La cita no se encuentra en un estado que permita esta acción.");
        }
        int changed = jdbc.update("""
                UPDATE dbo.Cita SET IdEstadoCita = ?, FechaActualizacion = SYSUTCDATETIME()
                WHERE IdCita = ? AND VersionFila = ? AND IdEstadoCita = ?
                """, stateId(newState), id, parseVersion(version), stateId(oldState));
        if (changed != 1) {
            throw conflict("La cita cambió desde que se abrió. Actualice la página e intente nuevamente.");
        }
        history(id, actor, event, oldState, newState, detail);
        return requireAppointment(id);
    }

    private void acquireDateLock(LocalDate date) {
        acquireLock("AulaCita:reserva:" + date);
    }

    private void acquireAvailabilityLock(int teacherId, int dayOfWeek) {
        acquireLock("AulaCita:disponibilidad:" + teacherId + ":" + dayOfWeek);
    }

    private void acquireAvailabilityLocks(int teacherId, int firstDay, int secondDay) {
        int lowerDay = Math.min(firstDay, secondDay);
        int upperDay = Math.max(firstDay, secondDay);
        acquireAvailabilityLock(teacherId, lowerDay);
        if (upperDay != lowerDay) {
            acquireAvailabilityLock(teacherId, upperDay);
        }
    }

    private void acquireLock(String resource) {
        Integer result = jdbc.queryForObject("""
                DECLARE @lockResult int;
                EXEC @lockResult = sys.sp_getapplock
                    @Resource = ?, @LockMode = 'Exclusive', @LockOwner = 'Transaction', @LockTimeout = 10000;
                SELECT @lockResult;
                """, Integer.class, resource);
        if (result == null || result < 0) {
            throw conflict("No se pudo reservar el horario ahora. Vuelva a intentarlo.");
        }
    }

    private int permittedTeacherId(String email, String role, Integer requestedTeacherId) {
        Integer teacherId;
        if ("DOCENTE".equals(role)) {
            teacherId = jdbc.query("""
                    SELECT d.IdDocente FROM dbo.Docente d
                    JOIN dbo.Usuario u ON u.IdUsuario = d.IdUsuario
                    WHERE u.Correo = ? AND d.Activo = 1 AND u.Activo = 1
                    """, rs -> rs.next() ? rs.getInt(1) : null, email);
            if (teacherId == null || (requestedTeacherId != null && !teacherId.equals(requestedTeacherId))) {
                throw new AppointmentException(HttpStatus.FORBIDDEN, "Solo puede gestionar su propia agenda.");
            }
        } else if (Set.of("ADMINISTRADOR", "SUPERADMINISTRADOR", "SECRETARIA").contains(role) && requestedTeacherId != null) {
            teacherId = requestedTeacherId;
            requireActiveTeacher(teacherId);
        } else {
            throw new AppointmentException(HttpStatus.FORBIDDEN, "No tiene permiso para gestionar disponibilidades.");
        }
        return teacherId;
    }

    private void requireActiveTeacher(int teacherId) {
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM dbo.Docente d
                JOIN dbo.Usuario u ON u.IdUsuario = d.IdUsuario
                WHERE d.IdDocente = ? AND d.Activo = 1 AND u.Activo = 1
                """, Integer.class, teacherId);
        if (count == null || count == 0) {
            throw new AppointmentException(HttpStatus.NOT_FOUND, "No se encontró un docente habilitado.");
        }
    }

    private AppointmentView requireAppointment(int id) {
        List<AppointmentView> rows = jdbc.query("""
                SELECT c.IdCita, c.CodigoCita, c.IdEstudianteApoderado, c.IdDocente,
                       CONCAT(e.Nombres, ' ', e.ApellidoPaterno, COALESCE(' ' + e.ApellidoMaterno, '')) AS Estudiante,
                       CONCAT(ua.Nombres, ' ', ua.ApellidoPaterno) AS Apoderado,
                       CONCAT(ud.Nombres, ' ', ud.ApellidoPaterno) AS Docente,
                       c.Fecha, c.HoraInicio, c.HoraFin, c.Modalidad, c.Motivo, ec.Nombre AS Estado, c.VersionFila
                FROM dbo.Cita c
                JOIN dbo.EstadoCita ec ON ec.IdEstadoCita = c.IdEstadoCita
                JOIN dbo.EstudianteApoderado ea ON ea.IdEstudianteApoderado = c.IdEstudianteApoderado
                JOIN dbo.Estudiante e ON e.IdEstudiante = ea.IdEstudiante
                JOIN dbo.Apoderado a ON a.IdApoderado = ea.IdApoderado
                JOIN dbo.Usuario ua ON ua.IdUsuario = a.IdUsuario
                JOIN dbo.Docente d ON d.IdDocente = c.IdDocente
                JOIN dbo.Usuario ud ON ud.IdUsuario = d.IdUsuario
                WHERE c.IdCita = ?
                """, (rs, row) -> toAppointment(rs), id);
        if (rows.isEmpty()) {
            throw new AppointmentException(HttpStatus.NOT_FOUND, "No se encontró la cita.");
        }
        return rows.get(0);
    }

    private AppointmentView toAppointment(ResultSet rs) throws SQLException {
        return new AppointmentView(rs.getInt("IdCita"), rs.getString("CodigoCita"),
                rs.getInt("IdEstudianteApoderado"), rs.getInt("IdDocente"), rs.getString("Estudiante"),
                rs.getString("Apoderado"), rs.getString("Docente"), rs.getDate("Fecha").toLocalDate(),
                rs.getTime("HoraInicio").toLocalTime(), rs.getTime("HoraFin").toLocalTime(),
                rs.getString("Modalidad"), rs.getString("Motivo"), rs.getString("Estado"),
                Base64.getEncoder().encodeToString(rs.getBytes("VersionFila")));
    }

    private void checkOwnerOrStaff(int userId, String role, int appointmentId) {
        if (Set.of("ADMINISTRADOR", "SUPERADMINISTRADOR", "SECRETARIA").contains(role)) {
            return;
        }
        String ownerPredicate = switch (role) {
            case "APODERADO" -> "ea.IdApoderado = (SELECT IdApoderado FROM dbo.Apoderado WHERE IdUsuario = ?)";
            case "DOCENTE" -> "c.IdDocente = (SELECT IdDocente FROM dbo.Docente WHERE IdUsuario = ?)";
            default -> throw new AppointmentException(HttpStatus.FORBIDDEN, "No tiene permiso para acceder a esta cita.");
        };
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM dbo.Cita c
                JOIN dbo.EstudianteApoderado ea ON ea.IdEstudianteApoderado = c.IdEstudianteApoderado
                WHERE c.IdCita = ? AND """ + ownerPredicate,
                Integer.class, appointmentId, userId);
        if (count == null || count == 0) {
            throw new AppointmentException(HttpStatus.FORBIDDEN, "No tiene permiso para acceder a esta cita.");
        }
    }

    private int userId(String email) {
        Integer id = jdbc.query("""
                SELECT IdUsuario FROM dbo.Usuario WHERE Correo = ? AND Activo = 1
                """, rs -> rs.next() ? rs.getInt(1) : null, email);
        if (id == null) {
            throw new AppointmentException(HttpStatus.FORBIDDEN, "La cuenta no está habilitada.");
        }
        return id;
    }

    private int stateId(String name) {
        Integer id = jdbc.query("""
                SELECT IdEstadoCita FROM dbo.EstadoCita WHERE Nombre = ?
                """, rs -> rs.next() ? rs.getInt(1) : null, name);
        if (id == null) {
            throw new IllegalStateException("Estado no configurado: " + name);
        }
        return id;
    }

    private void history(int appointmentId, int actor, String event, String oldState, String newState, String detail) {
        jdbc.update("""
                INSERT INTO dbo.HistorialCita (IdCita, IdUsuario, Evento, EstadoAnterior, EstadoNuevo, Detalle)
                VALUES (?, ?, ?, ?, ?, ?)
                """, appointmentId, actor, event, oldState, newState, detail);
    }

    private byte[] parseVersion(String version) {
        try {
            byte[] decoded = Base64.getDecoder().decode(version);
            if (decoded.length != 8) {
                throw new IllegalArgumentException();
            }
            return decoded;
        } catch (IllegalArgumentException ex) {
            throw new AppointmentException(HttpStatus.BAD_REQUEST, "La versión de la cita no es válida.");
        }
    }

    private AppointmentException conflict(String message) {
        return new AppointmentException(HttpStatus.CONFLICT, message);
    }
}
