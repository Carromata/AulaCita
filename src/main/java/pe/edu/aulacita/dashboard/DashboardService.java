package pe.edu.aulacita.dashboard;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.time.LocalDate;
import java.time.ZoneId;

@Service
public class DashboardService {
    private final JdbcTemplate jdbc;
    private static final ZoneId SCHOOL_ZONE = ZoneId.of("America/Lima");

    public DashboardService(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public Map<String, Integer> metrics(String email, String role) {
        Map<String, Integer> result = new LinkedHashMap<>();
        if ("APODERADO".equals(role)) {
            result.put("Estudiantes vinculados", count("""
                    SELECT COUNT(*) FROM dbo.EstudianteApoderado ea
                    JOIN dbo.Apoderado a ON a.IdApoderado = ea.IdApoderado
                    JOIN dbo.Usuario u ON u.IdUsuario = a.IdUsuario
                    WHERE u.Correo = ? AND ea.Activo = 1
                    """, email));
            result.put("Próximas citas", count("""
                    SELECT COUNT(*) FROM dbo.Cita c
                    JOIN dbo.EstudianteApoderado ea ON ea.IdEstudianteApoderado = c.IdEstudianteApoderado
                    JOIN dbo.Apoderado a ON a.IdApoderado = ea.IdApoderado
                    JOIN dbo.Usuario u ON u.IdUsuario = a.IdUsuario
                    JOIN dbo.EstadoCita ec ON ec.IdEstadoCita = c.IdEstadoCita
                    WHERE u.Correo = ? AND c.Fecha >= ?
                      AND ec.Nombre IN (N'Pendiente', N'Confirmada') AND c.Activo = 1
                    """, email, LocalDate.now(SCHOOL_ZONE)));
        } else if ("DOCENTE".equals(role)) {
            result.put("Próximas citas", count("""
                    SELECT COUNT(*) FROM dbo.Cita c
                    JOIN dbo.Docente d ON d.IdDocente = c.IdDocente
                    JOIN dbo.Usuario u ON u.IdUsuario = d.IdUsuario
                    JOIN dbo.EstadoCita ec ON ec.IdEstadoCita = c.IdEstadoCita
                    WHERE u.Correo = ? AND c.Fecha >= ?
                      AND ec.Nombre IN (N'Pendiente', N'Confirmada') AND c.Activo = 1
                    """, email, LocalDate.now(SCHOOL_ZONE)));
            result.put("Pendientes de atención", count("""
                    SELECT COUNT(*) FROM dbo.Cita c
                    JOIN dbo.Docente d ON d.IdDocente = c.IdDocente
                    JOIN dbo.Usuario u ON u.IdUsuario = d.IdUsuario
                    JOIN dbo.EstadoCita ec ON ec.IdEstadoCita = c.IdEstadoCita
                    WHERE u.Correo = ? AND ec.Nombre = N'Confirmada' AND c.Activo = 1
                    """, email));
        } else {
            result.put("Citas próximas", count("""
                    SELECT COUNT(*) FROM dbo.Cita c
                    JOIN dbo.EstadoCita ec ON ec.IdEstadoCita = c.IdEstadoCita
                    WHERE c.Fecha >= ? AND c.Activo = 1
                      AND ec.Nombre IN (N'Pendiente', N'Confirmada')
                    """, LocalDate.now(SCHOOL_ZONE)));
            result.put("Pendientes de confirmación", count("""
                    SELECT COUNT(*) FROM dbo.Cita c
                    JOIN dbo.EstadoCita ec ON ec.IdEstadoCita = c.IdEstadoCita
                    WHERE ec.Nombre = N'Pendiente' AND c.Activo = 1
                    """));
            result.put("Docentes habilitados", count("""
                    SELECT COUNT(*) FROM dbo.Docente d JOIN dbo.Usuario u ON u.IdUsuario = d.IdUsuario
                    WHERE d.Activo = 1 AND u.Activo = 1
                    """));
        }
        return result;
    }

    private int count(String sql, Object... args) {
        Integer value = jdbc.queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }
}
