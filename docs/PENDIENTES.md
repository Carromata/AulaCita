# Pendientes y límites conocidos

## Bloqueo de entorno

- La instancia SQL Server local es `localhost\SQLEXPRESS`; se encontró `AulaCitaDB`, pero no `AulaCitaDB_Real`. No se mostró conexión SQL configurada en el entorno. El usuario seleccionó preparar `AulaCitaDB_Real` pero no crearla sin autorización adicional.
- Por ello no se ejecutaron V1 ni el seed, no se inspeccionó el esquema de otra base, no se probó autenticación contra datos reales y no se validaron transacciones/concurrencia en SQL Server.
- `V1__crear_esquema_aulacita.sql` es un esquema inicial para una base nueva vacía, no una migración incremental de una base ya instalada.

## Capacidades ya implementadas

- Perfiles diferenciados con reglas aplicadas en servidor: solo el superadministrador administra cuentas de administrador; el administrador gestiona docentes y apoderados; secretaría mantiene estudiantes y relaciones familiares.
- Avisos institucionales enviados por el superadministrador, con notificación modal a las cuentas destinatarias.
- Adjuntos de imágenes en las citas, con control de acceso por participantes/rol y límites de tipo y tamaño.
- Recuperación de contraseña por correo con token aleatorio de un solo uso, hash almacenado, expiración y respuesta que no revela si el correo existe. Requiere aplicar V4 y configurar SMTP.
- Vista semanal de citas reales y descarga `.ics` para importar eventos manualmente a un calendario externo.

## Funciones aún no implementadas

- La gestión escolar permite que el superadministrador cree y gestione administradores, docentes y apoderados, y que el administrador cree y gestione docentes y apoderados. Ambos pueden editar los datos de contacto de las cuentas permitidas; todos los roles pueden editar sus propios datos de perfil y cambiar su contraseña. La creación y edición de grados/secciones todavía no tiene pantalla/API.
- Edición/desactivación segura de disponibilidad está disponible. Los bloqueos todavía se registran y consultan, pero no se pueden editar o retirar desde la interfaz.
- La reprogramación está implementada en el servicio/API de forma atómica; el flujo de reprogramación de la interfaz todavía usa diálogos y no está integrado directamente en la vista semanal.
- Paginación API básica de citas; faltan filtros por varios campos, ordenamiento configurable y paginación visual.
- Gestión de lugares/enlaces de reunión.
- Google Calendar/Meet: no hay sincronización automática ni se crean enlaces Meet; la descarga `.ics` permite importación manual.
- Recordatorios y notificaciones por correo: no están configurados; solo recuperación de contraseña está preparada para SMTP.
- Configurar SMTP en el entorno real y aplicar V4 antes de habilitar recuperación de contraseña.
- Historial administrativo de cambios de cuentas y auditoría detallada de modificaciones de disponibilidad.
- Pruebas de integración SQL Server para transiciones, familias, concurrencia, `rowversion`, recuperación, migraciones y restricciones. Las pruebas unitarias actuales no sustituyen estas verificaciones.
- Pruebas de navegador/responsive con automatización E2E.

No se afirma que el criterio de terminado integral esté satisfecho. Antes de uso real deben crearse/provisionarse la base correcta, revisar el esquema con el responsable de SQL Server, aplicar migraciones con aprobación y ejecutar las pruebas de integración descritas.
