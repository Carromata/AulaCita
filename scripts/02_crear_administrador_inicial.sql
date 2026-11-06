/*
 * Ejecutar una sola vez por SQLCMD/SSMS después de aplicar V1.
 * Crea una cuenta deshabilitada y sin contraseña. Luego configúrela
 * mediante AdminCredentialSetupRunner desde una consola segura.
 */
USE [$(DatabaseName)];
GO
DECLARE @Email nvarchar(254) = N'$(AdminEmail)';
IF EXISTS (SELECT 1 FROM dbo.Usuario WHERE Correo = @Email)
    THROW 51000, 'El correo ya existe; no se modificó ninguna cuenta.', 1;

INSERT INTO dbo.Usuario
    (IdRol, Nombres, ApellidoPaterno, ApellidoMaterno, Correo, PasswordHash, Activo)
VALUES
    ((SELECT IdRol FROM dbo.Rol WHERE Nombre = N'Administrador'),
     N'Administrador', N'Inicial', NULL, @Email, NULL, 0);
GO
