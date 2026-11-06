package pe.edu.aulacita.security;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.io.Console;
import java.util.Arrays;

@Component
@ConditionalOnProperty(name = "aulacita.setup-admin", havingValue = "true")
public class AdminCredentialSetupRunner implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final String email;

    public AdminCredentialSetupRunner(JdbcTemplate jdbc, PasswordEncoder encoder,
                                      @org.springframework.beans.factory.annotation.Value("${aulacita.admin-email:}") String email) {
        this.jdbc = jdbc;
        this.encoder = encoder;
        this.email = email;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (email.isBlank()) {
            throw new IllegalStateException("Indique aulacita.admin-email para configurar la cuenta administrativa.");
        }
        Console console = System.console();
        if (console == null) {
            throw new IllegalStateException("La configuración inicial requiere una consola interactiva; no se leyó ninguna contraseña.");
        }
        char[] password = console.readPassword("Nueva contraseña administrativa (mínimo 14 caracteres): ");
        char[] confirmation = console.readPassword("Repita la contraseña: ");
        try {
            if (password == null || password.length < 14 || !Arrays.equals(password, confirmation)) {
                throw new IllegalArgumentException("Las contraseñas no coinciden o no cumplen el mínimo de 14 caracteres.");
            }
            String hash = encoder.encode(new String(password));
            int changed = jdbc.update("""
                    UPDATE dbo.Usuario
                    SET PasswordHash = ?, Activo = 1
                    WHERE Correo = ?
                      AND IdRol = (SELECT IdRol FROM dbo.Rol WHERE Nombre = N'Administrador')
                      AND Activo = 0
                      AND (PasswordHash IS NULL OR PasswordHash = 'PENDIENTE_CONFIGURAR')
                    """, hash, email);
            if (changed != 1) {
                throw new IllegalStateException("No se actualizó la cuenta. Verifique que exista un administrador inactivo sin contraseña configurada.");
            }
            console.printf("Credencial administrativa configurada. La contraseña no se mostró ni se guardó en texto plano.%n");
        } finally {
            if (password != null) Arrays.fill(password, '\0');
            if (confirmation != null) Arrays.fill(confirmation, '\0');
        }
    }
}
