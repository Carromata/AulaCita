# Modelo de datos de instalación

El esquema de V1 es una propuesta nueva y no fue contrastado contra `AulaCitaDB_Real`: esa base no existe todavía en la instancia inspeccionada. No aplique la migración a una base poblada o con tablas de igual nombre sin inspección y migración incremental específica.

```text
Rol 1 ── * Usuario ── 0..1 Docente
                  └── 0..1 Apoderado
GradoSeccion 1 ── * Estudiante
Estudiante * ── * Apoderado (EstudianteApoderado; guarda parentesco y permiso)
Docente 1 ── * DisponibilidadDocente
Docente 1 ── * BloqueoDocente
EstudianteApoderado 1 ── * Cita * ── 1 Docente
EstadoCita 1 ── * Cita
Cita 1 ── 0..1 AsistenciaCita
Cita 1 ── * HistorialCita
Cita (anterior) 1 ── * Cita (reprogramada desde)
```

V1 crea: `Rol`, `EstadoCita`, `GradoSeccion`, `Usuario`, `Docente`, `Apoderado`, `Estudiante`, `EstudianteApoderado`, `DisponibilidadDocente`, `BloqueoDocente`, `Cita`, `AsistenciaCita`, `HistorialCita`.

- FKs protegen las relaciones; `Usuario.Correo`, parejas de rol y relaciones tienen índices/UNIQUE.
- `CodigoCita` se calcula a partir del identity; la aplicación no lo envía.
- `Usuario.VersionFila` y `Cita.VersionFila` son `rowversion`, no fechas.
- Índices de agenda cubren docente/fecha/horario y relación/fecha/horario.
- Fechas de cita y turnos son fecha/hora local de `America/Lima`; auditorías usan `datetime2` UTC.
- `CitaOrigenIdCita` conserva la trazabilidad de la cita reemplazada.
- `Activo=1` en Usuario exige una contraseña no nula y distinta de `PENDIENTE_CONFIGURAR`.
- El login ignora usuarios inactivos o sin hash BCrypt; no existen contraseñas de demostración en SQL.

La lista de campos de `Usuario` y `Cita` requerida está representada en V1. Las columnas de otras entidades son el mínimo necesario para los flujos implementados y deben cotejarse con el modelo institucional antes de usar otra base.
