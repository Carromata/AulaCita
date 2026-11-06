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
