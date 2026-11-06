# AulaCita

Sistema de gestión de citas entre apoderados y docentes. La interfaz está en español y la aplicación usa Spring Boot MVC, Thymeleaf, Spring Security, JDBC y SQL Server. Se seleccionó Spring Boot porque el proyecto original solo tenía una pantalla de ejemplo Servlet/JSP, no una arquitectura existente que conservar.

## Estado de esta entrega

La aplicación contiene login con usuarios de SQL Server, sesiones con protección CSRF, paneles con métricas consultadas a la base, consulta de citas, disponibilidad y bloqueos, reserva, confirmación, cancelación, reprogramación transaccional, asistencia, historial, perfil/cambio de contraseña, reporte por estado, API JSON y scripts de provisión y demostración.

La instancia encontrada durante la inspección es `localhost\SQLEXPRESS`. En ella existe `AulaCitaDB`, pero **no existe `AulaCitaDB_Real`**. Se dejó configurado este último como destino y no se conectó a `AulaCitaDB`, ni se creó/modificó ninguna base. La migración incluida define un esquema inicial para una base nueva vacía; no es una migración validada contra una base institucional existente. Detenga la instalación si `AulaCitaDB_Real` ya contiene tablas o datos y solicite una revisión del esquema antes de aplicar cambios.

La aplicación no usa `sa` ni requiere que el usuario de ejecución sea `db_owner`. Cree una cuenta de aplicación de solo lectura/escritura y una cuenta migradora separada. Las migraciones se ejecutan al iniciar la aplicación, con la cuenta migradora.

## Requisitos

- JDK 17 o superior (la inspección local encontró JDK 21).
- No necesita instalar Maven: use el wrapper `mvnw.cmd`.
- SQL Server 2019 o posterior con acceso TCP/IP y el controlador JDBC permitido.
- Cuenta SQL para ejecución y cuenta SQL separada para Flyway.
- Opcional: Docker y Docker Compose v2.

## 1. Configurar SQL Server

1. Confirme en SSMS la instancia, puerto, modo de autenticación y si `AulaCitaDB_Real` está ausente o vacía. No seleccione `PlantillaWeb`, `demo`, `prueba` ni bases de otros ejemplos.
2. Un administrador debe revisar y ejecutar manualmente `scripts/01_provision_database.sql` en modo SQLCMD solo después de confirmar que la base no existe. Crea `AulaCitaDB_Real` y dos logins SQL; si el nombre ya existe, el script se detiene antes de modificar nada para exigir inspección. Proporcione los valores SQLCMD desde un mecanismo seguro; no guarde contraseñas en el archivo, en Git, en la línea de comandos ni en el historial del shell.
3. El login migrador recibe permisos de lectura/escritura y `db_ddladmin` para aplicar migraciones. El login de la aplicación recibe lectura/escritura sobre el esquema y no puede crear o administrar la base.
4. Al iniciar la aplicación con `DB_MIGRATIONS_ENABLED=true`, Flyway aplica `src/main/resources/db/migration/V1__crear_esquema_aulacita.sql`. V1 solo es adecuada para una base nueva sin esquema preexistente.
5. Elija una vía para la primera cuenta: use `scripts/02_crear_administrador_inicial.sql` para crear un administrador propio deshabilitado, o ejecute `scripts/03_seed_demo.sql`, que crea `admin.demo@example.test` entre sus cuentas ficticias. No intente crear el mismo correo por ambas vías.
6. Configure la contraseña administrativa por consola como se indica abajo. Para datos ficticios, ejecute `scripts/03_seed_demo.sql` después de V1; es idempotente, no contiene contraseñas, no activa usuarios y no envía correos ni crea eventos externos.

La instancia local encontrada es SQL Server Express con nombre `SQLEXPRESS`; el puerto dinámico no se supuso. La cadena JDBC de ejemplo usa `instanceName=SQLEXPRESS` y SQL Server Browser. Si el controlador no descubre el puerto, habilite TCP/IP/asigne un puerto fijo en SQL Server Configuration Manager y use `DB_URL` con `host:puerto`. Para Docker Compose use el puerto publicado `14333` desde el equipo anfitrión y `sqlserver:1433` desde el contenedor de la aplicación.

El script administrativo de logins SQL requiere que SQL Server admita autenticación SQL (modo mixto). Si la instancia usa autenticación Windows exclusivamente, el administrador debe acordar y probar un método de credenciales compatible antes de provisionar; no convierta automáticamente la aplicación a `sa` ni a `db_owner`.

## 2. Configuración de la aplicación

Copie `.env.example` a `.env` para Docker Compose. Spring Boot ejecutado directamente no carga `.env` automáticamente: configure las variables en la configuración de ejecución de IntelliJ o en el entorno del proceso.

| Variable | Uso |
|---|---|
| `DB_URL` | URL JDBC completa. Desarrollo local de ejemplo: `jdbc:sqlserver://localhost;instanceName=SQLEXPRESS;databaseName=AulaCitaDB_Real;encrypt=true;trustServerCertificate=true;loginTimeout=5`. |
| `DB_USER`, `DB_PASSWORD` | Cuenta de ejecución, sin permisos `db_owner`. |
| `DB_MIGRATION_USER`, `DB_MIGRATION_PASSWORD` | Cuenta separada de Flyway. Por omisión Flyway usa `DB_USER`; en despliegue configure la cuenta migradora. |
| `DB_MIGRATIONS_ENABLED` | `true` aplica migraciones al inicio; `false` solo para una base que ya tenga exactamente el esquema esperado. |
| `DB_POOL_SIZE` | Máximo de conexiones HikariCP; predeterminado 10. |
| `SESSION_COOKIE_SECURE` | Use `true` detrás de HTTPS; `false` solo para HTTP local. |
| `SERVER_PORT` | Puerto HTTP; predeterminado 8080. |
| `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM` | SMTP para recuperación de contraseña. Guarde la contraseña en secretos del proveedor, nunca en Git. |
| `AULACITA_PUBLIC_URL` | URL HTTPS pública que se incluirá en enlaces de recuperación. |
| `PASSWORD_RESET_EXPIRATION_MINUTES` | Vigencia del token (5–120 minutos; predeterminado 30). |

`DB_ENCRYPT`/`DB_TRUST_SERVER_CERTIFICATE` se especifican en la URL JDBC. `trustServerCertificate=true` es solo para desarrollo local; despliegue con TLS y certificado válido.
Para despliegue active `SPRING_PROFILES_ACTIVE=prod`, configure `DB_URL` con cifrado y certificado válido, y mantenga `SESSION_COOKIE_SECURE=true` detrás de HTTPS.

Ejemplo de configuración temporal desde PowerShell; la contraseña se lee sin eco y solo queda en el entorno de este proceso y sus procesos hijos:

```powershell
$env:DB_URL = 'jdbc:sqlserver://localhost;instanceName=SQLEXPRESS;databaseName=AulaCitaDB_Real;encrypt=true;trustServerCertificate=true;loginTimeout=5'
$env:DB_USER = 'aulacita_app'
$env:DB_MIGRATION_USER = 'aulacita_migrator'
$secure = Read-Host 'Contraseña de la aplicación' -AsSecureString
$ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
try { $env:DB_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr) }
finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr); $secure.Dispose() }
$secure = Read-Host 'Contraseña del migrador' -AsSecureString
$ptr = [Runtime.InteropServices.Marshal]::SecureStringToBSTR($secure)
try { $env:DB_MIGRATION_PASSWORD = [Runtime.InteropServices.Marshal]::PtrToStringBSTR($ptr) }
finally { [Runtime.InteropServices.Marshal]::ZeroFreeBSTR($ptr); $secure.Dispose() }
```

Configura las credenciales antes de iniciar el JAR. Evita imprimir variables de entorno, poner secretos en argumentos SQLCMD o guardar contraseñas en archivos versionados.

Para pruebas con Gmail, habilite la verificación en dos pasos y cree una contraseña de aplicación; configure `MAIL_HOST=smtp.gmail.com`, `MAIL_PORT=587`, `MAIL_USERNAME` con la cuenta emisora y `MAIL_FROM` con esa misma dirección. No uses la contraseña normal de Gmail. En producción, prefiere SMTP institucional o un proveedor transaccional y guarda las credenciales como secretos.

## 3. Primera cuenta administrativa

Después de V1, cree una fila de administrador deshabilitada con `scripts/02_crear_administrador_inicial.sql` en SSMS, modo SQLCMD, indicando un correo controlado por la institución (omita este paso si usará el `admin.demo@example.test` creado por el seed). Con las variables de DB configuradas, empaquete y ejecute el JAR desde una consola interactiva:

```powershell
.\mvnw.cmd package
java -jar .\target\aulacita-1.0.0-SNAPSHOT.jar --spring.main.web-application-type=none --aulacita.setup-admin=true --aulacita.admin-email=admin@ejemplo.test
```

El proceso solicita la contraseña dos veces sin mostrarla, exige al menos 14 caracteres, genera BCrypt y habilita únicamente una fila de administrador que todavía no tenga contraseña. Si ya tiene una contraseña, no la reemplaza.

Para habilitar una cuenta ficticia en desarrollo, ejecute el JAR con `--spring.profiles.active=dev --spring.main.web-application-type=none --aulacita.setup-demo-user=true --aulacita.demo-email=docente01@example.test`. La contraseña también se solicita sin eco y solo se configura si la cuenta sigue sin hash. No utilice este modo en producción.

## 4. Compilar y ejecutar

En PowerShell, con variables SQL definidas en el perfil de ejecución:

```powershell
.\mvnw.cmd test
.\mvnw.cmd package
.\mvnw.cmd spring-boot:run
```

La aplicación escucha en `http://localhost:8080`. La página de acceso es `/login`. Los endpoints requieren sesión iniciada; no existe registro público ni selección de roles por el navegador.

## 5. Datos de demostración y pruebas

El seed crea 43 usuarios ficticios (`@example.test`), 20 docentes, 20 apoderados, 20 estudiantes, 20 grados/secciones, disponibilidades, algunos bloqueos, 30 citas, asistencias para las finalizadas e historial coherente. Las cuentas quedan inactivas hasta configurar explícitamente credenciales interactivas. Para probar un rol, configure el perfil `dev` en una cuenta de demostración correspondiente.

Pruebas automatizadas disponibles:

```powershell
.\mvnw.cmd test
```

Estas pruebas unitarias comprueban hash BCrypt, límite de intentos de login y reglas básicas de disponibilidad. No son pruebas de integración SQL Server: no se ejecutó el flujo completo de citas, login, concurrencia ni migraciones sobre `AulaCitaDB_Real`, porque la base no existe y no se autorizó crearla. Consulte `docs/GUIA_PRUEBAS.md` para completar esas verificaciones cuando la base esté provisionada.

El endpoint público `GET /health` (usado por Compose) comprueba que la aplicación y SQL respondan sin devolver configuración; `GET /api/v1/sistema/salud` ofrece la misma comprobación dentro de la API autenticada.

## 6. Docker Compose opcional

1. Copie `.env.example` como `.env`.
2. Complete una contraseña fuerte y exclusiva para el contenedor SQL de desarrollo en `MSSQL_SA_PASSWORD`; la aplicación no utiliza esa cuenta.
3. Para levantar SQL Server solamente: `docker compose up -d sqlserver`. La base del volumen es persistente.
4. Conéctese al puerto local `14333`, revise el esquema y ejecute el script administrativo. Cree la base y los logins antes de iniciar la aplicación. Configure `DB_URL_DOCKER`, `DB_USER`, `DB_PASSWORD`, `DB_MIGRATION_USER` y `DB_MIGRATION_PASSWORD`.
5. Inicie aplicación y SQL Server: `docker compose --profile app up -d --build`.
6. Consulte `docker compose ps`, `docker compose logs -f app` y `docker compose logs -f sqlserver`.
7. Detenga sin borrar datos con `docker compose stop` o `docker compose down`. **No use `docker compose down -v`** si desea conservar la base y los adjuntos subidos.

Los perfiles Compose evitan que la aplicación arranque hasta que el usuario solicite `--profile app`; aun así, la base y los logins deben prepararse antes. El contenedor SQL solicita aceptación de licencia y una contraseña propia para el administrador SQL.

## Publicación en Internet

Netlify no proporciona una base de datos SQL Server y no ejecuta directamente este servidor Spring Boot: AulaCita es una aplicación Java con backend y páginas Thymeleaf, no un sitio estático. Para una demo temporal se puede desplegar la aplicación Docker en Render y conectar una base SQL Server remota, por ejemplo una base creada en Somee. El archivo `render.yaml` prepara el servicio y solicita las credenciales como variables secretas; no contiene credenciales. Para producción, usa servicios con almacenamiento persistente, copias de seguridad y recursos suficientes.

Para la demo, crea una base nueva y vacía en SQL Server, verifica que acepte conexiones remotas desde Render y completa en Render las variables que pide el Blueprint: `DB_URL` (JDBC completa), `DB_USER`, `DB_PASSWORD`, `DB_MIGRATION_USER`, `DB_MIGRATION_PASSWORD` y `AULACITA_PUBLIC_URL` (la URL `onrender.com` asignada al servicio). Flyway aplicará las migraciones a esa base nueva al arrancar. Nunca publiques secretos en GitHub ni uses una base con datos existentes sin revisar primero las migraciones.

El plan gratuito de Render puede apagar la aplicación por inactividad y su disco local no es persistente: las imágenes subidas pueden desaparecer tras reinicios o suspensión. El SMTP de recuperación de contraseña también requiere un proveedor externo; sin configurarlo, esa función no estará disponible. Esta configuración es para presentar una demo temporal, no para datos escolares reales.

## Documentación

- `ARQUITECTURA.md`: capas, flujo y modelo de datos.
- `MANUAL_USUARIO.md`: instrucciones por rol.
- `docs/API.md` y `docs/openapi.yaml`: contrato API y OpenAPI.
- `docs/MATRIZ_PERMISOS.md`: referencia de roles.
- `docs/GUIA_PRUEBAS.md`: pruebas manuales e integración pendiente.
- `docs/PENDIENTES.md`: limitaciones conocidas y estado de integraciones.
- `ESTRUCTURA.md`: mapa de archivos.
