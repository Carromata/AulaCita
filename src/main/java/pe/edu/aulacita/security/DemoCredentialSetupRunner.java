package pe.edu.aulacita.security;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.context.annotation.Profile;

import java.io.Console;
import java.util.Arrays;

@Component
@Profile("dev")
@ConditionalOnProperty(name = "aulacita.setup-demo-user", havingValue = "true")
public class DemoCredentialSetupRunner implements ApplicationRunner {
    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;
    private final String email;

    public DemoCredentialSetupRunner(JdbcTemplate jdbc, PasswordEncoder encoder,
                                     @org.springframework.beans.factory.annotation.Value("${aulacita.demo-email:}") String email) {
        this.jdbc = jdbc;
        this.encoder = encoder;
        this.email = email;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (email.isBlank()) {
            throw new IllegalStateException("Indique aulacita.demo-email para configurar la cuenta.");
        }
        Console console = System.console();
        if (console == null) {
            throw new IllegalStateException("La configuración requiere una consola interactiva; no se leyó ninguna contraseña.");
        }
        char[] password = console.readPassword("Nueva contraseña de demostración (mínimo 12 caracteres): ");
        char[] confirmation = console.readPassword("Repita la contraseña: ");
        try {
            if (password == null || password.length < 12 || !Arrays.equals(password, confirmation)) {
                throw new IllegalArgumentException("Las contraseñas no coinciden o no cumplen el mínimo de 12 caracteres.");
            }
            int changed = jdbc.update("""
                    UPDATE dbo.Usuario
                    SET PasswordHash = ?, Activo = 1
                    WHERE Correo = ?
                      AND IdRol <> (SELECT IdRol FROM dbo.Rol WHERE Nombre = N'Administrador')
                      AND Activo = 0
                      AND (PasswordHash IS NULL OR PasswordHash = 'PENDIENTE_CONFIGURAR')
                    """, encoder.encode(new String(password)), email);
            if (changed != 1) {
                throw new IllegalStateException("No se actualizó la cuenta. Debe existir como usuario de demostración sin contraseña previa.");
            }
            console.printf("Credencial de demostración configurada; no se guardó la contraseña en texto plano.%n");
        } finally {
            if (password != null) Arrays.fill(password, '\0');
            if (confirmation != null) Arrays.fill(confirmation, '\0');
        }
    }
}
