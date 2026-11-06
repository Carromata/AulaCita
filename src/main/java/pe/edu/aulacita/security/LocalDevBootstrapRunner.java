package pe.edu.aulacita.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Completa objetos faltantes y deja credenciales locales conocidas.
 * Activar solo con aulacita.bootstrap-local=true.
 */
@Component
@ConditionalOnProperty(name = "aulacita.bootstrap-local", havingValue = "true")
public class LocalDevBootstrapRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(LocalDevBootstrapRunner.class);
    private static final String SUPER_EMAIL = "superadmin@gmail.com";
    private static final String ADMIN_EMAIL = "maria.salazar@aulacita.demo";
    private static final String SHARED_PASSWORD = "admin1234567";

    private final JdbcTemplate jdbc;
    private final PasswordEncoder encoder;

    public LocalDevBootstrapRunner(JdbcTemplate jdbc, PasswordEncoder encoder) {
        this.jdbc = jdbc;
        this.encoder = encoder;
    }

    @Override
    public void run(ApplicationArguments args) {
        ensureSchema();
        String hash = encoder.encode(SHARED_PASSWORD);
        upsertUser(SUPER_EMAIL, "SuperAdministrador", "Super", "Administrador", hash);
        upsertUser(ADMIN_EMAIL, "Administrador", "María", "Salazar", hash);
        seedSampleDnis();
        log.warn("Bootstrap local: {} y {} quedaron activos con la contraseña de desarrollo.",
                SUPER_EMAIL, ADMIN_EMAIL);
    }

    private void ensureSchema() {
        jdbc.execute("""
                IF NOT EXISTS (SELECT 1 FROM dbo.Rol WHERE Nombre = N'SuperAdministrador')
                    INSERT INTO dbo.Rol (Nombre) VALUES (N'SuperAdministrador')
                """);
        jdbc.execute("""
                IF COL_LENGTH('dbo.Estudiante', 'Dni') IS NULL
                    ALTER TABLE dbo.Estudiante ADD Dni nvarchar(15) NULL
                """);
        jdbc.execute("""
                IF NOT EXISTS (
                    SELECT 1 FROM sys.indexes
                    WHERE name = N'UQ_Estudiante_Dni' AND object_id = OBJECT_ID(N'dbo.Estudiante'))
                    CREATE UNIQUE INDEX UQ_Estudiante_Dni ON dbo.Estudiante (Dni) WHERE Dni IS NOT NULL
                """);
        jdbc.execute("""
                IF OBJECT_ID(N'dbo.InstitucionConfig', N'U') IS NULL
                BEGIN
                    CREATE TABLE dbo.InstitucionConfig (
                        IdConfig int NOT NULL CONSTRAINT PK_InstitucionConfig PRIMARY KEY
                            CONSTRAINT CK_InstitucionConfig_Singleton CHECK (IdConfig = 1),
                        NombreInstitucion nvarchar(160) NOT NULL,
                        Slogan nvarchar(300) NULL,
                        FechaActualizacion datetime2(3) NOT NULL
                            CONSTRAINT DF_InstitucionConfig_Fecha DEFAULT SYSUTCDATETIME()
                    );
                    INSERT INTO dbo.InstitucionConfig (IdConfig, NombreInstitucion, Slogan)
                    VALUES (1, N'AulaCita', N'Familias y docentes, mejor conectados.');
                END
                """);
        jdbc.execute("""
                IF OBJECT_ID(N'dbo.InstitucionImagen', N'U') IS NULL
                BEGIN
                    CREATE TABLE dbo.InstitucionImagen (
                        IdImagen int IDENTITY(1,1) NOT NULL CONSTRAINT PK_InstitucionImagen PRIMARY KEY,
                        Clave nvarchar(40) NOT NULL,
                        NombreOriginal nvarchar(255) NOT NULL,
                        NombreAlmacenado nvarchar(255) NOT NULL,
                        ContentType nvarchar(100) NOT NULL,
                        TamanoBytes bigint NOT NULL,
                        Activo bit NOT NULL CONSTRAINT DF_InstitucionImagen_Activo DEFAULT 1,
                        ActualizadoPorIdUsuario int NULL,
                        FechaActualizacion datetime2(3) NOT NULL
                            CONSTRAINT DF_InstitucionImagen_Fecha DEFAULT SYSUTCDATETIME(),
                        CONSTRAINT UQ_InstitucionImagen_Clave UNIQUE (Clave),
                        CONSTRAINT FK_InstitucionImagen_Usuario FOREIGN KEY (ActualizadoPorIdUsuario)
                            REFERENCES dbo.Usuario(IdUsuario),
                        CONSTRAINT CK_InstitucionImagen_Clave CHECK (
                            Clave IN (N'logo', N'hero', N'galeria_1', N'galeria_2', N'galeria_3'))
                    );
                END
                """);
        jdbc.execute("""
                IF OBJECT_ID(N'dbo.BloqueoDocente', N'U') IS NULL
                BEGIN
                    CREATE TABLE dbo.BloqueoDocente (
                        IdBloqueo int IDENTITY(1,1) NOT NULL CONSTRAINT PK_BloqueoDocente PRIMARY KEY,
                        IdDocente int NOT NULL,
                        Inicio datetime2(0) NOT NULL,
                        Fin datetime2(0) NOT NULL,
                        Motivo nvarchar(300) NOT NULL,
                        RegistradoPorIdUsuario int NOT NULL,
                        FechaRegistro datetime2(3) NOT NULL
                            CONSTRAINT DF_Bloqueo_FechaRegistro DEFAULT SYSUTCDATETIME(),
                        CONSTRAINT FK_Bloqueo_Docente FOREIGN KEY (IdDocente) REFERENCES dbo.Docente(IdDocente),
                        CONSTRAINT FK_Bloqueo_Usuario FOREIGN KEY (RegistradoPorIdUsuario)
                            REFERENCES dbo.Usuario(IdUsuario),
                        CONSTRAINT CK_Bloqueo_Rango CHECK (Inicio < Fin)
                    );
                    CREATE INDEX IX_Bloqueo_DocenteRango ON dbo.BloqueoDocente (IdDocente, Inicio, Fin);
                END
                """);
        jdbc.execute("""
                IF OBJECT_ID(N'dbo.HistorialCita', N'U') IS NULL
                BEGIN
                    CREATE TABLE dbo.HistorialCita (
                        IdHistorial int IDENTITY(1,1) NOT NULL CONSTRAINT PK_HistorialCita PRIMARY KEY,
                        IdCita int NOT NULL,
                        IdUsuario int NOT NULL,
                        Evento nvarchar(40) NOT NULL,
                        EstadoAnterior nvarchar(20) NULL,
                        EstadoNuevo nvarchar(20) NULL,
                        Detalle nvarchar(1000) NULL,
                        FechaEvento datetime2(3) NOT NULL
                            CONSTRAINT DF_Historial_FechaEvento DEFAULT SYSUTCDATETIME(),
                        CONSTRAINT FK_Historial_Cita FOREIGN KEY (IdCita) REFERENCES dbo.Cita(IdCita),
                        CONSTRAINT FK_Historial_Usuario FOREIGN KEY (IdUsuario) REFERENCES dbo.Usuario(IdUsuario)
                    );
                    CREATE INDEX IX_HistorialCita_CitaFecha ON dbo.HistorialCita (IdCita, FechaEvento);
                END
                """);
        jdbc.execute("""
                IF COL_LENGTH('dbo.Cita', 'CitaOrigenIdCita') IS NULL
                    ALTER TABLE dbo.Cita ADD CitaOrigenIdCita int NULL
                """);
        jdbc.execute("""
                IF COL_LENGTH('dbo.Cita', 'CitaOrigenIdCita') IS NOT NULL
                   AND NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = N'FK_Cita_Origen')
                    ALTER TABLE dbo.Cita ADD CONSTRAINT FK_Cita_Origen
                        FOREIGN KEY (CitaOrigenIdCita) REFERENCES dbo.Cita(IdCita)
                """);
    }

    private void upsertUser(String email, String roleName, String names, String lastName, String hash) {
        Integer roleId = jdbc.queryForObject(
                "SELECT IdRol FROM dbo.Rol WHERE Nombre = ?", Integer.class, roleName);
        Integer existing = jdbc.queryForObject(
                "SELECT COUNT(*) FROM dbo.Usuario WHERE LOWER(Correo) = LOWER(?)", Integer.class, email);
        if (existing != null && existing > 0) {
            jdbc.update("""
                    UPDATE dbo.Usuario
                    SET IdRol = ?, PasswordHash = ?, Activo = 1, Nombres = ?, ApellidoPaterno = ?
                    WHERE LOWER(Correo) = LOWER(?)
                    """, roleId, hash, names, lastName, email);
            return;
        }
        jdbc.update("""
                INSERT INTO dbo.Usuario (IdRol, Nombres, ApellidoPaterno, Correo, PasswordHash, Activo)
                VALUES (?, ?, ?, ?, ?, 1)
                """, roleId, names, lastName, email, hash);
    }

    private void seedSampleDnis() {
        jdbc.update("""
                UPDATE e
                SET e.Dni = RIGHT(REPLICATE('0', 8) + CAST(10000000 + e.IdEstudiante AS varchar(12)), 8)
                FROM dbo.Estudiante e
                WHERE e.Dni IS NULL AND e.Activo = 1
                """);
    }
}
