package pe.edu.aulacita.notifications;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public final class NotificationDtos {
    private NotificationDtos() {
    }

    public record NotificationView(
            int id,
            String title,
            String message,
            String type,
            Integer appointmentId,
            String appointmentCode,
            boolean read,
            String createdAt) {
    }

    public record BroadcastRequest(
            @NotBlank @Size(max = 160) String title,
            @NotBlank @Size(max = 1000) String message,
            @Size(max = 30) String role) {
    }
}
