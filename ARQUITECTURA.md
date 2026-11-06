# Arquitectura de AulaCita

## Tecnología

Spring Boot 3.5.5, Java 17, Spring MVC, Thymeleaf, Spring Security, JDBC administrado por HikariCP, Flyway y Microsoft JDBC Driver. Se reemplazó el ejemplo mínimo Servlet/JSP porque no había capas, persistencia ni flujo funcional que conservar. El empaquetado es JAR ejecutable.

## Capas

- **Presentación**: páginas Thymeleaf en `src/main/resources/templates`, CSS/JavaScript locales. La salida se escapa con el motor y los scripts crean nodos usando `textContent`.
- **HTTP**: controladores MVC y `AppointmentApiController`/`ReportController`. API bajo `/api/v1`, DTO validados y errores JSON sin SQL ni trazas.
- **Aplicación**: `AppointmentService`, `AccountService` y `DashboardService`. Autorización de propietario, reglas horarias, transacciones y cambios de estado.
- **Persistencia**: `JdbcTemplate` con parámetros, `@Transactional`, consultas explícitas y pool Hikari. No hay SQL ni reglas de negocio en las vistas.
- **Seguridad**: Spring Security con sesiones del mismo origen, BCrypt, CSRF en cookie y cabecera, cookies HttpOnly/SameSite para sesión, rotación al autenticar, rate limit de intentos por IP y autorización por rol y propiedad.

## Flujo de una cita

1. El apoderado obtiene sus relaciones habilitadas (`GET /mis-estudiantes`) y docentes activos.
2. Consulta horarios del docente, fecha y estudiante vinculado. Se usa `America/Lima`, semana ISO (lunes 1 a domingo 7) y turnos enteros según duración.
3. La reserva corre en una transacción, obtiene `sp_getapplock` por fecha, verifica propiedad/permiso, vigencia, duración, bloqueos y cruces de docente/estudiante/apoderado, inserta la cita y su primer movimiento de historial.
4. Secretaría/administración confirma; el apoderado o el personal autorizado cancela; el docente o personal autorizado registra la atención. Rowversion se representa en JSON como Base64 y se exige para cambios (`If-Match` al confirmar, campo `version` en las demás acciones).
5. Reprogramar cancela la cita anterior, crea la nueva con referencia `CitaOrigenIdCita` y registra ambos movimientos en una transacción. Si falla la nueva reserva, el rollback conserva activa la original.
6. Cierre y asistencia se escriben en una misma transacción. Los estados finales no se modifican por los endpoints implementados.

Los bloqueos usan fecha/hora local de Lima en `datetime2`; marcas de auditoría usan `SYSUTCDATETIME()`. Las disponibilidades superpuestas se rechazan. No existe edición/borrado de disponibilidades que pueda dejar citas activas fuera de horario; un bloqueo nuevo se rechaza cuando cruza una cita activa.

La aplicación serializa reservas con un application lock por fecha en SQL Server. Eso hace que la comprobación y escritura de las solicitudes de esta aplicación sean excluyentes incluso ante peticiones simultáneas. No se instaló un procedimiento almacenado sin haber podido inspeccionar una base objetivo: `AulaCitaDB_Real` no está creada en la instancia encontrada.

## Esquema

V1 crea las 13 tablas indicadas en `docs/MODELO_DATOS.md`. `CodigoCita` es calculado; `VersionFila` es `rowversion`. Las auditorías se fechan en UTC. El nuevo campo `CitaOrigenIdCita` permite rastrear reprogramaciones. Las tablas y columnas son una propuesta de instalación inicial para una base vacía, basada en el alcance solicitado; no se afirma que coincidan con una base institucional previa.

## Configuración/entornos

`application.properties` centraliza la conexión y sesiones mediante variables de entorno. El login runtime y migrador deben ser distintos. Flyway se ejecuta al inicio con el migrador separado. `scripts/01_provision_database.sql` es administrativo y no se ejecuta por la aplicación. El seed es independiente y no activa usuarios.

Las integraciones de Google Calendar/Meet y correo permanecen desactivadas y no se invocan. Los campos externos del modelo se reservan para una implementación posterior; el sistema no crea enlaces Meet ni eventos.
