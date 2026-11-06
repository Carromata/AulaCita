package pe.edu.aulacita.security;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.EmptyResultDataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pe.edu.aulacita.appointments.AppointmentException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

@Service
public class PasswordRecoveryService {
    private static final SecureRandom RANDOM = new SecureRandom();

    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final PasswordRecoveryMailService mail;
    private final Duration expiration;

    public PasswordRecoveryService(JdbcTemplate jdbc, PasswordEncoder encoder,
                                   PasswordRecoveryMailService mail,
                                   @Value("${aulacita.password-reset.expiration-minutes:30}") long expirationMinutes) {
        this.jdbc = jdbc;
        this.encoder = encoder;
        this.mail = mail;
        if (expirationMinutes < 5 || expirationMinutes > 120) {
            throw new IllegalArgumentException("La vigencia del enlace debe estar entre 5 y 120 minutos.");
        }
        this.expiration = Duration.ofMinutes(expirationMinutes);
    }

    @Transactional
    public void requestReset(String email) {
        mail.requireConfigured();
        String normalizedEmail = email.trim().toLowerCase(java.util.Locale.ROOT);
        Integer userId = jdbc.query("""
                SELECT u.IdUsuario
                FROM dbo.Usuario u WITH (UPDLOCK, HOLDLOCK)
                WHERE LOWER(u.Correo) = LOWER(?) AND u.Activo = 1 AND u.PasswordHash IS NOT NULL
                """, rs -> rs.next() ? rs.getInt(1) : null, normalizedEmail);
        if (userId == null) {
            return;
        }
        Integer recentRequest = jdbc.queryForObject("""
                SELECT COUNT(*)
                FROM dbo.TokenRestablecimientoContrasena
                WHERE IdUsuario = ? AND FechaCreacion >= DATEADD(minute, -2, SYSUTCDATETIME())
                """, Integer.class, userId);
        if (recentRequest != null && recentRequest > 0) {
            return;
        }

        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
        String tokenHash = sha256(rawToken);
        jdbc.update("""
                UPDATE dbo.TokenRestablecimientoContrasena
                SET Usado = 1
                WHERE IdUsuario = ? AND Usado = 0
                """, userId);
        jdbc.update("""
                INSERT INTO dbo.TokenRestablecimientoContrasena (IdUsuario, HashToken, ExpiraEn)
                VALUES (?, ?, ?)
                """, userId, tokenHash, Timestamp.from(Instant.now().plus(expiration)));
        mail.sendResetLink(normalizedEmail, rawToken);
    }

    @Transactional
    public void resetPassword(PasswordResetRequest request) {
        if (request.newPassword().getBytes(StandardCharsets.UTF_8).length > 72) {
            throw new AppointmentException(HttpStatus.BAD_REQUEST,
                    "La nueva contraseña supera el límite admitido por el algoritmo de hash.");
        }
        String tokenHash = sha256(request.token());
        Integer userId;
        try {
            userId = jdbc.queryForObject("""
                    SELECT t.IdUsuario
                    FROM dbo.TokenRestablecimientoContrasena t
                    JOIN dbo.Usuario u ON u.IdUsuario = t.IdUsuario
                    WHERE t.HashToken = ? AND t.Usado = 0
                      AND t.ExpiraEn > SYSUTCDATETIME() AND u.Activo = 1
                    """, Integer.class, tokenHash);
        } catch (EmptyResultDataAccessException exception) {
            throw invalidToken();
        }
        int consumed = jdbc.update("""
                UPDATE dbo.TokenRestablecimientoContrasena
                SET Usado = 1
                WHERE HashToken = ? AND Usado = 0 AND ExpiraEn > SYSUTCDATETIME()
                """, tokenHash);
        if (consumed != 1) {
            throw invalidToken();
        }
        int changed = jdbc.update("""
                UPDATE dbo.Usuario
                SET PasswordHash = ?
                WHERE IdUsuario = ? AND Activo = 1
                """, encoder.encode(request.newPassword()), userId);
        if (changed != 1) {
            throw invalidToken();
        }
        jdbc.update("""
                UPDATE dbo.TokenRestablecimientoContrasena
                SET Usado = 1
                WHERE IdUsuario = ? AND Usado = 0
                """, userId);
    }

    private static AppointmentException invalidToken() {
        return new AppointmentException(HttpStatus.BAD_REQUEST,
                "El enlace no es válido o expiró. Solicita uno nuevo.");
    }

    private static String sha256(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 no está disponible.", exception);
        }
    }
}
