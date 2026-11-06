package pe.edu.aulacita.school;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import pe.edu.aulacita.appointments.AppointmentException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import static pe.edu.aulacita.school.SchoolDtos.*;

@Service
public class SchoolService {
    private static final Set<String> IMAGE_KEYS = Set.of("logo", "hero", "galeria_1", "galeria_2", "galeria_3");
    private static final Set<String> IMAGE_TYPES = Set.of("image/jpeg", "image/png", "image/webp", "image/gif");
    private static final Set<String> CREATABLE_ROLES =
            Set.of("Administrador", "Secretaria", "Director", "Docente", "Apoderado");

    private final JdbcTemplate jdbc;
    private final Path uploadRoot;
    private final org.springframework.security.crypto.password.PasswordEncoder encoder;

    public SchoolService(JdbcTemplate jdbc,
                         org.springframework.security.crypto.password.PasswordEncoder encoder,
                         @Value("${aulacita.upload-dir:uploads}") String uploadDir) throws IOException {
        this.jdbc = jdbc;
        this.encoder = encoder;
        this.uploadRoot = Path.of(uploadDir).toAbsolutePath().normalize();
        Files.createDirectories(this.uploadRoot);
    }

    public StudentVerification verifyDni(String dni) {
        String normalized = normalizeDni(dni);
        return jdbc.query("""
                SELECT e.Dni, e.Nombres, e.ApellidoPaterno, e.ApellidoMaterno,
                       g.Nivel, g.Grado, g.Seccion
                FROM dbo.Estudiante e
                JOIN dbo.GradoSeccion g ON g.IdGradoSeccion = e.IdGradoSeccion
                WHERE e.Dni = ? AND e.Activo = 1
                """, rs -> {
            if (!rs.next()) {
                return new StudentVerification(false, normalized, null, null);
            }
            String mother = rs.getString("ApellidoMaterno");
            String fullName = rs.getString("Nombres") + " " + rs.getString("ApellidoPaterno")
                    + (mother == null || mother.isBlank() ? "" : " " + mother);
            String grade = rs.getString("Nivel") + " · " + rs.getString("Grado") + " · " + rs.getString("Seccion");
            return new StudentVerification(true, rs.getString("Dni"), fullName.trim(), grade);
        }, normalized);
    }

    public List<StudentAdminView> listStudents() {
        return jdbc.query("""
                SELECT e.IdEstudiante, e.Dni, e.CodigoEstudiante, e.Nombres, e.ApellidoPaterno, e.ApellidoMaterno,
                       e.IdGradoSeccion, g.Nivel, g.Grado, g.Seccion, e.Activo
                FROM dbo.Estudiante e
                JOIN dbo.GradoSeccion g ON g.IdGradoSeccion = e.IdGradoSeccion
                ORDER BY e.ApellidoPaterno, e.Nombres
                """, (rs, row) -> new StudentAdminView(
                rs.getInt("IdEstudiante"),
                rs.getString("Dni"),
                rs.getString("CodigoEstudiante"),
                rs.getString("Nombres"),
                rs.getString("ApellidoPaterno"),
                rs.getString("ApellidoMaterno"),
                rs.getInt("IdGradoSeccion"),
                rs.getString("Nivel") + " · " + rs.getString("Grado") + " · " + rs.getString("Seccion"),
                rs.getBoolean("Activo")));
    }

    public List<GradeSectionView> listGrades() {
        return jdbc.query("""
                SELECT IdGradoSeccion, Nivel, Grado, Seccion
                FROM dbo.GradoSeccion
                WHERE Activo = 1
                ORDER BY Nivel, Grado, Seccion
                """, (rs, row) -> {
            String level = rs.getString("Nivel");
            String grade = rs.getString("Grado");
            String section = rs.getString("Seccion");
            return new GradeSectionView(rs.getInt("IdGradoSeccion"), level, grade, section,
                    level + " · " + grade + " · " + section);
        });
    }

    @Transactional
    public StudentAdminView createStudent(StudentUpsertRequest request) {
        String dni = normalizeDni(request.dni());
        ensureGrade(request.gradeSectionId());
        try {
            jdbc.update("""
                    INSERT INTO dbo.Estudiante
                        (IdGradoSeccion, CodigoEstudiante, Nombres, ApellidoPaterno, ApellidoMaterno, Dni, Activo)
                    VALUES (?, ?, ?, ?, ?, ?, 1)
                    """, request.gradeSectionId(), blankToNull(request.code()), request.firstName().trim(),
                    request.lastName().trim(), blankToNull(request.motherLastName()), dni);
        } catch (DuplicateKeyException ex) {
            throw new AppointmentException(HttpStatus.CONFLICT, "Ya existe un estudiante con ese DNI.");
        }
        Integer id = jdbc.queryForObject("SELECT MAX(IdEstudiante) FROM dbo.Estudiante WHERE Dni = ?",
                Integer.class, dni);
        return listStudents().stream().filter(s -> s.id() == id).findFirst()
                .orElseThrow(() -> new AppointmentException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "No se pudo leer el estudiante creado."));
    }

    @Transactional
    public StudentAdminView updateStudent(int id, StudentUpsertRequest request) {
        ensureStudent(id);
        ensureGrade(request.gradeSectionId());
        String dni = normalizeDni(request.dni());
        boolean active = request.active() == null || request.active();
        try {
            int changed = jdbc.update("""
                    UPDATE dbo.Estudiante
                    SET IdGradoSeccion = ?, CodigoEstudiante = ?, Nombres = ?, ApellidoPaterno = ?,
                        ApellidoMaterno = ?, Dni = ?, Activo = ?
                    WHERE IdEstudiante = ?
                    """, request.gradeSectionId(), blankToNull(request.code()), request.firstName().trim(),
                    request.lastName().trim(), blankToNull(request.motherLastName()), dni, active, id);
            if (changed != 1) {
                throw new AppointmentException(HttpStatus.NOT_FOUND, "El estudiante no existe.");
            }
        } catch (DuplicateKeyException ex) {
            throw new AppointmentException(HttpStatus.CONFLICT, "Ya existe un estudiante con ese DNI.");
        }
        return listStudents().stream().filter(s -> s.id() == id).findFirst()
                .orElseThrow(() -> new AppointmentException(HttpStatus.NOT_FOUND, "El estudiante no existe."));
    }

    public InstitutionView institutionPublic() {
        var config = jdbc.query("""
                SELECT NombreInstitucion, Slogan FROM dbo.InstitucionConfig WHERE IdConfig = 1
                """, rs -> rs.next()
                ? new String[]{rs.getString(1), rs.getString(2)}
                : new String[]{"AulaCita", "Familias y docentes, mejor conectados."});
        return new InstitutionView(config[0], config[1], listImages());
    }

    public List<ImageView> listImages() {
        return jdbc.query("""
                SELECT Clave, NombreOriginal, ContentType, FechaActualizacion
                FROM dbo.InstitucionImagen
                WHERE Activo = 1
                ORDER BY Clave
                """, (rs, row) -> {
            String key = rs.getString("Clave");
            return new ImageView(key, "/media/institucion/" + key, rs.getString("NombreOriginal"),
                    rs.getString("ContentType"), rs.getTimestamp("FechaActualizacion").toInstant().toString());
        });
    }

    @Transactional
    public ImageView saveImage(String email, String key, MultipartFile file) {
        String normalizedKey = key == null ? "" : key.trim().toLowerCase(Locale.ROOT);
        if (!IMAGE_KEYS.contains(normalizedKey)) {
            throw new AppointmentException(HttpStatus.BAD_REQUEST, "Clave de imagen no permitida.");
        }
        if (file == null || file.isEmpty()) {
            throw new AppointmentException(HttpStatus.BAD_REQUEST, "Debe seleccionar una imagen.");
        }
        if (file.getSize() > 3_000_000) {
            throw new AppointmentException(HttpStatus.BAD_REQUEST, "La imagen no puede superar 3 MB.");
        }
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        if (!IMAGE_TYPES.contains(contentType)) {
            throw new AppointmentException(HttpStatus.BAD_REQUEST, "Solo se admiten JPG, PNG, WEBP o GIF.");
        }
        Integer userId = jdbc.queryForObject(
                "SELECT IdUsuario FROM dbo.Usuario WHERE LOWER(Correo) = LOWER(?) AND Activo = 1",
                Integer.class, email);
        if (userId == null) {
            throw new AppointmentException(HttpStatus.FORBIDDEN, "La cuenta no está disponible.");
        }
        String extension = extensionFor(contentType);
        String storedName = normalizedKey + "-" + UUID.randomUUID() + extension;
        Path target = uploadRoot.resolve(storedName).normalize();
        if (!target.startsWith(uploadRoot)) {
            throw new AppointmentException(HttpStatus.BAD_REQUEST, "Ruta de archivo inválida.");
        }
        try {
            Files.copy(file.getInputStream(), target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException ex) {
            throw new AppointmentException(HttpStatus.INTERNAL_SERVER_ERROR, "No se pudo guardar la imagen.");
        }

        String previous = jdbc.query("""
                SELECT NombreAlmacenado FROM dbo.InstitucionImagen WHERE Clave = ?
                """, rs -> rs.next() ? rs.getString(1) : null, normalizedKey);

        int updated = jdbc.update("""
                UPDATE dbo.InstitucionImagen
                SET NombreOriginal = ?, NombreAlmacenado = ?, ContentType = ?, TamanoBytes = ?,
                    Activo = 1, ActualizadoPorIdUsuario = ?, FechaActualizacion = SYSUTCDATETIME()
                WHERE Clave = ?
                """, file.getOriginalFilename(), storedName, contentType, file.getSize(), userId, normalizedKey);
        if (updated == 0) {
            jdbc.update("""
                    INSERT INTO dbo.InstitucionImagen
                        (Clave, NombreOriginal, NombreAlmacenado, ContentType, TamanoBytes, ActualizadoPorIdUsuario)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """, normalizedKey, file.getOriginalFilename(), storedName, contentType, file.getSize(), userId);
        }
        if (previous != null && !previous.equals(storedName)) {
            try {
                Files.deleteIfExists(uploadRoot.resolve(previous));
            } catch (IOException ignored) {
                // keep new image even if cleanup fails
            }
        }
        return listImages().stream().filter(img -> img.key().equals(normalizedKey)).findFirst()
                .orElseThrow(() -> new AppointmentException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "No se pudo leer la imagen guardada."));
    }

    public Path resolveStoredImage(String key) {
        String normalizedKey = key == null ? "" : key.trim().toLowerCase(Locale.ROOT);
        if (!IMAGE_KEYS.contains(normalizedKey)) {
            throw new AppointmentException(HttpStatus.NOT_FOUND, "Imagen no encontrada.");
        }
        String stored = jdbc.query("""
                SELECT NombreAlmacenado FROM dbo.InstitucionImagen WHERE Clave = ? AND Activo = 1
                """, rs -> rs.next() ? rs.getString(1) : null, normalizedKey);
        if (stored == null) {
            throw new AppointmentException(HttpStatus.NOT_FOUND, "Imagen no encontrada.");
        }
        Path path = uploadRoot.resolve(stored).normalize();
        if (!path.startsWith(uploadRoot) || !Files.isRegularFile(path)) {
            throw new AppointmentException(HttpStatus.NOT_FOUND, "Imagen no encontrada.");
        }
        return path;
    }

    public String imageContentType(String key) {
        String normalizedKey = key == null ? "" : key.trim().toLowerCase(Locale.ROOT);
        String type = jdbc.query("""
                SELECT ContentType FROM dbo.InstitucionImagen WHERE Clave = ? AND Activo = 1
                """, rs -> rs.next() ? rs.getString(1) : null, normalizedKey);
        return type == null ? "application/octet-stream" : type;
    }

    public List<UserAdminView> listUsers(String actorRole) {
        boolean superAdmin = "SUPERADMINISTRADOR".equals(actorRole);
        return jdbc.query("""
                SELECT u.IdUsuario, u.Correo, u.Nombres, u.ApellidoPaterno, u.ApellidoMaterno,
                       u.Celular, u.TipoDocumento, u.NumeroDocumento, r.Nombre, u.Activo
                FROM dbo.Usuario u
                JOIN dbo.Rol r ON r.IdRol = u.IdRol
                WHERE r.Nombre <> N'SuperAdministrador'
                  AND (? = 1 OR r.Nombre IN (N'Docente', N'Apoderado'))
                ORDER BY r.Nombre, u.ApellidoPaterno, u.Nombres
                """, (rs, row) -> {
            String mother = rs.getString("ApellidoMaterno");
            String full = rs.getString("Nombres") + " " + rs.getString("ApellidoPaterno")
                    + (mother == null || mother.isBlank() ? "" : " " + mother);
            return new UserAdminView(rs.getInt("IdUsuario"), rs.getString("Correo"), full.trim(),
                    rs.getString("Nombres"), rs.getString("ApellidoPaterno"), mother,
                    rs.getString("Nombre"), rs.getBoolean("Activo"), rs.getString("Celular"),
                    rs.getString("TipoDocumento"), rs.getString("NumeroDocumento"));
        }, superAdmin ? 1 : 0);
    }

    public ProfileView getProfile(String email) {
        return jdbc.query("""
                SELECT u.Correo, u.Nombres, u.ApellidoPaterno, u.ApellidoMaterno, u.Celular,
                       u.TipoDocumento, u.NumeroDocumento, r.Nombre
                FROM dbo.Usuario u
                JOIN dbo.Rol r ON r.IdRol = u.IdRol
                WHERE LOWER(u.Correo) = LOWER(?) AND u.Activo = 1
                """, rs -> {
            if (!rs.next()) {
                throw new AppointmentException(HttpStatus.NOT_FOUND, "No se encontró el perfil activo.");
            }
            return new ProfileView(rs.getString("Correo"), rs.getString("Nombres"),
                    rs.getString("ApellidoPaterno"), rs.getString("ApellidoMaterno"),
                    rs.getString("Nombre"), rs.getString("Celular"),
                    rs.getString("TipoDocumento"), rs.getString("NumeroDocumento"));
        }, email);
    }

    @Transactional
    public ProfileView updateProfile(String email, ProfileUpdateRequest request) {
        int changed = jdbc.update("""
                UPDATE dbo.Usuario
                SET Nombres = ?, ApellidoPaterno = ?, ApellidoMaterno = ?, Celular = ?,
                    TipoDocumento = ?, NumeroDocumento = ?
                WHERE LOWER(Correo) = LOWER(?) AND Activo = 1
                """, request.firstName().trim(), request.lastName().trim(),
                blankToNull(request.motherLastName()), blankToNull(request.phone()),
                blankToNull(request.documentType()), blankToNull(request.documentNumber()), email);
        if (changed != 1) {
            throw new AppointmentException(HttpStatus.NOT_FOUND, "No se encontró el perfil activo.");
        }
        return getProfile(email);
    }

    @Transactional
    public UserAdminView createUser(String actorRole, UserCreateRequest request) {
        ensureAssignableRole(actorRole, request.role());
        Integer exists = jdbc.queryForObject(
                "SELECT COUNT(*) FROM dbo.Usuario WHERE LOWER(Correo) = LOWER(?)",
                Integer.class, request.email());
        if (exists != null && exists > 0) {
            throw new AppointmentException(HttpStatus.CONFLICT, "El correo ya está registrado.");
        }
        Integer roleId = jdbc.queryForObject(
                "SELECT IdRol FROM dbo.Rol WHERE Nombre = ?", Integer.class, request.role());
        String normalizedEmail = request.email().trim().toLowerCase(Locale.ROOT);
        try {
            jdbc.update("""
                INSERT INTO dbo.Usuario
                    (IdRol, Nombres, ApellidoPaterno, ApellidoMaterno, Correo, PasswordHash,
                     Celular, TipoDocumento, NumeroDocumento, Activo)
                VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 1)
                """, roleId, request.firstName().trim(), request.lastName().trim(),
                    blankToNull(request.motherLastName()), normalizedEmail, encoder.encode(request.password()),
                    blankToNull(request.phone()), blankToNull(request.documentType()),
                    blankToNull(request.documentNumber()));
        } catch (DuplicateKeyException ex) {
            throw new AppointmentException(HttpStatus.CONFLICT, "El correo ya está registrado.");
        }
        if ("Docente".equals(request.role())) {
            Integer hasArea = jdbc.queryForObject("""
                    SELECT CASE WHEN COL_LENGTH('dbo.Docente', 'AreaEspecialidad') IS NULL THEN 0 ELSE 1 END
                    """, Integer.class);
            if (hasArea != null && hasArea == 1) {
                jdbc.update("""
                        INSERT INTO dbo.Docente (IdUsuario, AreaEspecialidad, PermiteReservas, Activo)
                        SELECT IdUsuario, N'General', 1, 1 FROM dbo.Usuario WHERE LOWER(Correo) = LOWER(?)
                        """, normalizedEmail);
            } else {
                jdbc.update("""
                        INSERT INTO dbo.Docente (IdUsuario, Activo)
                        SELECT IdUsuario, 1 FROM dbo.Usuario WHERE LOWER(Correo) = LOWER(?)
                        """, normalizedEmail);
            }
        }
        if ("Apoderado".equals(request.role())) {
            jdbc.update("""
                    INSERT INTO dbo.Apoderado (IdUsuario, Activo)
                    SELECT IdUsuario, 1 FROM dbo.Usuario WHERE LOWER(Correo) = LOWER(?)
                    """, normalizedEmail);
        }
        return listUsers(actorRole).stream()
                .filter(u -> u.email().equalsIgnoreCase(normalizedEmail))
                .findFirst()
                .orElseThrow(() -> new AppointmentException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "No se pudo leer el usuario creado."));
    }

    @Transactional
    public UserAdminView updateUser(String actorRole, String actorEmail, int userId, UserUpdateRequest request) {
        ensureAssignableRole(actorRole, request.role());
        String targetRole = requireManageableUser(actorRole, userId);
        int actorId = localUserId(actorEmail);
        if (userId == actorId && !request.role().equals(targetRole)) {
            throw new AppointmentException(HttpStatus.BAD_REQUEST, "No puede cambiar su propio rol.");
        }
        boolean active = request.active() == null || request.active();
        if (userId == actorId && !active) {
            throw new AppointmentException(HttpStatus.BAD_REQUEST, "No puede desactivar su propia cuenta.");
        }
        Integer roleId = jdbc.queryForObject(
                "SELECT IdRol FROM dbo.Rol WHERE Nombre = ?", Integer.class, request.role());
        int changed = jdbc.update("""
                UPDATE dbo.Usuario
                SET Nombres = ?, ApellidoPaterno = ?, ApellidoMaterno = ?, Celular = ?,
                    TipoDocumento = ?, NumeroDocumento = ?, IdRol = ?, Activo = ?
                WHERE IdUsuario = ?
                """, request.firstName().trim(), request.lastName().trim(),
                blankToNull(request.motherLastName()), blankToNull(request.phone()),
                    blankToNull(request.documentType()), blankToNull(request.documentNumber()),
                    roleId, active, userId);
        if (changed != 1) {
            throw new AppointmentException(HttpStatus.NOT_FOUND, "Usuario no encontrado.");
        }
        syncRoleRows(userId, request.role());
        return listUsers(actorRole).stream()
                .filter(u -> u.id() == userId)
                .findFirst()
                .orElseThrow(() -> new AppointmentException(HttpStatus.INTERNAL_SERVER_ERROR,
                        "No se pudo leer el usuario actualizado."));
    }

    @Transactional
    public void resetPassword(String actorRole, int userId, String password) {
        requireManageableUser(actorRole, userId);
        int changed = jdbc.update("""
                UPDATE dbo.Usuario SET PasswordHash = ? WHERE IdUsuario = ?
                """, encoder.encode(password), userId);
        if (changed != 1) {
            throw new AppointmentException(HttpStatus.NOT_FOUND, "Usuario no encontrado.");
        }
    }

    @Transactional
    public void setUserActive(String actorRole, String actorEmail, int userId, boolean active) {
        requireManageableUser(actorRole, userId);
        int actorId = localUserId(actorEmail);
        if (userId == actorId && !active) {
            throw new AppointmentException(HttpStatus.BAD_REQUEST, "No puede desactivar su propia cuenta.");
        }
        jdbc.update("UPDATE dbo.Usuario SET Activo = ? WHERE IdUsuario = ?", active, userId);
    }

    public List<RelationAdminView> listRelations() {
        return jdbc.query("""
                SELECT ea.IdEstudianteApoderado, e.IdEstudiante,
                       CONCAT(e.Nombres, ' ', e.ApellidoPaterno, COALESCE(' ' + e.ApellidoMaterno, '')) AS Estudiante,
                       a.IdApoderado,
                       CONCAT(ua.Nombres, ' ', ua.ApellidoPaterno) AS Apoderado,
                       ea.Parentesco, ea.PuedeReservar, ea.Activo
                FROM dbo.EstudianteApoderado ea
                JOIN dbo.Estudiante e ON e.IdEstudiante = ea.IdEstudiante
                JOIN dbo.Apoderado a ON a.IdApoderado = ea.IdApoderado
                JOIN dbo.Usuario ua ON ua.IdUsuario = a.IdUsuario
                ORDER BY Estudiante, Apoderado
                """, (rs, row) -> new RelationAdminView(
                rs.getInt("IdEstudianteApoderado"),
                rs.getInt("IdEstudiante"),
                rs.getString("Estudiante"),
                rs.getInt("IdApoderado"),
                rs.getString("Apoderado"),
                rs.getString("Parentesco"),
                rs.getBoolean("PuedeReservar"),
                rs.getBoolean("Activo")));
    }

    public List<GuardianOption> listGuardians() {
        return jdbc.query("""
                SELECT a.IdApoderado,
                       CONCAT(u.Nombres, ' ', u.ApellidoPaterno, COALESCE(' ' + u.ApellidoMaterno, '')) AS Nombre
                FROM dbo.Apoderado a
                JOIN dbo.Usuario u ON u.IdUsuario = a.IdUsuario
                WHERE a.Activo = 1 AND u.Activo = 1
                ORDER BY Nombre
                """, (rs, row) -> new GuardianOption(rs.getInt(1), rs.getString(2)));
    }

    @Transactional
    public RelationAdminView createRelation(RelationUpsertRequest request) {
        ensureStudent(request.studentId());
        ensureGuardian(request.guardianId());
        boolean canBook = request.canBook() == null || request.canBook();
        try {
            jdbc.update("""
                    INSERT INTO dbo.EstudianteApoderado
                        (IdEstudiante, IdApoderado, Parentesco, PuedeReservar, Activo)
                    VALUES (?, ?, ?, ?, 1)
                    """, request.studentId(), request.guardianId(),
                    request.relationship().trim(), canBook);
        } catch (DuplicateKeyException ex) {
            throw new AppointmentException(HttpStatus.CONFLICT,
                    "Ese estudiante ya está vinculado con ese apoderado.");
        }
        int id = jdbc.queryForObject("SELECT CONVERT(int, SCOPE_IDENTITY())", Integer.class);
        return relationById(id);
    }

    @Transactional
    public RelationAdminView updateRelation(int id, RelationUpsertRequest request) {
        ensureStudent(request.studentId());
        ensureGuardian(request.guardianId());
        boolean canBook = request.canBook() == null || request.canBook();
        try {
            int changed = jdbc.update("""
                    UPDATE dbo.EstudianteApoderado
                    SET IdEstudiante = ?, IdApoderado = ?, Parentesco = ?, PuedeReservar = ?
                    WHERE IdEstudianteApoderado = ?
                    """, request.studentId(), request.guardianId(),
                    request.relationship().trim(), canBook, id);
            if (changed != 1) {
                throw new AppointmentException(HttpStatus.NOT_FOUND, "La relación no existe.");
            }
        } catch (DuplicateKeyException ex) {
            throw new AppointmentException(HttpStatus.CONFLICT,
                    "Ese estudiante ya está vinculado con ese apoderado.");
        }
        return relationById(id);
    }

    private RelationAdminView relationById(int id) {
        return listRelations().stream()
                .filter(relation -> relation.id() == id)
                .findFirst()
                .orElseThrow(() -> new AppointmentException(HttpStatus.NOT_FOUND, "La relación no existe."));
    }

    private String requireManageableUser(String actorRole, int userId) {
        String target = jdbc.query("""
                SELECT r.Nombre FROM dbo.Usuario u
                JOIN dbo.Rol r ON r.IdRol = u.IdRol
                WHERE u.IdUsuario = ?
                """, rs -> rs.next() ? rs.getString(1) : null, userId);
        if (target == null) {
            throw new AppointmentException(HttpStatus.NOT_FOUND, "Usuario no encontrado.");
        }
        if (!manageableRoles(actorRole).contains(target)) {
            throw new AppointmentException(HttpStatus.FORBIDDEN,
                    "No tiene permiso para gestionar una cuenta con ese rol.");
        }
        return target;
    }

    private static void ensureAssignableRole(String actorRole, String targetRole) {
        if (!manageableRoles(actorRole).contains(targetRole)) {
            throw new AppointmentException(HttpStatus.FORBIDDEN,
                    "No tiene permiso para asignar ese rol.");
        }
    }

    private static Set<String> manageableRoles(String actorRole) {
        if ("SUPERADMINISTRADOR".equals(actorRole)) {
            return CREATABLE_ROLES;
        }
        if ("ADMINISTRADOR".equals(actorRole)) {
            return Set.of("Docente", "Apoderado");
        }
        return Set.of();
    }

    private int localUserId(String email) {
        Integer id = jdbc.query("""
                SELECT IdUsuario FROM dbo.Usuario WHERE LOWER(Correo) = LOWER(?)
                """, rs -> rs.next() ? rs.getInt(1) : null, email);
        if (id == null) {
            throw new AppointmentException(HttpStatus.FORBIDDEN, "La cuenta no está disponible.");
        }
        return id;
    }

    private void syncRoleRows(int userId, String role) {
        boolean teacher = "Docente".equals(role);
        boolean guardian = "Apoderado".equals(role);
        jdbc.update("UPDATE dbo.Docente SET Activo = ? WHERE IdUsuario = ?", teacher, userId);
        jdbc.update("UPDATE dbo.Apoderado SET Activo = ? WHERE IdUsuario = ?", guardian, userId);
        if (teacher) {
            jdbc.update("""
                    IF NOT EXISTS (SELECT 1 FROM dbo.Docente WHERE IdUsuario = ?)
                        INSERT INTO dbo.Docente (IdUsuario, Activo) VALUES (?, 1)
                    """, userId, userId);
        }
        if (guardian) {
            jdbc.update("""
                    IF NOT EXISTS (SELECT 1 FROM dbo.Apoderado WHERE IdUsuario = ?)
                        INSERT INTO dbo.Apoderado (IdUsuario, Activo) VALUES (?, 1)
                    """, userId, userId);
        }
    }

    private void ensureGuardian(int guardianId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM dbo.Apoderado WHERE IdApoderado = ? AND Activo = 1",
                Integer.class, guardianId);
        if (count == null || count == 0) {
            throw new AppointmentException(HttpStatus.BAD_REQUEST, "El apoderado no es válido.");
        }
    }

    private void ensureGrade(int gradeSectionId) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM dbo.GradoSeccion WHERE IdGradoSeccion = ? AND Activo = 1",
                Integer.class, gradeSectionId);
        if (count == null || count == 0) {
            throw new AppointmentException(HttpStatus.BAD_REQUEST, "El grado/sección no es válido.");
        }
    }

    private void ensureStudent(int id) {
        Integer count = jdbc.queryForObject(
                "SELECT COUNT(*) FROM dbo.Estudiante WHERE IdEstudiante = ?", Integer.class, id);
        if (count == null || count == 0) {
            throw new AppointmentException(HttpStatus.NOT_FOUND, "El estudiante no existe.");
        }
    }

    private static String normalizeDni(String dni) {
        String value = dni == null ? "" : dni.trim();
        if (!value.matches("\\d{8}")) {
            throw new AppointmentException(HttpStatus.BAD_REQUEST, "El DNI debe tener exactamente 8 dígitos.");
        }
        return value;
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
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
