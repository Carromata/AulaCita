CREATE TABLE dbo.Rol (
    IdRol int IDENTITY(1,1) NOT NULL CONSTRAINT PK_Rol PRIMARY KEY,
    Nombre nvarchar(30) NOT NULL CONSTRAINT UQ_Rol_Nombre UNIQUE
);

CREATE TABLE dbo.EstadoCita (
    IdEstadoCita int IDENTITY(1,1) NOT NULL CONSTRAINT PK_EstadoCita PRIMARY KEY,
    Nombre nvarchar(20) NOT NULL CONSTRAINT UQ_EstadoCita_Nombre UNIQUE
);

CREATE TABLE dbo.GradoSeccion (
    IdGradoSeccion int IDENTITY(1,1) NOT NULL CONSTRAINT PK_GradoSeccion PRIMARY KEY,
    Nivel nvarchar(30) NOT NULL,
    Grado nvarchar(20) NOT NULL,
    Seccion nvarchar(10) NOT NULL,
    Activo bit NOT NULL CONSTRAINT DF_GradoSeccion_Activo DEFAULT 1,
    CONSTRAINT UQ_GradoSeccion UNIQUE (Nivel, Grado, Seccion)
);

CREATE TABLE dbo.Usuario (
    IdUsuario int IDENTITY(1,1) NOT NULL CONSTRAINT PK_Usuario PRIMARY KEY,
    IdRol int NOT NULL,
    Nombres nvarchar(80) NOT NULL,
    ApellidoPaterno nvarchar(60) NOT NULL,
    ApellidoMaterno nvarchar(60) NULL,
    TipoDocumento nvarchar(20) NULL,
    NumeroDocumento nvarchar(30) NULL,
    Correo nvarchar(254) NOT NULL,
    Celular nvarchar(30) NULL,
    PasswordHash varchar(100) NULL,
    Activo bit NOT NULL CONSTRAINT DF_Usuario_Activo DEFAULT 0,
    FechaRegistro datetime2(3) NOT NULL CONSTRAINT DF_Usuario_FechaRegistro DEFAULT SYSUTCDATETIME(),
    UltimoAcceso datetime2(3) NULL,
    VersionFila rowversion NOT NULL,
    CONSTRAINT FK_Usuario_Rol FOREIGN KEY (IdRol) REFERENCES dbo.Rol(IdRol),
    CONSTRAINT UQ_Usuario_Correo UNIQUE (Correo),
    CONSTRAINT CK_Usuario_ActivoCredencial CHECK (
        Activo = 0 OR (PasswordHash IS NOT NULL AND LEN(PasswordHash) = 60 AND PasswordHash LIKE '$2%')
    )
);

CREATE TABLE dbo.Docente (
    IdDocente int IDENTITY(1,1) NOT NULL CONSTRAINT PK_Docente PRIMARY KEY,
    IdUsuario int NOT NULL,
    Activo bit NOT NULL CONSTRAINT DF_Docente_Activo DEFAULT 1,
    CONSTRAINT UQ_Docente_Usuario UNIQUE (IdUsuario),
    CONSTRAINT FK_Docente_Usuario FOREIGN KEY (IdUsuario) REFERENCES dbo.Usuario(IdUsuario)
);

CREATE TABLE dbo.Apoderado (
    IdApoderado int IDENTITY(1,1) NOT NULL CONSTRAINT PK_Apoderado PRIMARY KEY,
    IdUsuario int NOT NULL,
    Activo bit NOT NULL CONSTRAINT DF_Apoderado_Activo DEFAULT 1,
    CONSTRAINT UQ_Apoderado_Usuario UNIQUE (IdUsuario),
    CONSTRAINT FK_Apoderado_Usuario FOREIGN KEY (IdUsuario) REFERENCES dbo.Usuario(IdUsuario)
);

CREATE TABLE dbo.Estudiante (
    IdEstudiante int IDENTITY(1,1) NOT NULL CONSTRAINT PK_Estudiante PRIMARY KEY,
    IdGradoSeccion int NOT NULL,
    Nombres nvarchar(100) NOT NULL,
    ApellidoPaterno nvarchar(60) NOT NULL,
    ApellidoMaterno nvarchar(60) NULL,
    Activo bit NOT NULL CONSTRAINT DF_Estudiante_Activo DEFAULT 1,
    CONSTRAINT FK_Estudiante_GradoSeccion FOREIGN KEY (IdGradoSeccion) REFERENCES dbo.GradoSeccion(IdGradoSeccion)
);

CREATE TABLE dbo.EstudianteApoderado (
    IdEstudianteApoderado int IDENTITY(1,1) NOT NULL CONSTRAINT PK_EstudianteApoderado PRIMARY KEY,
    IdEstudiante int NOT NULL,
    IdApoderado int NOT NULL,
    Parentesco nvarchar(40) NOT NULL,
    PuedeReservar bit NOT NULL CONSTRAINT DF_EstudianteApoderado_PuedeReservar DEFAULT 0,
    Activo bit NOT NULL CONSTRAINT DF_EstudianteApoderado_Activo DEFAULT 1,
    CONSTRAINT FK_EstudianteApoderado_Estudiante FOREIGN KEY (IdEstudiante) REFERENCES dbo.Estudiante(IdEstudiante),
    CONSTRAINT FK_EstudianteApoderado_Apoderado FOREIGN KEY (IdApoderado) REFERENCES dbo.Apoderado(IdApoderado),
    CONSTRAINT UQ_EstudianteApoderado UNIQUE (IdEstudiante, IdApoderado)
);

CREATE TABLE dbo.DisponibilidadDocente (
    IdDisponibilidad int IDENTITY(1,1) NOT NULL CONSTRAINT PK_DisponibilidadDocente PRIMARY KEY,
    IdDocente int NOT NULL,
    DiaSemana tinyint NOT NULL,
    HoraInicio time(0) NOT NULL,
    HoraFin time(0) NOT NULL,
    VigenteDesde date NOT NULL,
    VigenteHasta date NULL,
    DuracionMinutos smallint NOT NULL,
    Activo bit NOT NULL CONSTRAINT DF_Disponibilidad_Activo DEFAULT 1,
    CONSTRAINT FK_Disponibilidad_Docente FOREIGN KEY (IdDocente) REFERENCES dbo.Docente(IdDocente),
    CONSTRAINT CK_Disponibilidad_Dia CHECK (DiaSemana BETWEEN 1 AND 7),
    CONSTRAINT CK_Disponibilidad_Horas CHECK (HoraInicio < HoraFin),
    CONSTRAINT CK_Disponibilidad_Duracion CHECK (DuracionMinutos BETWEEN 5 AND 240),
    CONSTRAINT CK_Disponibilidad_Vigencia CHECK (VigenteHasta IS NULL OR VigenteHasta >= VigenteDesde)
);

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

CREATE TABLE dbo.Cita (
    IdCita int IDENTITY(1,1) NOT NULL CONSTRAINT PK_Cita PRIMARY KEY,
    CodigoCita AS ('AC-' + RIGHT(REPLICATE('0', 8) + CONVERT(varchar(8), IdCita), 8)) PERSISTED,
    IdEstudianteApoderado int NOT NULL,
    IdDocente int NOT NULL,
    IdEstadoCita int NOT NULL,
    Fecha date NOT NULL,
    HoraInicio time(0) NOT NULL,
    HoraFin time(0) NOT NULL,
    Modalidad nvarchar(20) NOT NULL,
    LugarOEnlace nvarchar(500) NULL,
    Motivo nvarchar(500) NOT NULL,
    ComentarioAdicional nvarchar(1000) NULL,
    RegistradoPorIdUsuario int NOT NULL,
    FechaRegistro datetime2(3) NOT NULL CONSTRAINT DF_Cita_FechaRegistro DEFAULT SYSUTCDATETIME(),
    FechaActualizacion datetime2(3) NOT NULL CONSTRAINT DF_Cita_FechaActualizacion DEFAULT SYSUTCDATETIME(),
    FechaCancelacion datetime2(3) NULL,
    MotivoCancelacion nvarchar(500) NULL,
    CanceladoPorIdUsuario int NULL,
    GoogleCalendarEventId nvarchar(200) NULL,
    EnlaceMeet nvarchar(500) NULL,
    Activo bit NOT NULL CONSTRAINT DF_Cita_Activo DEFAULT 1,
    VersionFila rowversion NOT NULL,
    CitaOrigenIdCita int NULL,
    CONSTRAINT FK_Cita_Relacion FOREIGN KEY (IdEstudianteApoderado) REFERENCES dbo.EstudianteApoderado(IdEstudianteApoderado),
    CONSTRAINT FK_Cita_Docente FOREIGN KEY (IdDocente) REFERENCES dbo.Docente(IdDocente),
    CONSTRAINT FK_Cita_Estado FOREIGN KEY (IdEstadoCita) REFERENCES dbo.EstadoCita(IdEstadoCita),
    CONSTRAINT FK_Cita_RegistradoPor FOREIGN KEY (RegistradoPorIdUsuario) REFERENCES dbo.Usuario(IdUsuario),
    CONSTRAINT FK_Cita_CanceladoPor FOREIGN KEY (CanceladoPorIdUsuario) REFERENCES dbo.Usuario(IdUsuario),
    CONSTRAINT FK_Cita_Origen FOREIGN KEY (CitaOrigenIdCita) REFERENCES dbo.Cita(IdCita),
    CONSTRAINT CK_Cita_Horas CHECK (HoraInicio < HoraFin),
    CONSTRAINT CK_Cita_Modalidad CHECK (Modalidad IN ('PRESENCIAL', 'VIRTUAL'))
);

CREATE TABLE dbo.AsistenciaCita (
    IdAsistencia int IDENTITY(1,1) NOT NULL CONSTRAINT PK_AsistenciaCita PRIMARY KEY,
    IdCita int NOT NULL,
    Asistio bit NOT NULL,
    Observacion nvarchar(1000) NULL,
    RegistradoPorIdUsuario int NOT NULL,
    FechaRegistro datetime2(3) NOT NULL CONSTRAINT DF_Asistencia_FechaRegistro DEFAULT SYSUTCDATETIME(),
    CONSTRAINT UQ_Asistencia_Cita UNIQUE (IdCita),
    CONSTRAINT FK_Asistencia_Cita FOREIGN KEY (IdCita) REFERENCES dbo.Cita(IdCita),
    CONSTRAINT FK_Asistencia_Usuario FOREIGN KEY (RegistradoPorIdUsuario) REFERENCES dbo.Usuario(IdUsuario)
);

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

CREATE INDEX IX_EstudianteApoderado_Apoderado ON dbo.EstudianteApoderado (IdApoderado, Activo, PuedeReservar);
CREATE INDEX IX_Disponibilidad_DocenteDia ON dbo.DisponibilidadDocente (IdDocente, DiaSemana, Activo, VigenteDesde, VigenteHasta);
CREATE INDEX IX_Bloqueo_DocenteRango ON dbo.BloqueoDocente (IdDocente, Inicio, Fin);
CREATE INDEX IX_Cita_DocenteFecha ON dbo.Cita (IdDocente, Fecha, HoraInicio, HoraFin) INCLUDE (IdEstadoCita, Activo);
CREATE INDEX IX_Cita_RelacionFecha ON dbo.Cita (IdEstudianteApoderado, Fecha, HoraInicio, HoraFin) INCLUDE (IdEstadoCita, Activo);
CREATE INDEX IX_HistorialCita_CitaFecha ON dbo.HistorialCita (IdCita, FechaEvento);

INSERT INTO dbo.Rol (Nombre) VALUES
    (N'Administrador'), (N'Secretaria'), (N'Director'), (N'Docente'), (N'Apoderado');
INSERT INTO dbo.EstadoCita (Nombre) VALUES
    (N'Pendiente'), (N'Confirmada'), (N'Cancelada'), (N'Completada'), (N'No asistió');
