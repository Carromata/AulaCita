/*
 * Datos ficticios e idempotentes para AulaCitaDB_Real.
 * No asigna hashes ni activa usuarios. No envía correo ni crea eventos externos.
 * Aplicar primero las migraciones y ejecutar solo en un entorno de demostración.
 */
SET NOCOUNT ON;
SET XACT_ABORT ON;
BEGIN TRANSACTION;

DECLARE @i int = 1;
DECLARE @roleTeacher int = (SELECT IdRol FROM dbo.Rol WHERE Nombre = N'Docente');
DECLARE @roleGuardian int = (SELECT IdRol FROM dbo.Rol WHERE Nombre = N'Apoderado');
DECLARE @roleAdmin int = (SELECT IdRol FROM dbo.Rol WHERE Nombre = N'Administrador');
DECLARE @roleSecretary int = (SELECT IdRol FROM dbo.Rol WHERE Nombre = N'Secretaria');
DECLARE @roleDirector int = (SELECT IdRol FROM dbo.Rol WHERE Nombre = N'Director');
DECLARE @today date = CONVERT(date, DATEADD(hour, -5, SYSUTCDATETIME()));
DECLARE @section nchar(1);
DECLARE @grade nvarchar(20);
DECLARE @level nvarchar(30);
DECLARE @email nvarchar(254);
DECLARE @isNew bit;

IF NOT EXISTS (SELECT 1 FROM dbo.Usuario WHERE Correo = N'admin.demo@example.test')
    INSERT INTO dbo.Usuario (IdRol, Nombres, ApellidoPaterno, Correo)
    VALUES (@roleAdmin, N'Cuenta', N'Demostración', N'admin.demo@example.test');
IF NOT EXISTS (SELECT 1 FROM dbo.Usuario WHERE Correo = N'secretaria.demo@example.test')
    INSERT INTO dbo.Usuario (IdRol, Nombres, ApellidoPaterno, Correo)
    VALUES (@roleSecretary, N'Cuenta', N'Secretaría', N'secretaria.demo@example.test');
IF NOT EXISTS (SELECT 1 FROM dbo.Usuario WHERE Correo = N'director.demo@example.test')
    INSERT INTO dbo.Usuario (IdRol, Nombres, ApellidoPaterno, Correo)
    VALUES (@roleDirector, N'Cuenta', N'Dirección', N'director.demo@example.test');

WHILE @i <= 20
BEGIN
    SET @email = CONCAT(N'docente', RIGHT(CONCAT(N'00', @i), 2), N'@example.test');
    IF NOT EXISTS (SELECT 1 FROM dbo.Usuario WHERE Correo = @email)
        INSERT INTO dbo.Usuario (IdRol, Nombres, ApellidoPaterno, Correo)
        VALUES (@roleTeacher, CONCAT(N'Docente ', @i), N'Demostración', @email);

    SET @email = CONCAT(N'familia', RIGHT(CONCAT(N'00', @i), 2), N'@example.test');
    IF NOT EXISTS (SELECT 1 FROM dbo.Usuario WHERE Correo = @email)
        INSERT INTO dbo.Usuario (IdRol, Nombres, ApellidoPaterno, Correo)
        VALUES (@roleGuardian, CONCAT(N'Apoderado ', @i), N'Demostración', @email);

    SET @level = CASE WHEN @i <= 12 THEN N'Primaria' ELSE N'Secundaria' END;
    SET @grade = CONVERT(nvarchar(20), ((@i - 1) % 6) + 1);
    SET @section = NCHAR(65 + ((@i - 1) / 6));
    IF NOT EXISTS (SELECT 1 FROM dbo.GradoSeccion WHERE Nivel = @level AND Grado = @grade AND Seccion = @section)
        INSERT INTO dbo.GradoSeccion (Nivel, Grado, Seccion) VALUES (@level, @grade, @section);

    IF NOT EXISTS (
        SELECT 1 FROM dbo.Estudiante e
        JOIN dbo.GradoSeccion g ON g.IdGradoSeccion = e.IdGradoSeccion
        WHERE e.Nombres = CONCAT(N'Estudiante ', @i) AND e.ApellidoPaterno = N'Demostración'
          AND g.Nivel = @level AND g.Grado = @grade AND g.Seccion = @section)
        INSERT INTO dbo.Estudiante (IdGradoSeccion, Nombres, ApellidoPaterno)
        VALUES ((SELECT IdGradoSeccion FROM dbo.GradoSeccion WHERE Nivel = @level AND Grado = @grade AND Seccion = @section),
                CONCAT(N'Estudiante ', @i), N'Demostración');
    SET @i += 1;
END;

SET @i = 1;
WHILE @i <= 20
BEGIN
    DECLARE @teacherEmail nvarchar(254) = CONCAT(N'docente', RIGHT(CONCAT(N'00', @i), 2), N'@example.test');
    DECLARE @guardianEmail nvarchar(254) = CONCAT(N'familia', RIGHT(CONCAT(N'00', @i), 2), N'@example.test');
    DECLARE @teacherId int = (SELECT d.IdDocente FROM dbo.Docente d JOIN dbo.Usuario u ON u.IdUsuario = d.IdUsuario WHERE u.Correo = @teacherEmail);
    DECLARE @guardianId int = (SELECT a.IdApoderado FROM dbo.Apoderado a JOIN dbo.Usuario u ON u.IdUsuario = a.IdUsuario WHERE u.Correo = @guardianEmail);
    DECLARE @studentId int = (
        SELECT e.IdEstudiante FROM dbo.Estudiante e
        WHERE e.Nombres = CONCAT(N'Estudiante ', @i) AND e.ApellidoPaterno = N'Demostración');

    IF @teacherId IS NULL
    BEGIN
        INSERT INTO dbo.Docente (IdUsuario)
        SELECT IdUsuario FROM dbo.Usuario WHERE Correo = @teacherEmail;
        SET @teacherId = SCOPE_IDENTITY();
    END;
    IF @guardianId IS NULL
    BEGIN
        INSERT INTO dbo.Apoderado (IdUsuario)
        SELECT IdUsuario FROM dbo.Usuario WHERE Correo = @guardianEmail;
        SET @guardianId = SCOPE_IDENTITY();
    END;

    IF NOT EXISTS (SELECT 1 FROM dbo.EstudianteApoderado WHERE IdEstudiante = @studentId AND IdApoderado = @guardianId)
        INSERT INTO dbo.EstudianteApoderado (IdEstudiante, IdApoderado, Parentesco, PuedeReservar)
        VALUES (@studentId, @guardianId, N'Apoderado/a', 1);

    IF NOT EXISTS (SELECT 1 FROM dbo.DisponibilidadDocente WHERE IdDocente = @teacherId AND DiaSemana = ((@i - 1) % 7) + 1 AND HoraInicio = '08:00')
        INSERT INTO dbo.DisponibilidadDocente
            (IdDocente, DiaSemana, HoraInicio, HoraFin, VigenteDesde, DuracionMinutos)
        VALUES (@teacherId, ((@i - 1) % 7) + 1, '08:00', '12:00', @today, 30);
    SET @i += 1;
END;

SET @i = 1;
WHILE @i <= 3
BEGIN
    DECLARE @blockedTeacher int = (SELECT IdDocente FROM dbo.Docente ORDER BY IdDocente OFFSET (@i - 1) ROWS FETCH NEXT 1 ROW ONLY);
    IF NOT EXISTS (
        SELECT 1 FROM dbo.BloqueoDocente
        WHERE IdDocente = @blockedTeacher AND Inicio = DATEADD(hour, 17, CONVERT(datetime2(0), DATEADD(day, @i, @today))))
        INSERT INTO dbo.BloqueoDocente (IdDocente, Inicio, Fin, Motivo, RegistradoPorIdUsuario)
        VALUES (@blockedTeacher, DATEADD(hour, 17, CONVERT(datetime2(0), DATEADD(day, @i, @today))),
                DATEADD(hour, 18, CONVERT(datetime2(0), DATEADD(day, @i, @today))),
                N'Bloqueo ficticio de demostración',
                (SELECT TOP (1) IdUsuario FROM dbo.Usuario WHERE Correo = N'secretaria.demo@example.test'));
    SET @i += 1;
END;

SET @i = 1;
WHILE @i <= 30
BEGIN
    DECLARE @person int = ((@i - 1) % 20) + 1;
    DECLARE @demoTeacher int = (
        SELECT d.IdDocente FROM dbo.Docente d JOIN dbo.Usuario u ON u.IdUsuario = d.IdUsuario
        WHERE u.Correo = CONCAT(N'docente', RIGHT(CONCAT(N'00', @person), 2), N'@example.test'));
    DECLARE @demoGuardian int = (
        SELECT a.IdApoderado FROM dbo.Apoderado a JOIN dbo.Usuario u ON u.IdUsuario = a.IdUsuario
        WHERE u.Correo = CONCAT(N'familia', RIGHT(CONCAT(N'00', @person), 2), N'@example.test'));
    DECLARE @relationId int = (
        SELECT ea.IdEstudianteApoderado FROM dbo.EstudianteApoderado ea
        WHERE ea.IdApoderado = @demoGuardian
          AND ea.IdEstudiante = (SELECT TOP (1) IdEstudiante FROM dbo.Estudiante WHERE Nombres = CONCAT(N'Estudiante ', @person) AND ApellidoPaterno = N'Demostración'));
    DECLARE @offset int = CASE WHEN @i <= 20 THEN -250 + (@i * 7) ELSE 21 + ((@i - 21) * 7) END;
    DECLARE @appointmentDate date = DATEADD(day, @offset, @today);
    DECLARE @targetDay int = ((@person - 1) % 7) + 1;
    DECLARE @currentDay int = ((DATEDIFF(day, CONVERT(date, '19000101'), @appointmentDate) % 7) + 1);
    IF @i <= 20
        SET @appointmentDate = DATEADD(day, -((@currentDay - @targetDay + 7) % 7), @appointmentDate);
    ELSE
        SET @appointmentDate = DATEADD(day, (@targetDay - @currentDay + 7) % 7, @appointmentDate);

    DECLARE @startTime time(0) = DATEADD(minute, ((@i - 1) % 8) * 30, CONVERT(time(0), '08:00'));
    DECLARE @stateName nvarchar(20) = CASE
        WHEN @i <= 20 AND @i % 2 = 0 THEN N'No asistió'
        WHEN @i <= 20 THEN N'Completada'
        WHEN @i <= 25 THEN N'Confirmada'
        ELSE N'Pendiente' END;
    DECLARE @stateId int = (SELECT IdEstadoCita FROM dbo.EstadoCita WHERE Nombre = @stateName);
    DECLARE @appointmentId int;
    SET @appointmentId = NULL;
    SET @isNew = 0;

    SELECT @appointmentId = IdCita FROM dbo.Cita
    WHERE IdEstudianteApoderado = @relationId AND IdDocente = @demoTeacher
      AND Fecha = @appointmentDate AND HoraInicio = @startTime AND CitaOrigenIdCita IS NULL;
    IF @appointmentId IS NULL
    BEGIN
        INSERT INTO dbo.Cita
            (IdEstudianteApoderado, IdDocente, IdEstadoCita, Fecha, HoraInicio, HoraFin, Modalidad,
             Motivo, RegistradoPorIdUsuario)
        VALUES (@relationId, @demoTeacher, @stateId, @appointmentDate, @startTime,
                DATEADD(minute, 30, @startTime), CASE WHEN @i % 2 = 0 THEN N'PRESENCIAL' ELSE N'VIRTUAL' END,
                N'Seguimiento académico (dato ficticio)', (SELECT IdUsuario FROM dbo.Usuario WHERE Correo = N'secretaria.demo@example.test'));
        SET @appointmentId = SCOPE_IDENTITY();
        SET @isNew = 1;
    END;

    IF @isNew = 1 AND NOT EXISTS (SELECT 1 FROM dbo.HistorialCita WHERE IdCita = @appointmentId AND Evento = N'RESERVADA')
        INSERT INTO dbo.HistorialCita (IdCita, IdUsuario, Evento, EstadoNuevo, Detalle)
        VALUES (@appointmentId, (SELECT IdUsuario FROM dbo.Usuario WHERE Correo = N'secretaria.demo@example.test'),
                N'RESERVADA', N'Pendiente', N'Reserva ficticia de demostración');

    IF @isNew = 1 AND @stateName IN (N'Confirmada', N'Completada', N'No asistió')
    BEGIN
        IF NOT EXISTS (SELECT 1 FROM dbo.HistorialCita WHERE IdCita = @appointmentId AND Evento = N'CONFIRMADA')
            INSERT INTO dbo.HistorialCita (IdCita, IdUsuario, Evento, EstadoAnterior, EstadoNuevo)
            VALUES (@appointmentId, (SELECT IdUsuario FROM dbo.Usuario WHERE Correo = N'secretaria.demo@example.test'),
                    N'CONFIRMADA', N'Pendiente', N'Confirmada');
    END;
    IF @isNew = 1 AND @stateName IN (N'Completada', N'No asistió')
    BEGIN
        IF NOT EXISTS (SELECT 1 FROM dbo.AsistenciaCita WHERE IdCita = @appointmentId)
            INSERT INTO dbo.AsistenciaCita (IdCita, Asistio, Observacion, RegistradoPorIdUsuario)
            VALUES (@appointmentId, CASE WHEN @stateName = N'Completada' THEN 1 ELSE 0 END,
                    N'Registro ficticio de demostración',
                    (SELECT IdUsuario FROM dbo.Usuario WHERE Correo = CONCAT(N'docente', RIGHT(CONCAT(N'00', @person), 2), N'@example.test')));
        IF NOT EXISTS (SELECT 1 FROM dbo.HistorialCita WHERE IdCita = @appointmentId AND Evento = N'ATENDIDA')
            INSERT INTO dbo.HistorialCita (IdCita, IdUsuario, Evento, EstadoAnterior, EstadoNuevo, Detalle)
            VALUES (@appointmentId, (SELECT IdUsuario FROM dbo.Usuario WHERE Correo = CONCAT(N'docente', RIGHT(CONCAT(N'00', @person), 2), N'@example.test')),
                    N'ATENDIDA', N'Confirmada', @stateName, N'Cierre ficticio de demostración');
    END;
    SET @i += 1;
END;

COMMIT TRANSACTION;
