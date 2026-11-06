# Guía de pruebas

## Pruebas automáticas disponibles

```powershell
.\mvnw.cmd test
```

Incluye pruebas unitarias de hash BCrypt, rate limiting de login, y disponibilidad para fecha pasada/sin ventanas. No requieren una base de datos.

## Preparación de las pruebas integrales

Estas pruebas están pendientes hasta que una persona autorizada cree y prepare `AulaCitaDB_Real`. No deben apuntarse a `AulaCitaDB` u otra base de ejemplos.

1. Provisione SQL Server y cuentas de ejecución/migración con `scripts/01_provision_database.sql`.
2. Configure variables de entorno sin guardarlas en Git.
3. Inicie la aplicación y deje que Flyway cree el esquema vacío.
4. Ejecute `scripts/03_seed_demo.sql` en modo demostración y configure usuarios por consola.
5. Verifique `GET /api/v1/sistema/salud`, login por rol y cierre de sesión.

## Casos manuales imprescindibles

| Caso | Resultado esperado |
|---|---|
| Login válido, contraseña incorrecta y usuario inactivo | Solo el usuario activo con BCrypt válido inicia sesión; el mensaje de login no enumera cuentas. |
| Repetir 11 contraseñas incorrectas desde una IP | Se responde 429 temporal; un login correcto limpia el contador local. |
| Apoderado consulta `/mis-estudiantes` | Solo aparecen sus relaciones; un `relacionId` de otra familia recibe 403. |
| Reservar fecha pasada, docente deshabilitado, fuera de disponibilidad o turno que no cabe | Error 400/404/409; no se inserta cita. |
| Reservar dentro de bloqueo o con cruce de docente, estudiante o apoderado | 409; no se persiste una segunda reserva. |
| Dos peticiones simultáneas al mismo docente/fecha/hora | Solo una gana el application lock y crea cita. |
| Disponibilidades con rangos superpuestos | Se rechazan y la agenda no ofrece turnos ambiguos. |
| Confirmar una cita pendiente con `If-Match` actual | Pasa a Confirmada y agrega historial. |
| Confirmar con `rowversion` anterior o una cita finalizada | 409, sin cambio parcial. |
| Cancelar una cita futura | Pasa a Cancelada, conserva el motivo y libera el horario. |
| Reprogramar a horario libre | En una transacción cancela la cita anterior, crea una nueva con `CitaOrigenIdCita` y escribe historial de ambas. |
| Reprogramar a horario no libre | 409 y rollback; la cita anterior sigue activa. |
| Registrar asistencia de una confirmada ya terminada | Estado, `AsistenciaCita` e historial se guardan juntos. |
| Registrar asistencia antes del fin o de nuevo | 409; estado y asistencia no se duplican. |
| Dos usuarios actualizan la misma versión | El segundo recibe 409 por `VersionFila`. |
| Director intenta abrir agenda o confirmar cita | 403; solo puede consultar reportes agregados. |
| Reporte con rango válido | Conteos provienen de SQL Server; rango invertido o mayor a 367 días se rechaza. |
| CSRF ausente en un `POST` | Spring Security lo rechaza; repetir con cookie y cabecera XSRF válidas permite procesar. |

Las comprobaciones de simultaneidad, transacciones, FK/índices y rowversion deben ejecutarse con SQL Server, no con H2. Para E2E se requiere además navegador y servicio SQL; no se afirma que se hayan ejecutado en esta entrega.

## Diagnóstico de conexión

- Confirme instancia y puerto con SQL Server Configuration Manager/SSMS. No infiera el puerto de `SQLEXPRESS`.
- Compare `DB_URL` con la instancia/base de SSMS. La conexión elegida para esta entrega es `AulaCitaDB_Real`, que aún no existe.
- Compruebe permisos de `DB_MIGRATION_USER` y `DB_USER` por separado.
- Ejecute `SELECT DB_NAME(), @@SERVERNAME;` de forma administrativa para comprobar el destino, sin copiar secretos a logs.
- Ya iniciada la app, `GET /api/v1/sistema/salud` responde solo `connected` o un error genérico.
