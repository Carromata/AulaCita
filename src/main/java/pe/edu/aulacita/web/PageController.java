package pe.edu.aulacita.web;

import pe.edu.aulacita.dashboard.DashboardService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import pe.edu.aulacita.appointments.AppointmentException;
import org.springframework.http.HttpStatus;

import java.util.Locale;

@Controller
public class PageController {
    private final DashboardService dashboard;
    private final JdbcTemplate jdbc;

    public PageController(DashboardService dashboard, JdbcTemplate jdbc) {
        this.dashboard = dashboard;
        this.jdbc = jdbc;
    }

    @ModelAttribute("currentRole")
    public String currentRole(@AuthenticationPrincipal UserDetails user) {
        if (user == null) return "";
        return user.getAuthorities().stream().findFirst()
                .map(authority -> authority.getAuthority().replaceFirst("^ROLE_", ""))
                .orElse("");
    }

    @GetMapping("/")
    public String home() {
        return "inicio";
    }

    @GetMapping("/sobre")
    public String about() {
        return "sobre";
    }

    @GetMapping("/verificar-alumno")
    public String verifyStudent() {
        return "verificar-alumno";
    }

    @GetMapping("/login")
    public String login() {
        return "login";
    }

    @GetMapping("/recuperar-contrasena")
    public String recoverPassword() {
        return "recuperar-contrasena";
    }

    @GetMapping("/restablecer-contrasena")
    public String resetPassword() {
        return "restablecer-contrasena";
    }

    @GetMapping("/gestion")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','SUPERADMINISTRADOR','SECRETARIA')")
    public String management() {
        return "gestion";
    }

    @GetMapping("/panel")
    public String panel(@AuthenticationPrincipal UserDetails user, Model model) {
        String role = user.getAuthorities().stream().findFirst()
                .map(authority -> authority.getAuthority().replaceFirst("^ROLE_", ""))
                .orElse("USUARIO");
        String displayName = jdbc.query("""
                SELECT CONCAT(Nombres, ' ', ApellidoPaterno)
                FROM dbo.Usuario WHERE Correo = ?
                """, result -> result.next() ? result.getString(1) : null, user.getUsername());
        if (displayName == null) {
            throw new AppointmentException(HttpStatus.FORBIDDEN, "La cuenta ya no está disponible.");
        }
        model.addAttribute("nombre", displayName);
        model.addAttribute("rol", role.toLowerCase(Locale.ROOT));
        model.addAttribute("metricas", dashboard.metrics(user.getUsername(), role));
        return "panel";
    }

    @GetMapping("/citas")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','SUPERADMINISTRADOR','SECRETARIA','DOCENTE','APODERADO')")
    public String appointments() {
        return "citas";
    }

    @GetMapping("/notificaciones")
    public String notifications() {
        return "notificaciones";
    }

    @GetMapping("/perfil")
    public String profile() {
        return "perfil";
    }

    @GetMapping("/reportes")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','SUPERADMINISTRADOR','SECRETARIA','DIRECTOR')")
    public String reports() {
        return "reportes";
    }

    @GetMapping("/acceso-denegado")
    public String forbidden() {
        return "acceso-denegado";
    }
}
