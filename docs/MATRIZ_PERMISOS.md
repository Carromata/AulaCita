# Matriz de permisos

La autorización se valida en Spring Security y, para las operaciones sobre recursos propios, también en el servicio. El usuario autenticado se obtiene de la sesión; no se confía en un identificador de actor enviado por el navegador.

| Operación | Superadministrador | Administrador | Secretaría | Dirección | Docente | Apoderado |
|---|---:|---:|---:|---:|---:|---:|
| Consultar citas | Todas | Todas | Todas | No | Solo su agenda | Solo sus relaciones |
| Reservar cita | Para relaciones habilitadas | Para relaciones habilitadas | Para relaciones habilitadas | No | Para relaciones habilitadas | Para relación propia habilitada |
| Confirmar cita pendiente | Sí | Sí | Sí | No | Cita propia | No |
| Cancelar o reprogramar cita futura activa | Sí | Sí | Sí | No | Cita propia | Cita propia |
| Registrar asistencia | Sí | Sí | Sí | No | Cita propia | No |
| Adjuntar/ver imágenes de cita | Citas accesibles | Citas accesibles | Citas accesibles | No | Cita propia | Cita propia |
| Gestionar estudiantes y relaciones familiares | Sí | Sí | Sí | No | No | No |
| Gestionar cuentas de administrador | Sí | No | No | No | No | No |
| Gestionar cuentas docentes y apoderados | Sí | Sí | No | No | No | No |
| Gestionar cuentas de secretaría y dirección | Sí | No | No | No | No | No |
| Editar datos de perfil propio | Sí | Sí | Sí | Sí | Sí | Sí |
| Editar datos de perfil de otros usuarios | Sí | Docentes y apoderados | No | No | No | No |
| Avisos generales con modal | Sí | No | No | No | No | No |
| Imágenes institucionales | Sí | No | No | No | No | No |
| Reportes agregados | Sí | Sí | Sí | Sí | No | No |
| Cambiar contraseña propia | Sí | Sí | Sí | Sí | Sí | Sí |

El superadministrador puede administrar las cuentas de los demás roles, excepto otras cuentas de superadministrador. El administrador solo puede crear, editar, activar, desactivar y restablecer las cuentas de docentes y apoderados; la API aplica la misma restricción aunque se invoque directamente. Ambos pueden editar desde Gestión los datos personales y de contacto de las cuentas permitidas. Cada persona puede actualizar sus nombres y datos de contacto desde Mi perfil; el correo de acceso y el rol no son editables desde el perfil propio. Secretaría puede mantener estudiantes y relaciones, pero no gestionar usuarios. Dirección solo tiene acceso a los reportes agregados y su propio perfil.

Las imágenes de cita están limitadas a JPG, PNG, WEBP o GIF y 3 MB por archivo. El acceso a los adjuntos sigue la misma autorización que la cita.
