/*
 * Ejecutar manualmente por un administrador de SQL Server en modo SQLCMD.
 * Proporcionar los valores desde un gestor seguro; nunca guardar contrasenas aquí.
 * No ejecutar contra una base con datos existentes sin revisar primero.
 */
IF DB_ID(N'$(DatabaseName)') IS NOT NULL
BEGIN
    THROW 51001, 'La base ya existe. Deténgase e inspeccione su esquema antes de provisionar.', 1;
END;
DECLARE @createDatabase nvarchar(300) =
    N'CREATE DATABASE ' + QUOTENAME(N'$(DatabaseName)') + N';';
EXEC sys.sp_executesql @createDatabase;
GO

IF NOT EXISTS (SELECT 1 FROM sys.server_principals WHERE name = N'$(RuntimeLogin)')
    CREATE LOGIN [$(RuntimeLogin)] WITH PASSWORD = N'$(RuntimePassword)', CHECK_POLICY = ON;
IF NOT EXISTS (SELECT 1 FROM sys.server_principals WHERE name = N'$(MigrationLogin)')
    CREATE LOGIN [$(MigrationLogin)] WITH PASSWORD = N'$(MigrationPassword)', CHECK_POLICY = ON;
GO

USE [$(DatabaseName)];
GO
IF USER_ID(N'$(RuntimeLogin)') IS NULL
    CREATE USER [$(RuntimeLogin)] FOR LOGIN [$(RuntimeLogin)];
IF USER_ID(N'$(MigrationLogin)') IS NULL
    CREATE USER [$(MigrationLogin)] FOR LOGIN [$(MigrationLogin)];
GO

IF NOT EXISTS (
    SELECT 1 FROM sys.database_role_members rm
    JOIN sys.database_principals r ON r.principal_id = rm.role_principal_id
    JOIN sys.database_principals m ON m.principal_id = rm.member_principal_id
    WHERE r.name = N'db_datareader' AND m.name = N'$(RuntimeLogin)')
    ALTER ROLE db_datareader ADD MEMBER [$(RuntimeLogin)];
IF NOT EXISTS (
    SELECT 1 FROM sys.database_role_members rm
    JOIN sys.database_principals r ON r.principal_id = rm.role_principal_id
    JOIN sys.database_principals m ON m.principal_id = rm.member_principal_id
    WHERE r.name = N'db_datawriter' AND m.name = N'$(RuntimeLogin)')
    ALTER ROLE db_datawriter ADD MEMBER [$(RuntimeLogin)];
GRANT EXECUTE ON SCHEMA::dbo TO [$(RuntimeLogin)];
IF NOT EXISTS (
    SELECT 1 FROM sys.database_role_members rm
    JOIN sys.database_principals r ON r.principal_id = rm.role_principal_id
    JOIN sys.database_principals m ON m.principal_id = rm.member_principal_id
    WHERE r.name = N'db_datareader' AND m.name = N'$(MigrationLogin)')
    ALTER ROLE db_datareader ADD MEMBER [$(MigrationLogin)];
IF NOT EXISTS (
    SELECT 1 FROM sys.database_role_members rm
    JOIN sys.database_principals r ON r.principal_id = rm.role_principal_id
    JOIN sys.database_principals m ON m.principal_id = rm.member_principal_id
    WHERE r.name = N'db_datawriter' AND m.name = N'$(MigrationLogin)')
    ALTER ROLE db_datawriter ADD MEMBER [$(MigrationLogin)];
IF NOT EXISTS (
    SELECT 1 FROM sys.database_role_members rm
    JOIN sys.database_principals r ON r.principal_id = rm.role_principal_id
    JOIN sys.database_principals m ON m.principal_id = rm.member_principal_id
    WHERE r.name = N'db_ddladmin' AND m.name = N'$(MigrationLogin)')
    ALTER ROLE db_ddladmin ADD MEMBER [$(MigrationLogin)];
GO
