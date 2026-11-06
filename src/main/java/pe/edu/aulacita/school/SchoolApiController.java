package pe.edu.aulacita.school;

import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import pe.edu.aulacita.appointments.AppointmentApiController.ApiResponse;
import pe.edu.aulacita.appointments.AppointmentException;

import java.util.List;
import java.util.Map;

import static pe.edu.aulacita.school.SchoolDtos.*;

@RestController
@RequestMapping("/api/v1")
public class SchoolApiController {
    private final SchoolService school;

    public SchoolApiController(SchoolService school) {
        this.school = school;
    }

    @GetMapping("/estudiantes/verificar")
    public ApiResponse<StudentVerification> verify(@RequestParam String dni) {
        return ApiResponse.of(school.verifyDni(dni));
    }

    @GetMapping("/institucion")
    public ApiResponse<InstitutionView> institution() {
        return ApiResponse.of(school.institutionPublic());
    }

    @GetMapping("/auth/perfil")
    public ApiResponse<ProfileView> profile(@AuthenticationPrincipal UserDetails user) {
        return ApiResponse.of(school.getProfile(user.getUsername()));
    }

    @PutMapping("/auth/perfil")
    public ApiResponse<ProfileView> updateProfile(@AuthenticationPrincipal UserDetails user,
                                                  @Valid @RequestBody ProfileUpdateRequest request) {
        return ApiResponse.of(school.updateProfile(user.getUsername(), request));
    }

    @GetMapping("/admin/estudiantes")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','SUPERADMINISTRADOR','SECRETARIA')")
    public ApiResponse<List<StudentAdminView>> students() {
        return ApiResponse.of(school.listStudents());
    }

    @GetMapping("/admin/grados")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','SUPERADMINISTRADOR','SECRETARIA')")
    public ApiResponse<List<GradeSectionView>> grades() {
        return ApiResponse.of(school.listGrades());
    }

    @PostMapping("/admin/estudiantes")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','SUPERADMINISTRADOR','SECRETARIA')")
    public ApiResponse<StudentAdminView> createStudent(@Valid @RequestBody StudentUpsertRequest request) {
        return ApiResponse.of(school.createStudent(request));
    }

    @PutMapping("/admin/estudiantes/{id}")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','SUPERADMINISTRADOR','SECRETARIA')")
    public ApiResponse<StudentAdminView> updateStudent(@PathVariable int id,
                                                       @Valid @RequestBody StudentUpsertRequest request) {
        return ApiResponse.of(school.updateStudent(id, request));
    }

    @GetMapping("/admin/imagenes")
    @PreAuthorize("hasRole('SUPERADMINISTRADOR')")
    public ApiResponse<List<ImageView>> images() {
        return ApiResponse.of(school.listImages());
    }

    @PostMapping("/admin/imagenes/{clave}")
    @PreAuthorize("hasRole('SUPERADMINISTRADOR')")
    public ApiResponse<ImageView> uploadImage(@AuthenticationPrincipal UserDetails user,
                                              @PathVariable String clave,
                                              @RequestParam("archivo") MultipartFile archivo) {
        return ApiResponse.of(school.saveImage(user.getUsername(), clave, archivo));
    }

    @GetMapping("/admin/usuarios")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','SUPERADMINISTRADOR')")
    public ApiResponse<List<UserAdminView>> users(@AuthenticationPrincipal UserDetails user) {
        return ApiResponse.of(school.listUsers(role(user)));
    }

    @PostMapping("/admin/usuarios")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','SUPERADMINISTRADOR')")
    public ApiResponse<UserAdminView> createUser(@AuthenticationPrincipal UserDetails user,
                                                 @Valid @RequestBody UserCreateRequest request) {
        return ApiResponse.of(school.createUser(role(user), request));
    }

    @PostMapping("/admin/usuarios/{id}/activo")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','SUPERADMINISTRADOR')")
    public ApiResponse<Map<String, String>> setActive(@AuthenticationPrincipal UserDetails user,
                                                      @PathVariable int id,
                                                      @RequestParam boolean activo) {
        school.setUserActive(role(user), user.getUsername(), id, activo);
        return ApiResponse.of(Map.of("message", activo ? "Usuario activado." : "Usuario desactivado."));
    }

    @PutMapping("/admin/usuarios/{id}")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','SUPERADMINISTRADOR')")
    public ApiResponse<UserAdminView> updateUser(@AuthenticationPrincipal UserDetails user,
                                                 @PathVariable int id,
                                                 @Valid @RequestBody UserUpdateRequest request) {
        return ApiResponse.of(school.updateUser(role(user), user.getUsername(), id, request));
    }

    @PostMapping("/admin/usuarios/{id}/password")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','SUPERADMINISTRADOR')")
    public ApiResponse<Map<String, String>> resetPassword(@AuthenticationPrincipal UserDetails user,
                                                          @PathVariable int id,
                                                          @Valid @RequestBody PasswordResetRequest request) {
        school.resetPassword(role(user), id, request.password());
        return ApiResponse.of(Map.of("message", "Contraseña actualizada."));
    }

    @GetMapping("/admin/relaciones")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','SUPERADMINISTRADOR','SECRETARIA')")
    public ApiResponse<List<RelationAdminView>> relations() {
        return ApiResponse.of(school.listRelations());
    }

    @PostMapping("/admin/relaciones")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','SUPERADMINISTRADOR','SECRETARIA')")
    public ApiResponse<RelationAdminView> createRelation(@Valid @RequestBody RelationUpsertRequest request) {
        return ApiResponse.of(school.createRelation(request));
    }

    @PutMapping("/admin/relaciones/{id}")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','SUPERADMINISTRADOR','SECRETARIA')")
    public ApiResponse<RelationAdminView> updateRelation(@PathVariable int id,
                                                         @Valid @RequestBody RelationUpsertRequest request) {
        return ApiResponse.of(school.updateRelation(id, request));
    }

    @GetMapping("/admin/apoderados")
    @PreAuthorize("hasAnyRole('ADMINISTRADOR','SUPERADMINISTRADOR','SECRETARIA')")
    public ApiResponse<List<GuardianOption>> guardians() {
        return ApiResponse.of(school.listGuardians());
    }

    private static String role(UserDetails user) {
        return user.getAuthorities().stream()
                .map(authority -> authority.getAuthority().replaceFirst("^ROLE_", ""))
                .filter(value -> !value.isBlank())
                .findFirst()
                .orElseThrow(() -> new AppointmentException(HttpStatus.FORBIDDEN, "La cuenta no tiene un rol."));
    }
}
