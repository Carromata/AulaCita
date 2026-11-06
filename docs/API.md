# API HTTP AulaCita

Contrato bajo `/api/v1`, documentado también en [`openapi.yaml`](openapi.yaml). Las respuestas exitosas usan `{ "data": ... }`; los errores usan `{ "status", "message", "details" }`. No se devuelven SQL, trazas ni hashes. Las fechas son `AAAA-MM-DD`, horas locales de Lima y los filtros de informe están limitados a un año.

## Sesión y CSRF

La autenticación usa el formulario del mismo origen `GET /login` y `POST /login` de Spring Security (`username` con correo y `password`). La sesión viaja en cookie `JSESSIONID`; no hay JWT ni registro público. Las operaciones `POST` requieren cookie `XSRF-TOKEN` y cabecera `X-XSRF-TOKEN`, que entrega la página del mismo origen. En un login HTTP de desarrollo, la cookie `Secure` está desactivada; despliegue HTTPS debe establecer `SESSION_COOKIE_SECURE=true`.

## Recursos implementados

| Método y ruta | Acceso | Descripción |
|---|---|---|
| `GET /auth/me` | sesión | Correo y rol(es) del usuario actual. |
| `GET /auth/perfil` | sesión | Datos personales y de contacto propios. |
| `PUT /auth/perfil` | sesión + CSRF | Actualiza nombres, celular y documento propios; no cambia correo ni rol. |
| `POST /auth/recuperar-contrasena` | público + CSRF | Envía un enlace de un solo uso si SMTP está configurado; respuesta no revela si el correo existe. |
| `POST /auth/restablecer-contrasena` | público + CSRF | Cambia la contraseña usando un token de recuperación válido y no expirado. |
| `GET /health` | público | Health check genérico; devuelve `ok` si el proceso y SQL responden. |
| `POST /auth/cambiar-contrasena` | sesión + CSRF | Verifica contraseña actual y actualiza BCrypt. |
| `GET /sistema/salud` | sesión | Comprueba `SELECT 1`, sin mostrar configuración. |
| `GET /mis-estudiantes` | apoderado | Relaciones propias con permiso de reserva. |
| `GET /docentes` | sesión | Docentes habilitados. |
| `GET /disponibilidades?docenteId=&fecha=&relacionId=` | roles de agenda | Turnos disponibles. Para apoderado `relacionId` es obligatorio y se verifica pertenencia. |
| `GET /disponibilidades/mias` | docente/secretaría/admin | Disponibilidades propias o lista administrativa acotada. |
| `POST /disponibilidades` | docente/secretaría/admin + CSRF | Agrega un rango vigente; rechaza superposiciones. |
| `PUT /disponibilidades/{id}` | docente dueño/secretaría/admin + CSRF | Modifica o desactiva un horario; rechaza cambios que dejen citas activas futuras fuera de disponibilidad. |
| `GET /bloqueos?desde=&hasta=` | docente/secretaría/admin | Lista bloqueos; docente solo consulta los propios. |
| `POST /bloqueos` | docente/secretaría/admin + CSRF | Agrega bloqueo futuro que no cruce citas activas. |
| `GET /citas?pagina=0&limite=20` | apoderado/docente/secretaría/admin | Citas propias o agenda de gestión, límite máximo 100 por página. |
| `POST /citas` | apoderado/secretaría/admin + CSRF | Reserva un horario de una relación habilitada. |
| `POST /citas/{id}/confirmar` | secretaría/admin + CSRF | Confirma pendiente; requiere cabecera `If-Match` con `version` Base64 de la respuesta. |
| `POST /citas/{id}/cancelar` | propietario/secretaría/admin + CSRF | Cancela cita activa futura y libera el horario. |
| `POST /citas/{id}/reprogramar` | propietario/secretaría/admin + CSRF | Cancela la anterior y crea una nueva en una transacción. |
| `POST /citas/{id}/asistencia` | docente dueño/secretaría/admin + CSRF | Cierra una confirmada terminada y registra asistencia e historial. |
| `GET /citas/{id}/historial` | propietario/docente dueño/secretaría/admin | Movimientos de auditoría. |
| `GET /reportes/resumen?desde=&hasta=` | dirección/secretaría/admin | Conteo real por estado; máximo 367 días inclusivos. |
| `GET /admin/usuarios` | superadmin/admin | Lista cuentas que puede administrar cada rol. |
| `POST /admin/usuarios` | superadmin/admin + CSRF | Crea una cuenta según la matriz de permisos; el administrador solo puede crear docentes y apoderados. |
| `PUT /admin/usuarios/{id}` | superadmin/admin + CSRF | Actualiza datos personales, rol permitido y estado de una cuenta administrable. |
| `POST /admin/usuarios/{id}/password` | superadmin/admin + CSRF | Restablece la contraseña de una cuenta administrable. |

La matriz completa está en [`MATRIZ_PERMISOS.md`](MATRIZ_PERMISOS.md). La gestión de usuarios, estudiantes y relaciones familiares está disponible solo para los roles autorizados.

La recuperación de contraseña requiere aplicar Flyway V4 y configurar SMTP (`MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_FROM`) y `AULACITA_PUBLIC_URL`. Sin esa configuración la API devuelve `503`; no informa éxito de un correo que no salió. Los tokens se almacenan con SHA-256, expiran y son de un solo uso.

## Ejemplos

Consulta de horarios (fecha ficticia):

```http
GET /api/v1/disponibilidades?docenteId=4&fecha=2030-04-15&relacionId=18
Cookie: JSESSIONID=<sesion>; XSRF-TOKEN=<csrf>
Accept: application/json
```

Respuesta:

```json
{
  "data": [
    {"date":"2030-04-15","startTime":"09:00:00","endTime":"09:30:00"}
  ]
}
```

Reserva:

```http
POST /api/v1/citas
Cookie: JSESSIONID=<sesion>; XSRF-TOKEN=<csrf>
X-XSRF-TOKEN: <csrf>
Content-Type: application/json
```

```json
{
  "studentGuardianRelationId": 18,
  "teacherId": 4,
  "date": "2030-04-15",
  "startTime": "09:00",
  "modality": "PRESENCIAL",
  "reason": "Conversar sobre avances académicos",
  "comment": ""
}
```

Respuesta resumida:

```json
{
  "data": {
    "id": 31,
    "code": "AC-00000031",
    "studentGuardianRelationId": 18,
    "teacherId": 4,
    "date": "2030-04-15",
    "startTime": "09:00:00",
    "endTime": "09:30:00",
    "status": "Pendiente",
    "version": "AAAAAAA..."
  }
}
```

El código de ejemplo no corresponde a una cita real. `version` representa los 8 bytes de `rowversion` en Base64; conservar el valor exacto al actualizar.

Cancelación:

```json
{"reason":"Solicito cambiar la fecha","version":"AAAAAAA..."}
```

Reprogramación:

```json
{
  "version":"AAAAAAA...",
  "replacement":{
    "studentGuardianRelationId":18,
    "teacherId":4,
    "date":"2030-04-16",
    "startTime":"10:00",
    "modality":"VIRTUAL",
    "reason":"Conversar sobre avances académicos",
    "comment":""
  }
}
```

Asistencia:

```json
{"attended":true,"observation":"Se revisaron los acuerdos","version":"AAAAAAA..."}
```

## Errores

- `400`: formato o validación incorrectos.
- `401`: sesión requerida o contraseña actual incorrecta.
- `403`: rol o propiedad insuficiente.
- `404`: recurso inexistente.
- `409`: cruce, horario ocupado o edición concurrente (`rowversion` cambió).
- `500`: error interno; el detalle SQL se conserva solo en registros del servidor.
