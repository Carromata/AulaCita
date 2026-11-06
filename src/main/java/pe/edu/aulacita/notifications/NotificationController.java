package pe.edu.aulacita.notifications;

import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import pe.edu.aulacita.appointments.AppointmentApiController.ApiResponse;

import java.util.List;
import java.util.Map;

import static pe.edu.aulacita.notifications.NotificationDtos.*;

@RestController
public class NotificationController {
    private final NotificationService notifications;

    public NotificationController(NotificationService notifications) {
        this.notifications = notifications;
    }

    @GetMapping("/api/v1/notificaciones")
    public ApiResponse<List<NotificationView>> list(
            @AuthenticationPrincipal UserDetails user,
            @RequestParam(defaultValue = "false") boolean soloNoLeidas,
            @RequestParam(defaultValue = "30") int limite) {
        return ApiResponse.of(notifications.list(user.getUsername(), soloNoLeidas, limite));
    }

    @GetMapping("/api/v1/notificaciones/resumen")
    public ApiResponse<Map<String, Integer>> summary(@AuthenticationPrincipal UserDetails user) {
        return ApiResponse.of(Map.of("unread", notifications.unreadCount(user.getUsername())));
    }

    @GetMapping("/api/v1/notificaciones/panel")
    public ApiResponse<List<NotificationView>> panel(@AuthenticationPrincipal UserDetails user) {
        return ApiResponse.of(notifications.recentForPanel(user.getUsername()));
    }

    @PostMapping("/api/v1/notificaciones/{id}/leer")
    public ApiResponse<Map<String, String>> markRead(@AuthenticationPrincipal UserDetails user,
                                                     @PathVariable int id) {
        notifications.markRead(user.getUsername(), id);
        return ApiResponse.of(Map.of("message", "Notificación marcada como leída."));
    }

    @PostMapping("/api/v1/notificaciones/leer-todas")
    public ApiResponse<Map<String, String>> markAllRead(@AuthenticationPrincipal UserDetails user) {
        notifications.markAllRead(user.getUsername());
        return ApiResponse.of(Map.of("message", "Notificaciones marcadas como leídas."));
    }

    @PostMapping("/api/v1/admin/notificaciones")
    @PreAuthorize("hasRole('SUPERADMINISTRADOR')")
    public ApiResponse<Map<String, Object>> broadcast(@AuthenticationPrincipal UserDetails user,
                                                      @Valid @RequestBody BroadcastRequest request) {
        int sent = notifications.broadcast(user.getUsername(), request);
        return ApiResponse.of(Map.of("message", "Aviso enviado a " + sent + " usuario(s).", "sent", sent));
    }
}
