IF OBJECT_ID(N'dbo.Notificacion', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.Notificacion (
        IdNotificacion int IDENTITY(1,1) NOT NULL CONSTRAINT PK_Notificacion PRIMARY KEY,
        IdUsuario int NOT NULL,
        Titulo nvarchar(160) NOT NULL,
        Mensaje nvarchar(1000) NOT NULL,
        Tipo nvarchar(30) NOT NULL CONSTRAINT DF_Notificacion_Tipo DEFAULT N'INFO',
        IdCita int NULL,
        CreadoPorIdUsuario int NULL,
        Leida bit NOT NULL CONSTRAINT DF_Notificacion_Leida DEFAULT 0,
        FechaCreacion datetime2(3) NOT NULL CONSTRAINT DF_Notificacion_Fecha DEFAULT SYSUTCDATETIME(),
        CONSTRAINT FK_Notificacion_Usuario FOREIGN KEY (IdUsuario) REFERENCES dbo.Usuario(IdUsuario),
        CONSTRAINT FK_Notificacion_Cita FOREIGN KEY (IdCita) REFERENCES dbo.Cita(IdCita),
        CONSTRAINT FK_Notificacion_Creador FOREIGN KEY (CreadoPorIdUsuario) REFERENCES dbo.Usuario(IdUsuario)
    );
    CREATE INDEX IX_Notificacion_Usuario ON dbo.Notificacion (IdUsuario, Leida, FechaCreacion);
END;
GO

IF OBJECT_ID(N'dbo.CitaImagen', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.CitaImagen (
        IdImagen int IDENTITY(1,1) NOT NULL CONSTRAINT PK_CitaImagen PRIMARY KEY,
        IdCita int NOT NULL,
        NombreOriginal nvarchar(255) NOT NULL,
        NombreAlmacenado nvarchar(255) NOT NULL,
        ContentType nvarchar(100) NOT NULL,
        TamanoBytes bigint NOT NULL,
        SubidoPorIdUsuario int NOT NULL,
        FechaSubida datetime2(3) NOT NULL CONSTRAINT DF_CitaImagen_Fecha DEFAULT SYSUTCDATETIME(),
        Activo bit NOT NULL CONSTRAINT DF_CitaImagen_Activo DEFAULT 1,
        CONSTRAINT FK_CitaImagen_Cita FOREIGN KEY (IdCita) REFERENCES dbo.Cita(IdCita),
        CONSTRAINT FK_CitaImagen_Usuario FOREIGN KEY (SubidoPorIdUsuario) REFERENCES dbo.Usuario(IdUsuario)
    );
    CREATE INDEX IX_CitaImagen_Cita ON dbo.CitaImagen (IdCita, Activo, FechaSubida);
END;
GO
