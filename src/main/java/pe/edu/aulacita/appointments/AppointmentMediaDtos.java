package pe.edu.aulacita.appointments;

import java.nio.file.Path;

public final class AppointmentMediaDtos {
    private AppointmentMediaDtos() {
    }

    public record AppointmentImageView(
            int id,
            String url,
            String originalName,
            String contentType,
            long size,
            String uploadedAt,
            String uploadedBy) {
    }

    public record StoredImage(Path path, String contentType) {
    }
}
