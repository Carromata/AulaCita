package pe.edu.aulacita.security;

import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.aulacita.appointments.AppointmentException;

import java.nio.charset.StandardCharsets;

@Service
public class AccountService {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;

    public AccountService(JdbcTemplate jdbc, PasswordEncoder encoder) {
        this.jdbc = jdbc;
        this.encoder = encoder;
    }

    @Transactional
    public void changePassword(String email, PasswordChangeRequest request) {
        if (request.newPassword().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new AppointmentException(HttpStatus.BAD_REQUEST,
                    "La nueva contraseña supera el límite admitido por el algoritmo de hash.");
        }
        String storedHash = jdbc.query("""
                SELECT PasswordHash FROM dbo.Usuario
                WHERE Correo = ? AND Activo = 1
                """, rs -> rs.next() ? rs.getString(1) : null, email);
        if (storedHash == null || !encoder.matches(request.currentPassword(), storedHash)) {
            throw new AppointmentException(HttpStatus.UNAUTHORIZED, "La contraseña actual no es correcta.");
        }
        if (encoder.matches(request.newPassword(), storedHash)) {
            throw new AppointmentException(HttpStatus.BAD_REQUEST, "La nueva contraseña debe ser distinta.");
        }
        int changed = jdbc.update("""
                UPDATE dbo.Usuario SET PasswordHash = ?
                WHERE Correo = ? AND Activo = 1 AND PasswordHash = ?
                """, encoder.encode(request.newPassword()), email, storedHash);
        if (changed != 1) {
            throw new AppointmentException(HttpStatus.CONFLICT,
                    "La cuenta cambió durante la operación. Inicie sesión nuevamente.");
        }
    }
}
