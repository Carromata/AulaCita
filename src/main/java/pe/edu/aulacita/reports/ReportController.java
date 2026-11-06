package pe.edu.aulacita.reports;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

@RestController
@RequestMapping("/api/v1/reportes")
@PreAuthorize("hasAnyRole('ADMINISTRADOR','SUPERADMINISTRADOR','SECRETARIA','DIRECTOR')")
public class ReportController {
    private final JdbcTemplate jdbc;

    public ReportController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @GetMapping("/resumen")
    public ReportEnvelope summary(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate hasta) {
        if (desde.isAfter(hasta) || ChronoUnit.DAYS.between(desde, hasta) > 366) {
            throw new IllegalArgumentException("El rango debe ser válido y no superar 367 días.");
        }
        List<StatusCount> counts = jdbc.query("""
                SELECT ec.Nombre AS Estado, COUNT(*) AS Cantidad
                FROM dbo.Cita c
                JOIN dbo.EstadoCita ec ON ec.IdEstadoCita = c.IdEstadoCita
                WHERE c.Fecha >= ? AND c.Fecha <= ? AND c.Activo = 1
                GROUP BY ec.Nombre
                ORDER BY ec.Nombre
                """, (rs, row) -> new StatusCount(rs.getString("Estado"), rs.getInt("Cantidad")), desde, hasta);
        return new ReportEnvelope(new ReportResponse(desde, hasta, counts));
    }

    public record StatusCount(String status, int count) {
    }

    public record ReportResponse(LocalDate from, LocalDate to, List<StatusCount> appointmentsByStatus) {
    }

    public record ReportEnvelope(ReportResponse data) {
    }
}
