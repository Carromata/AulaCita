package pe.edu.aulacita.appointments;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.sql.Timestamp;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import static pe.edu.aulacita.appointments.AppointmentMediaDtos.*;

@Service
public class AppointmentMediaService {
    private static final Set<String> IMAGE_TYPES = Set.of(
            "image/jpeg", "image/png", "image/webp", "image/gif");
    private static final long MAX_BYTES = 3_000_000L;

    private final JdbcTemplate jdbc;
    private final AppointmentService appointments;
    private final Path uploadRoot;

    public AppointmentMediaService(JdbcTemplate jdbc,
                                   AppointmentService appointments,
                                   @Value("${aulacita.upload-dir:uploads}") String uploadDir) throws IOException {
        this.jdbc = jdbc;
        this.appointments = appointments;
        this.uploadRoot = Path.of(uploadDir, "citas").toAbsolutePath().normalize();
        Files.createDirectories(this.uploadRoot);
    }

    public List<AppointmentImageView> list(String email, String role, int appointmentId) {
        appointments.authorizeAccess(email, role, appointmentId);
        return jdbc.query("""
                SELECT ci.IdImagen, ci.NombreOriginal, ci.ContentType, ci.TamanoBytes,
                       ci.FechaSubida, CONCAT(u.Nombres, ' ', u.ApellidoPaterno) AS SubidoPor
                FROM dbo.CitaImagen ci
                JOIN dbo.Usuario u ON u.IdUsuario = ci.SubidoPorIdUsuario
                WHERE ci.IdCita = ? AND ci.Activo = 1
                ORDER BY ci.FechaSubida DESC
                """, (rs, row) -> {
            int imageId = rs.getInt("IdImagen");
            Timestamp uploaded = rs.getTimestamp("FechaSubida");
            return new AppointmentImageView(
                    imageId,
                    "/api/v1/citas/" + appointmentId + "/imagenes/" + imageId + "/contenido",
                    rs.getString("NombreOriginal"),
                    rs.getString("ContentType"),
                    rs.getLong("TamanoBytes"),
                    uploaded == null ? null : uploaded.toInstant().toString(),
                    rs.getString("SubidoPor"));
        }, appointmentId);
    }

    @Transactional
    public AppointmentImageView upload(String email, String role, int appointmentId, MultipartFile file) {
        appointments.authorizeAccess(email, role, appointmentId);
        if (file == null || file.isEmpty()) {
            throw new AppointmentException(HttpStatus.BAD_REQUEST, "Debe seleccionar una imagen.");
        }
        if (file.getSize() > MAX_BYTES) {
            throw new AppointmentException(HttpStatus.BAD_REQUEST, "La imagen no puede superar 3 MB.");
        }
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (!IMAGE_TYPES.contains(contentType)) {
            throw new AppointmentException(HttpStatus.BAD_REQUEST, "Solo se admiten JPG, PNG, WEBP o GIF.");
        }
        Integer userId = jdbc.query("""
                SELECT IdUsuario FROM dbo.Usuario WHERE LOWER(Correo) = LOWER(?) AND Activo = 1
                """, rs -> rs.next() ? rs.getInt(1) : null, email);
        if (userId == null) {
            throw new AppointmentException(HttpStatus.FORBIDDEN, "La cuenta no está disponible.");
        }
        String storedName = "cita-" + appointmentId + "-" + UUID.randomUUID() + extensionFor(contentType);
        Path target = uploadRoot.resolve(storedName).normalize();
        if (!target.startsWith(uploadRoot)) {
            throw new AppointmentException(HttpStatus.BAD_REQUEST, "Ruta de archivo inválida.");
        }
        try {
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new AppointmentException(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo guardar la imagen.");
        }
        jdbc.update("""
                INSERT INTO dbo.CitaImagen
                    (IdCita, NombreOriginal, NombreAlmacenado, ContentType, TamanoBytes, SubidoPorIdUsuario)
                VALUES (?, ?, ?, ?, ?, ?)
                """, appointmentId, safeName(file.getOriginalFilename()), storedName,
                contentType, file.getSize(), userId);
        int imageId = jdbc.queryForObject("SELECT CONVERT(int, SCOPE_IDENTITY())", Integer.class);
        return list(email, role, appointmentId).stream()
                .filter(image -> image.id() == imageId)
                .findFirst()
                .orElseThrow(() -> new AppointmentException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "No se pudo leer la imagen guardada."));
    }

    public StoredImage content(String email, String role, int appointmentId, int imageId) {
        appointments.authorizeAccess(email, role, appointmentId);
        var row = jdbc.query("""
                SELECT NombreAlmacenado, ContentType
                FROM dbo.CitaImagen
                WHERE IdImagen = ? AND IdCita = ? AND Activo = 1
                """, rs -> rs.next()
                ? new String[]{rs.getString(1), rs.getString(2)}
                : null, imageId, appointmentId);
        if (row == null) {
            throw new AppointmentException(HttpStatus.NOT_FOUND, "Imagen no encontrada.");
        }
        Path path = uploadRoot.resolve(row[0]).normalize();
        if (!path.startsWith(uploadRoot) || !Files.isRegularFile(path)) {
            throw new AppointmentException(HttpStatus.NOT_FOUND, "Imagen no encontrada.");
        }
        return new StoredImage(path, row[1]);
    }

    @Transactional
    public void delete(String email, String role, int appointmentId, int imageId) {
        appointments.authorizeAccess(email, role, appointmentId);
        String stored = jdbc.query("""
                SELECT NombreAlmacenado FROM dbo.CitaImagen
                WHERE IdImagen = ? AND IdCita = ? AND Activo = 1
                """, rs -> rs.next() ? rs.getString(1) : null, imageId, appointmentId);
        if (stored == null) {
            throw new AppointmentException(HttpStatus.NOT_FOUND, "Imagen no encontrada.");
        }
        jdbc.update("""
                UPDATE dbo.CitaImagen SET Activo = 0
                WHERE IdImagen = ? AND IdCita = ?
                """, imageId, appointmentId);
        try {
            Files.deleteIfExists(uploadRoot.resolve(stored));
        } catch (IOException ignored) {
            // La imagen queda inactiva aunque no se pueda borrar el archivo físico.
        }
    }

    private static String safeName(String name) {
        if (name == null || name.isBlank()) {
            return "imagen";
        }
        return name.length() > 255 ? name.substring(0, 255) : name;
    }

    private static String extensionFor(String contentType) {
        return switch (contentType) {
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            case "image/gif" -> ".gif";
            default -> ".jpg";
        };
    }
}
