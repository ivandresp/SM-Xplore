# Política de retención de datos y procedimiento ARCO

SM-Xplore trata datos personales de turistas, guías, prestadoras de servicios, gestoras de destinos y
administradores. Este documento describe qué datos se conservan, por cuánto tiempo, cómo se eliminan y cómo se atienden
las solicitudes de **Acceso, Rectificación, Cancelación y Oposición (ARCO)**.

Base legal: Ley 1581 de 2012, Decreto 1377 de 2013 y demás normas colombianas sobre protección de datos personales.

## 1. Datos tratados y finalidad

| Dato | Almacenamiento | Finalidad | Retención |
| --- | --- | --- | --- |
| Nombre, correo, teléfono, idioma, nacionalidad, fecha de nacimiento | PostgreSQL `users` | Identificar y contactar a la persona, prestar el servicio | Mientras la cuenta esté activa; al darse de baja se anonimiza |
| Documento de identidad (tipo y número) | PostgreSQL `users` | Verificar identidad de guías y prestadoras | Mientras la cuenta esté activa; se anonimiza al darse de baja |
| Hash de contraseña (BCrypt) | PostgreSQL `users` | Autenticación | Mientras la cuenta esté activa |
| Secreto TOTP cifrado y códigos de recuperación hasheados | PostgreSQL `mfa_enrollments`, `mfa_recovery_codes` | Segundo factor de autenticación | Mientras el MFA esté activo; los códigos usados se purgan a los 30 días |
| Tokens de restablecimiento de contraseña e invitación (hash) | PostgreSQL `password_reset_tokens` | Recuperar el acceso y activar cuentas internas | 30 días |
| Intentos fallidos de inicio de sesión | PostgreSQL `login_attempts` | Bloqueo por fuerza bruta | 30 días |
| Auditoría de acciones administrativas | PostgreSQL `audit_logs` | Trazabilidad y control interno | 5 años (configurable) |
| Reseñas sobre guías y prestadoras | MongoDB `smxplore_reviews.reviews` | Reputación del destino y del prestador | Mientras el contenido sea publicado; se conserva sin identidad tras la baja del autor |
| Anomalías y umbrales | PostgreSQL `anomalies`, `anomaly_thresholds` | Monitoreo operativo | 5 años (configurable) |
| Datos de contacto para correo | Mailpit en desarrollo; proveedor SMTP en producción | Enviar enlaces de recuperación e invitaciones | No se almacenan en la base de datos |

## 2. Reglas de retención implementadas

La aplicación ejecuta una pasada diaria de limpieza (`RetentionScheduler`, 03:30 por defecto) que aplica:

- **Artefactos de autenticación** (`RETENTION_AUTH_ARTIFACTS_DAYS`, 30 días por defecto):
  - Tokens de restablecimiento creados antes del corte.
  - Contadores de intentos fallidos cuyo último fallo es anterior al corte.
  - Códigos de recuperación MFA ya usados antes del corte.
- **Auditoría** (`RETENTION_AUDIT_LOG_DAYS`, 1825 días = 5 años por defecto): entradas con `occurred_at` anterior al
  corte.

Variables: `RETENTION_ENABLED`, `RETENTION_AUTH_ARTIFACTS_DAYS`, `RETENTION_AUDIT_LOG_DAYS` y `RETENTION_CRON`.
La pasada se registra en el log con las filas eliminadas y nunca detiene el servicio si falla; se reintenta al día
siguiente.

La baja de una cuenta (`DELETE /api/v1/admin/users/{id}`) se implementa con **anonimización**, no con borrado físico:
se reemplazan nombre, correo, teléfono y documento por valores anónimos, se invalida la sesión (`auth_version`) y se
conservan las reseñas y la auditoría sin asociarlas a una persona identificable.

## 3. Procedimiento ARCO

Canal oficial: `privacidad@smxplore.local` (buzón a configurar con el dominio real antes de operar).

1. **Radicación.** La persona envía la solicitud indicando derecho invocado, datos de contacto y documento de
   identidad. Se registra en la bitácora de solicitudes con número de radicado.
2. **Verificación de identidad.** Se confirma que el solicitante es el titular (o su representante con poder) mediante
   comparación con los datos registrados. Sin verificación no se entrega información.
3. **Respuesta.** Consultas: máximo 15 días hábiles. Reclamos: máximo 15 días hábiles, prorrogables 8 días hábiles
   informando el motivo.
4. **Ejecución según el derecho:**

| Derecho | Acción técnica | Responsable |
| --- | --- | --- |
| Acceso | `GET /api/v1/users/me` para el titular; el administrador entrega el perfil y el historial de auditoría que le concierna | Soporte + Administrador |
| Rectificación | `PATCH /api/v1/users/me`; correcciones de rol o estado las aplica el administrador | Titular / Administrador |
| Cancelación | Baja con anonimización (`DELETE /api/v1/admin/users/{id}`) y revocación de sesiones | Administrador |
| Oposición | Se evalúa el caso; se suspende el tratamiento no esencial (por ejemplo, comunicaciones) y se documenta la decisión | Administrador + Responsable de privacidad |

5. **Cierre.** Se deja constancia en la auditoría (`CUENTA_ELIMINADA`, `ESTADO_CAMBIADO`) y se comunica al titular.

## 4. Medidas de seguridad

- Autenticación con contraseña BCrypt (costo 12), bloqueo por intentos y MFA TOTP con códigos de recuperación hasheados.
- Secretos TOTP cifrados con AES-256-GCM; tokens de restablecimiento guardados como hash.
- JWT RS256 con validación de emisor, audiencia, expiración y versión de autenticación; el cierre de sesión revoca los
  tokens vigentes de la cuenta.
- Control de acceso por rol (ADMIN) y auditoría de toda acción administrativa.
- En producción: TLS obligatorio en el proxy, HSTS activo, Swagger deshabilitado y validaciones de arranque que impiden
  usar llaves o contraseñas de desarrollo.

## 5. Roles y responsabilidades

- **Responsable de privacidad:** mantiene esta política, atiende las solicitudes ARCO y reporta incidentes.
- **Administrador del sistema:** ejecuta bajas, anonimizaciones y consultas de auditoría.
- **Equipo de desarrollo:** mantiene las reglas de retención y verifica que los cambios no amplíen los datos tratados.

## 6. Vigencia y revisión

Esta política rige desde la puesta en producción del servicio y se revisa al menos una vez al año o cuando cambie la
normativa aplicable. Los plazos de retención pueden ajustarse por variables de entorno sin cambiar el código.
