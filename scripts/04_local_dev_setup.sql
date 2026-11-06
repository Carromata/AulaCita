IF NOT EXISTS (SELECT 1 FROM sys.server_principals WHERE name = N'aulacita_app')
    CREATE LOGIN [aulacita_app] WITH PASSWORD = N'6eoVqk9SjMX-t5d6oUC5QJBzQ!@cmEv5AzmngXAt_mqd', CHECK_POLICY = OFF;
GO
USE [AulaCitaDB];
GO
IF USER_ID(N'aulacita_app') IS NULL
    CREATE USER [aulacita_app] FOR LOGIN [aulacita_app];
GO
ALTER ROLE db_datareader ADD MEMBER [aulacita_app];
ALTER ROLE db_datawriter ADD MEMBER [aulacita_app];
ALTER ROLE db_ddladmin ADD MEMBER [aulacita_app];
GRANT EXECUTE ON SCHEMA::dbo TO [aulacita_app];
GO

IF NOT EXISTS (SELECT 1 FROM dbo.Rol WHERE Nombre = N'SuperAdministrador')
    INSERT INTO dbo.Rol (Nombre) VALUES (N'SuperAdministrador');
GO

IF COL_LENGTH('dbo.Estudiante', 'Dni') IS NULL
    ALTER TABLE dbo.Estudiante ADD Dni nvarchar(15) NULL;
GO

SET QUOTED_IDENTIFIER ON;
IF NOT EXISTS (
    SELECT 1 FROM sys.indexes
    WHERE name = N'UQ_Estudiante_Dni' AND object_id = OBJECT_ID(N'dbo.Estudiante'))
    CREATE UNIQUE INDEX UQ_Estudiante_Dni ON dbo.Estudiante (Dni) WHERE Dni IS NOT NULL;
GO


IF OBJECT_ID(N'dbo.InstitucionConfig', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.InstitucionConfig (
        IdConfig int NOT NULL CONSTRAINT PK_InstitucionConfig PRIMARY KEY
            CONSTRAINT CK_InstitucionConfig_Singleton CHECK (IdConfig = 1),
        NombreInstitucion nvarchar(160) NOT NULL,
        Slogan nvarchar(300) NULL,
        FechaActualizacion datetime2(3) NOT NULL CONSTRAINT DF_InstitucionConfig_Fecha DEFAULT SYSUTCDATETIME()
    );
    INSERT INTO dbo.InstitucionConfig (IdConfig, NombreInstitucion, Slogan)
    VALUES (1, N'AulaCita', N'Familias y docentes, mejor conectados.');
END;
GO

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
        FechaActualizacion datetime2(3) NOT NULL CONSTRAINT DF_InstitucionImagen_Fecha DEFAULT SYSUTCDATETIME(),
        CONSTRAINT UQ_InstitucionImagen_Clave UNIQUE (Clave),
        CONSTRAINT FK_InstitucionImagen_Usuario FOREIGN KEY (ActualizadoPorIdUsuario)
            REFERENCES dbo.Usuario(IdUsuario),
        CONSTRAINT CK_InstitucionImagen_Clave CHECK (Clave IN (N'logo', N'hero', N'galeria_1', N'galeria_2', N'galeria_3'))
    );
END;
GO

IF OBJECT_ID(N'dbo.BloqueoDocente', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.BloqueoDocente (
        IdBloqueo int IDENTITY(1,1) NOT NULL CONSTRAINT PK_BloqueoDocente PRIMARY KEY,
        IdDocente int NOT NULL,
        Inicio datetime2(0) NOT NULL,
        Fin datetime2(0) NOT NULL,
        Motivo nvarchar(300) NOT NULL,
        RegistradoPorIdUsuario int NOT NULL,
        FechaRegistro datetime2(3) NOT NULL CONSTRAINT DF_Bloqueo_FechaRegistro DEFAULT SYSUTCDATETIME(),
        CONSTRAINT FK_Bloqueo_Docente FOREIGN KEY (IdDocente) REFERENCES dbo.Docente(IdDocente),
        CONSTRAINT FK_Bloqueo_Usuario FOREIGN KEY (RegistradoPorIdUsuario) REFERENCES dbo.Usuario(IdUsuario),
        CONSTRAINT CK_Bloqueo_Rango CHECK (Inicio < Fin)
    );
    CREATE INDEX IX_Bloqueo_DocenteRango ON dbo.BloqueoDocente (IdDocente, Inicio, Fin);
END;
GO

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
        FechaEvento datetime2(3) NOT NULL CONSTRAINT DF_Historial_FechaEvento DEFAULT SYSUTCDATETIME(),
        CONSTRAINT FK_Historial_Cita FOREIGN KEY (IdCita) REFERENCES dbo.Cita(IdCita),
        CONSTRAINT FK_Historial_Usuario FOREIGN KEY (IdUsuario) REFERENCES dbo.Usuario(IdUsuario)
    );
    CREATE INDEX IX_HistorialCita_CitaFecha ON dbo.HistorialCita (IdCita, FechaEvento);
END;
GO

IF COL_LENGTH('dbo.Cita', 'CitaOrigenIdCita') IS NULL
    ALTER TABLE dbo.Cita ADD CitaOrigenIdCita int NULL;
GO

IF COL_LENGTH('dbo.Cita', 'CitaOrigenIdCita') IS NOT NULL
   AND NOT EXISTS (SELECT 1 FROM sys.foreign_keys WHERE name = N'FK_Cita_Origen')
    ALTER TABLE dbo.Cita ADD CONSTRAINT FK_Cita_Origen
        FOREIGN KEY (CitaOrigenIdCita) REFERENCES dbo.Cita(IdCita);
GO

DECLARE @Hash varchar(100) = '$2a$12$nj8FfqUStRqqZXxNS7CANO6.qAitttfsRvB31MsPH7R8QNbFPkPEW';
DECLARE @RoleSuper int = (SELECT IdRol FROM dbo.Rol WHERE Nombre = N'SuperAdministrador');
DECLARE @RoleAdmin int = (SELECT IdRol FROM dbo.Rol WHERE Nombre = N'Administrador');

IF EXISTS (SELECT 1 FROM dbo.Usuario WHERE LOWER(Correo) = LOWER(N'superadmin@gmail.com'))
    UPDATE dbo.Usuario
    SET IdRol = @RoleSuper, PasswordHash = @Hash, Activo = 1, Nombres = N'Super', ApellidoPaterno = N'Administrador'
    WHERE LOWER(Correo) = LOWER(N'superadmin@gmail.com');
ELSE
    INSERT INTO dbo.Usuario (IdRol, Nombres, ApellidoPaterno, Correo, PasswordHash, Activo)
    VALUES (@RoleSuper, N'Super', N'Administrador', N'superadmin@gmail.com', @Hash, 1);

IF EXISTS (SELECT 1 FROM dbo.Usuario WHERE LOWER(Correo) = LOWER(N'maria.salazar@aulacita.demo'))
    UPDATE dbo.Usuario
    SET IdRol = @RoleAdmin, PasswordHash = @Hash, Activo = 1
    WHERE LOWER(Correo) = LOWER(N'maria.salazar@aulacita.demo');
ELSE
    INSERT INTO dbo.Usuario (IdRol, Nombres, ApellidoPaterno, Correo, PasswordHash, Activo)
    VALUES (@RoleAdmin, N'María', N'Salazar', N'maria.salazar@aulacita.demo', @Hash, 1);

UPDATE e
SET e.Dni = RIGHT(REPLICATE('0', 8) + CAST(10000000 + e.IdEstudiante AS varchar(12)), 8)
FROM dbo.Estudiante e
WHERE e.Dni IS NULL AND e.Activo = 1;
GO
