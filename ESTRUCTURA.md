# Estructura del proyecto

```text
src/main/java/pe/edu/aulacita/
  AulaCitaApplication.java
  appointments/      API, reglas de citas, DTO, errores y transacciones
  config/            configuración de Spring Security
  dashboard/         métricas reales por rol
  reports/           resumen agregado de citas
  security/          autenticación SQL, sesiones, contraseñas y límites
  web/               páginas Thymeleaf
src/main/resources/
  application.properties
  db/migration/      migraciones Flyway
  static/css/        diseño local
  static/js/         flujo de citas, perfil e informes
  templates/         vistas en español
src/test/java/       pruebas unitarias
scripts/             provisión DBA, primera cuenta y seed demostrativo
docs/                OpenAPI, modelo, permisos y guía de pruebas
```

## Puntos de entrada

- `AulaCitaApplication`: arranque.
- `SecurityConfig`: sesión, autenticación, roles, CSRF y cookies.
- `AppointmentService`: agenda, reserva, estados, historial y asistencia.
- `AppointmentApiController`: API bajo `/api/v1`.
- `PageController`: páginas públicas y paneles.
- `V1__crear_esquema_aulacita.sql`: esquema para una base nueva vacía.
