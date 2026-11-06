# Manual de usuario

## Ingreso y seguridad

1. Abre `http://localhost:8080/login` o la URL HTTPS que entregue la institución.
2. Usa el correo de una cuenta que haya habilitado el administrador. El registro público no está disponible.
3. Para salir, selecciona **Cerrar sesión**. AulaCita invalida la sesión del servidor.
4. En **Mi perfil** puedes actualizar tus nombres, apellidos, celular y datos del documento, además de cambiar tu contraseña indicando la actual y una nueva de al menos 12 caracteres. El correo de acceso y el rol no se modifican desde el perfil propio.

Si no puedes ingresar, usa **¿Olvidaste tu contraseña?**. La recuperación envía un enlace temporal a tu correo cuando el servicio SMTP está configurado. Si el servicio no está disponible, contacta al administrador; no envíes contraseñas por correo.

## Apoderados

1. En **Citas y agenda**, selecciona un estudiante que aparezca vinculado y habilitado para reserva.
2. Selecciona docente y fecha. Solo se listan turnos futuros, vigentes y libres para ese estudiante, docente y apoderado.
3. Escoge modalidad y escribe un motivo. La solicitud queda en estado **Pendiente**.
4. La cita aparecerá en el listado con su código y horario local de Lima.
5. La vista semanal muestra las citas reales de la semana seleccionada. Usa los controles para cambiar de semana y los filtros para encontrar una cita.
6. Descarga el archivo `.ics` de una cita para importarlo manualmente en Google Calendar, Outlook u otra aplicación compatible. No se sincroniza automáticamente.
7. Puedes cancelar o reprogramar una cita activa antes de su hora de inicio. Se solicita confirmación. La reprogramación crea una reserva vinculada a la anterior y, si falla, conserva la original.
8. El historial muestra los cambios registrados.

No podrás consultar estudiantes de otra familia. Si falta un estudiante o el sistema indica que no tienes permiso para reservar, secretaría debe revisar la relación y el permiso `PuedeReservar`.

## Docentes

- En **Citas y agenda** se listan únicamente las citas asignadas a tu cuenta.
- En **Citas y agenda** puedes registrar, editar, activar y desactivar tu disponibilidad. Un horario no se puede modificar ni desactivar si afecta una cita activa futura.
- Puedes registrar bloqueos mediante la API documentada. Un bloqueo se rechaza si cruza una cita activa; su edición y retiro todavía no están disponibles en la interfaz.
- Después del horario de una cita confirmada, registra si el apoderado asistió. Esa operación cierra la cita y registra el historial en conjunto.
- No puedes ver la agenda de otro docente ni cambiar el estado de citas ajenas.

## Secretaría

- El panel muestra datos actuales de SQL Server.
- Puede consultar la agenda, confirmar solicitudes pendientes, cancelar o reprogramar reservas futuras y registrar asistencia según el proceso institucional.
- Puede agregar disponibilidades y bloqueos por docente habilitado, además de mantener estudiantes y relaciones familiares desde **Gestión escolar**.
- Los reportes agregados se consultan en **Reportes** seleccionando un rango de fechas.

## Administración

- Comparte las operaciones de secretaría y puede revisar reportes.
- En **Gestión escolar** puede crear, editar, activar, desactivar y restablecer las cuentas de docentes y apoderados, y actualizar sus datos personales y de contacto. No puede administrar cuentas administrativas.
- También puede mantener estudiantes y relaciones familiares. No hay registro público: las cuentas las crea personal autorizado.

## Superadministración

- Puede administrar cuentas de administración, secretaría y dirección, además de docentes y apoderados; las cuentas de superadministrador no se gestionan desde el listado.
- Puede administrar imágenes institucionales y enviar avisos generales.
- Puede editar su propio perfil en **Mi perfil**; desde **Gestión escolar** puede mantener los datos de las cuentas bajo su administración.

## Dirección

- Tiene acceso al reporte agregado por estado y período. No puede confirmar, reservar, cancelar ni modificar agendas.

## Estados y fechas

- `Pendiente`: solicitud recibida.
- `Confirmada`: reunión aceptada.
- `Completada`: reunión cerrada con asistencia.
- `No asistió`: reunión cerrada sin asistencia.
- `Cancelada`: reunión anulada; el horario se libera.

La agenda usa `America/Lima`. Fechas API se envían como `AAAA-MM-DD`; horas como `HH:mm:ss` o `HH:mm`. En los datos de auditoría se almacena UTC.
