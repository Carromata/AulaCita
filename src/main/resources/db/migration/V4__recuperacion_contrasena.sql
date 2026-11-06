IF OBJECT_ID(N'dbo.TokenRestablecimientoContrasena', N'U') IS NULL
BEGIN
    CREATE TABLE dbo.TokenRestablecimientoContrasena (
        IdToken bigint IDENTITY(1,1) NOT NULL
            CONSTRAINT PK_TokenRestablecimientoContrasena PRIMARY KEY,
        IdUsuario int NOT NULL,
        HashToken char(64) NOT NULL,
        ExpiraEn datetime2(3) NOT NULL,
        Usado bit NOT NULL CONSTRAINT DF_TokenRestablecimiento_Usado DEFAULT 0,
        FechaCreacion datetime2(3) NOT NULL
            CONSTRAINT DF_TokenRestablecimiento_Fecha DEFAULT SYSUTCDATETIME(),
        CONSTRAINT FK_TokenRestablecimiento_Usuario FOREIGN KEY (IdUsuario)
            REFERENCES dbo.Usuario(IdUsuario),
        CONSTRAINT UQ_TokenRestablecimiento_Hash UNIQUE (HashToken)
    );
    CREATE INDEX IX_TokenRestablecimiento_Usuario
        ON dbo.TokenRestablecimientoContrasena (IdUsuario, Usado, ExpiraEn);
END;
