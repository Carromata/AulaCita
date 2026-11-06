package pe.edu.aulacita.notifications;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.aulacita.appointments.AppointmentException;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import static pe.edu.aulacita.notifications.NotificationDtos.*;

@Service
public class NotificationService {
    private static final Set<String> ROLES = Set.of(
            "SuperAdministrador", "Administrador", "Secretaria", "Director", "Docente", "Apoderado");

    private final JdbcTemplate jdbc;

    public NotificationService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public List<NotificationView> list(String email, boolean onlyUnread, int limit) {
        Integer userId = userId(email);
        if (userId == null) {
            return List.of();
        }
        int safeLimit = Math.max(1, Math.min(limit, 100));
        String sql = """
                SELECT n.IdNotificacion, n.Titulo, n.Mensaje, n.Tipo, n.IdCita,
                       c.CodigoCita, n.Leida, n.FechaCreacion
                FROM dbo.Notificacion n
                LEFT JOIN dbo.Cita c ON c.IdCita = n.IdCita
                WHERE n.IdUsuario = ?
                """ + (onlyUnread ? " AND n.Leida = 0 " : "") + """
                ORDER BY n.FechaCreacion DESC
                OFFSET 0 ROWS FETCH NEXT ? ROWS ONLY
                """;
        return jdbc.query(sql, (rs, row) -> {
            Timestamp created = rs.getTimestamp("FechaCreacion");
            String createdAt = created == null ? null
                    : created.toInstant().toString();
            Integer appointmentId = rs.getObject("IdCita", Integer.class);
            return new NotificationView(
                    rs.getInt("IdNotificacion"),
                    rs.getString("Titulo"),
                    rs.getString("Mensaje"),
                    rs.getString("Tipo"),
                    appointmentId,
                    rs.getString("CodigoCita"),
                    rs.getBoolean("Leida"),
                    createdAt);
        }, userId, safeLimit);
    }

    public int unreadCount(String email) {
        Integer userId = userId(email);
        if (userId == null) {
            return 0;
        }
        Integer count = jdbc.queryForObject("""
                SELECT COUNT(*) FROM dbo.Notificacion WHERE IdUsuario = ? AND Leida = 0
                """, Integer.class, userId);
        return count == null ? 0 : count;
    }

    public void markRead(String email, int notificationId) {
        Integer userId = userId(email);
        if (userId == null) {
            throw new AppointmentException(HttpStatus.NOT_FOUND, "Notificación no encontrada.");
        }
        jdbc.update("""
                UPDATE dbo.Notificacion SET Leida = 1
                WHERE IdNotificacion = ? AND IdUsuario = ?
                """, notificationId, userId);
    }

    public void markAllRead(String email) {
        jdbc.update("""
                UPDATE dbo.Notificacion SET Leida = 1
                WHERE Leida = 0 AND IdUsuario = (SELECT IdUsuario FROM dbo.Usuario WHERE Correo = ?)
                """, email);
    }

    @Transactional
    public int broadcast(String actorEmail, BroadcastRequest request) {
        Integer actorId = userId(actorEmail);
        String rawRole = request.role() == null ? "" : request.role().trim();
        boolean all = rawRole.isBlank() || rawRole.equalsIgnoreCase("TODOS") || rawRole.equalsIgnoreCase("ALL");
        String targetRole = null;
        if (!all) {
            targetRole = ROLES.stream()
                    .filter(role -> role.equalsIgnoreCase(rawRole))
                    .findFirst()
                    .orElseThrow(() -> new AppointmentException(HttpStatus.BAD_REQUEST,
                            "El rol de destino no es válido."));
        }
        List<Map<String, Object>> users = jdbc.queryForList("""
                SELECT u.IdUsuario, r.Nombre AS Rol
                FROM dbo.Usuario u
                JOIN dbo.Rol r ON r.IdRol = u.IdRol
                WHERE u.Activo = 1
                """);
        int sent = 0;
        for (Map<String, Object> user : users) {
            String role = String.valueOf(user.get("Rol"));
            if (!all && !role.equalsIgnoreCase(targetRole)) {
                continue;
            }
            int userId = ((Number) user.get("IdUsuario")).intValue();
            notifyUser(userId, request.title().trim(), request.message().trim(),
                    "AVISO", null, actorId);
            sent++;
        }
        if (sent == 0) {
            throw new AppointmentException(HttpStatus.BAD_REQUEST,
                    "No hay usuarios activos para el rol seleccionado.");
        }
        return sent;
    }

    public void notifyUser(int userId, String title, String message, String type,
                           Integer appointmentId, Integer actorUserId) {
        if (userId <= 0) {
            return;
        }
        if (actorUserId != null && actorUserId == userId) {
            return;
        }
        jdbc.update("""
                INSERT INTO dbo.Notificacion
                    (IdUsuario, Titulo, Mensaje, Tipo, IdCita, CreadoPorIdUsuario)
                VALUES (?, ?, ?, ?, ?, ?)
                """, userId, title, message, type, appointmentId, actorUserId);
    }

    public void notifyRoles(Set<String> roles, String title, String message, String type,
                            Integer appointmentId, Integer actorUserId) {
        if (roles == null || roles.isEmpty()) {
            return;
        }
        List<Map<String, Object>> users = jdbc.queryForList("""
                SELECT u.IdUsuario, r.Nombre AS Rol
                FROM dbo.Usuario u
                JOIN dbo.Rol r ON r.IdRol = u.IdRol
                WHERE u.Activo = 1
                """);
        for (Map<String, Object> user : users) {
            if (roles.contains(String.valueOf(user.get("Rol")))) {
                int userId = ((Number) user.get("IdUsuario")).intValue();
                notifyUser(userId, title, message, type, appointmentId, actorUserId);
            }
        }
    }

    /**
     * Notifica a los participantes de una cita. El mensaje se compone con el
     * código, la fecha y la hora reales de la cita.
     */
    public void appointmentEvent(int appointmentId, int actorUserId, String title, String type,
                                 boolean notifyTeacher, boolean notifyGuardian, boolean notifyStaff) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
                SELECT c.CodigoCita,
                       CONVERT(varchar(10), c.Fecha, 23) AS Fecha,
                       CONVERT(varchar(5), c.HoraInicio, 108) AS Hora,
                       d.IdUsuario AS DocenteUsuario,
                       a.IdUsuario AS ApoderadoUsuario
                FROM dbo.Cita c
                JOIN dbo.Docente d ON d.IdDocente = c.IdDocente
                JOIN dbo.EstudianteApoderado ea ON ea.IdEstudianteApoderado = c.IdEstudianteApoderado
                JOIN dbo.Apoderado a ON a.IdApoderado = ea.IdApoderado
                WHERE c.IdCita = ?
                """, appointmentId);
        if (rows.isEmpty()) {
            return;
        }
        Map<String, Object> data = rows.get(0);
        String message = "Cita " + data.get("CodigoCita") + " para el " + data.get("Fecha")
                + " a las " + data.get("Hora") + ".";
        Set<Integer> recipients = new LinkedHashSet<>();
        if (notifyTeacher && data.get("DocenteUsuario") != null) {
            recipients.add(((Number) data.get("DocenteUsuario")).intValue());
        }
        if (notifyGuardian && data.get("ApoderadoUsuario") != null) {
            recipients.add(((Number) data.get("ApoderadoUsuario")).intValue());
        }
        for (Integer userId : recipients) {
            notifyUser(userId, title, message, type, appointmentId, actorUserId);
        }
        if (notifyStaff) {
            notifyRoles(Set.of("SuperAdministrador", "Administrador", "Secretaria"),
                    title, message, type, appointmentId, actorUserId);
        }
    }

    public Integer userId(String email) {
        if (email == null || email.isBlank()) {
            return null;
        }
        return jdbc.query("""
                SELECT IdUsuario FROM dbo.Usuario
                WHERE LOWER(Correo) = LOWER(?) AND Activo = 1
                """, rs -> rs.next() ? rs.getInt(1) : null, email);
    }

    public List<NotificationView> recentForPanel(String email) {
        List<NotificationView> unread = list(email, true, 5);
        if (!unread.isEmpty()) {
            return unread;
        }
        List<NotificationView> all = new ArrayList<>(list(email, false, 3));
        return all;
    }

    public String formatTimestamp(LocalDateTime value) {
        return value == null ? null : value.toInstant(ZoneOffset.UTC).toString();
    }

    public boolean isValidRole(String role) {
        if (role == null || role.isBlank() || role.equalsIgnoreCase("TODOS")) {
            return true;
        }
        String normalized = role.trim().toLowerCase(Locale.ROOT);
        return ROLES.stream().anyMatch(value -> value.toLowerCase(Locale.ROOT).equals(normalized));
    }
}
