package pe.edu.aulacita.appointments;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import pe.edu.aulacita.appointments.AppointmentApiController.ApiResponse;

import java.util.List;
import java.util.Map;

import static pe.edu.aulacita.appointments.AppointmentMediaDtos.*;

@RestController
@RequestMapping("/api/v1/citas/{appointmentId}/imagenes")
@PreAuthorize("hasAnyRole('APODERADO','DOCENTE','ADMINISTRADOR','SUPERADMINISTRADOR','SECRETARIA')")
public class AppointmentMediaController {
    private final AppointmentMediaService media;

    public AppointmentMediaController(AppointmentMediaService media) {
        this.media = media;
    }

    @GetMapping
    public ApiResponse<List<AppointmentImageView>> list(@AuthenticationPrincipal UserDetails user,
                                                        @PathVariable int appointmentId) {
        return ApiResponse.of(media.list(user.getUsername(), role(user), appointmentId));
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AppointmentImageView> upload(@AuthenticationPrincipal UserDetails user,
                                                    @PathVariable int appointmentId,
                                                    @RequestParam("archivo") MultipartFile archivo) {
        return ApiResponse.of(media.upload(user.getUsername(), role(user), appointmentId, archivo));
    }

    @GetMapping("/{imageId}/contenido")
    public ResponseEntity<Resource> content(@AuthenticationPrincipal UserDetails user,
                                            @PathVariable int appointmentId,
                                            @PathVariable int imageId) {
        StoredImage stored = media.content(user.getUsername(), role(user), appointmentId, imageId);
        return ResponseEntity.ok()
                .header(HttpHeaders.CACHE_CONTROL, "private, max-age=600")
                .contentType(MediaType.parseMediaType(stored.contentType()))
                .body(new FileSystemResource(stored.path()));
    }

    @DeleteMapping("/{imageId}")
    public ApiResponse<Map<String, String>> delete(@AuthenticationPrincipal UserDetails user,
                                                   @PathVariable int appointmentId,
                                                   @PathVariable int imageId) {
        media.delete(user.getUsername(), role(user), appointmentId, imageId);
        return ApiResponse.of(Map.of("message", "Imagen eliminada."));
    }

    private static String role(UserDetails user) {
        return user.getAuthorities().stream()
                .map(authority -> authority.getAuthority().replaceFirst("^ROLE_", ""))
                .filter(value -> !value.isBlank())
                .findFirst()
                .orElseThrow(() -> new AppointmentException(HttpStatus.FORBIDDEN, "La cuenta no tiene un rol."));
    }
}
