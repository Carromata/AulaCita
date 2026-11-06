package pe.edu.aulacita.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;
import pe.edu.aulacita.appointments.AppointmentException;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Locale;

@Service
public class PasswordRecoveryMailService {
    private static final Logger log = LoggerFactory.getLogger(PasswordRecoveryMailService.class);

    private final ObjectProvider<JavaMailSender> senderProvider;
    private final String from;
    private final String publicUrl;
    private final long expirationMinutes;

    public PasswordRecoveryMailService(ObjectProvider<JavaMailSender> senderProvider,
                                       @Value("${aulacita.mail.from:}") String from,
                                       @Value("${aulacita.public-url:http://localhost:8080}") String publicUrl,
                                       @Value("${aulacita.password-reset.expiration-minutes:30}") long expirationMinutes) {
        this.senderProvider = senderProvider;
        this.from = from.trim();
        this.publicUrl = normalizePublicUrl(publicUrl);
        this.expirationMinutes = expirationMinutes;
    }

    public void requireConfigured() {
        if (senderProvider.getIfAvailable() == null || from.isBlank()) {
            throw new AppointmentException(HttpStatus.SERVICE_UNAVAILABLE,
                    "La recuperación por correo no está configurada. Contacta al administrador.");
        }
    }

    public void sendResetLink(String email, String token) {
        requireConfigured();
        String encodedToken = URLEncoder.encode(token, StandardCharsets.UTF_8);
        String link = publicUrl + "/restablecer-contrasena?token=" + encodedToken;
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(email);
        message.setSubject("Restablece tu contraseña de AulaCita");
        message.setText("""
                Recibimos una solicitud para restablecer la contraseña de tu cuenta de AulaCita.

                Abre este enlace dentro de los próximos %d minutos:
                %s

                Si no solicitaste el cambio, ignora este correo. Tu contraseña no cambiará.
                """.formatted(expirationMinutes, link));
        try {
            senderProvider.getObject().send(message);
        } catch (MailException exception) {
            log.error("No se pudo enviar el correo de recuperación ({}).",
                    exception.getClass().getSimpleName());
            throw new AppointmentException(HttpStatus.SERVICE_UNAVAILABLE,
                    "No se pudo enviar el correo de recuperación. Inténtalo más tarde.");
        }
    }

    private static String normalizePublicUrl(String value) {
        String trimmed = value == null ? "" : value.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed.toLowerCase(Locale.ROOT).startsWith("https://")
                || trimmed.startsWith("http://") ? trimmed : "http://localhost:8080";
    }
}
