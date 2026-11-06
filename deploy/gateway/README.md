# API gateway (nginx) — SM-Xplore

Punto de entrada único del backend. Un contenedor `nginx` publica un solo puerto HTTP y uno HTTPS
y enruta cada petición al microservicio dueño de la ruta. Los servicios dejan de exponerse
directamente: el cliente habla con el gateway y el gateway habla con `ms-01..ms-05` por la red
interna de Docker.

> Alcance: es un **proxy inverso**, no un servicio de negocio. No valida JWT, no emite tokens ni
> conoce el catálogo: esas reglas siguen viviendo en los servicios (que continúan validando el
> token en cada petición). El gateway aporta un contrato estable de URLs, terminación TLS y un
> único punto de observación.

---

## 1. Dónde está y qué archivos tocan qué

```
deploy/gateway/
├── Dockerfile                 # nginx:1.27-alpine + openssl; instala el entrypoint de certs
├── entrypoint.sh              # genera/valida el certificado TLS antes de arrancar nginx
├── nginx.conf                 # configuración global (http, logs, upstreams, cabeceras)
├── conf.d/
│   └── gateway.conf           # los dos servers: :80 y :443 (TLS), ambos con las mismas rutas
├── snippets/
│   └── routes.conf            # tabla de rutas ruta -> servicio (lo que más vas a editar)
└── README.md                  # este documento
```

| Archivo | Se monta en el contenedor | Para qué |
|---|---|---|
| `nginx.conf` | `/etc/nginx/nginx.conf` | `worker_processes`, logs, límites, upstreams `ms_01..ms_05`, cabeceras |
| `conf.d/` | `/etc/nginx/conf.d/` | servers `:80` y `:443`; `include` de las rutas |
| `snippets/` | `/etc/nginx/snippets/` | `routes.conf`, incluido dentro de cada server |

Todo se monta **desde el repositorio** (no se copia dentro de la imagen): para cambiar el ruteo
basta editar el archivo en el repo y reiniciar el contenedor. El único artefacto que sí vive dentro
del contenedor es el certificado autofirmado, en el volumen `gateway_certs`.

El servicio está declarado en `compose.yaml` (raíz) y en `deploy/prod/compose.yaml`.

---

## 2. Cómo funciona

1. El cliente llama `https://127.0.0.1:8443/api/v1/activities`.
2. nginx busca la `location` más específica en `snippets/routes.conf` (aquí `/api/v1/activities`)
   y hace `proxy_pass http://ms_03`, que usa el upstream `ms-03:8082` de la red `smxplore-stack`.
3. La respuesta vuelve tal cual: el gateway **no reescribe códigos, ni cuerpos, ni cabeceras de
   negocio**. Un `401` del servicio sigue siendo un `401`; un `400` con `application/problem+json`
   sigue siendo el mismo `400`.

Cabeceras que el gateway añade a toda petición reenviada:

| Cabecera | Valor | Para qué |
|---|---|---|
| `Host` | host original | que los servicios vean el host público, no el del contenedor |
| `X-Real-IP` | IP del cliente | auditoría y rate limiting por IP en los servicios |
| `X-Forwarded-For` | cadena de proxies | idem |
| `X-Forwarded-Proto` | `http` o `https` | que los servicios sepan si el cliente vino por TLS |
| `X-Forwarded-Port` | puerto original | idem |
| `X-Request-Id` | el del cliente o uno generado | correlación entre el log del gateway y los logs de los servicios |

Los logs del gateway (`docker compose logs gateway`) muestran el mismo `rid=` para una petición y
sus reintentos.

---

## 3. Arranque en local (paso a paso)

Requisitos: Docker Desktop con el stack ya construido (ver README raíz).

```powershell
# 1) Levantar el stack completo (el gateway espera a que los 5 servicios estén healthy)
docker compose up -d --build --wait --wait-timeout 900

# 2) Comprobar que el gateway está sano
docker compose ps gateway
curl.exe -s http://127.0.0.1:8088/healthz        # -> ok
curl.exe -sk https://127.0.0.1:8443/healthz      # -> ok (-k: cert autofirmado)

# 3) Probar una ruta pública real a través del gateway
curl.exe -s http://127.0.0.1:8088/api/v1/activities
curl.exe -sk https://127.0.0.1:8443/api/v1/events
```

Si añades o cambias rutas:

```powershell
docker compose exec gateway nginx -t      # valida sintaxis SIN reiniciar
docker compose restart gateway            # aplica el cambio
```

`nginx -t` es obligatorio antes de reiniciar: si la configuración está rota, nginx no arranca y el
contenedor queda reiniciándose en bucle.

### Autenticación a través del gateway

El login sigue siendo de `ms-01`:

```bash
# bash / Git Bash
curl -sk -X POST https://127.0.0.1:8443/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"admin@smxplore.local","password":"ChangeMe123!"}'

# y luego, con el accessToken en $TOKEN:
curl -sk https://127.0.0.1:8443/api/v1/recommendations \
  -H "Authorization: Bearer $TOKEN"
```

En **Windows PowerShell 5.1** el `-d` con comillas dobles se rompe al pasarlo a `curl.exe`
(PowerShell elimina las comillas). Usa Git Bash, o `Invoke-RestMethod` en PowerShell 7
(`-SkipCertificateCheck`), o guarda el JSON en un archivo y usa `curl.exe --data-binary "@body.json"`.

### TLS en desarrollo

`entrypoint.sh` genera un certificado autofirmado la primera vez y lo guarda en el volumen
`gateway_certs`. Los clientes deben usar `-k` / `--insecure` o confiar en `deploy/gateway/certs/`
si generas los certificados en el host.

Para regenerarlo (por ejemplo, si cambiaste `GATEWAY_TLS_CN`):

```powershell
docker compose down gateway
docker volume rm smxplore_gateway_certs
docker compose up -d gateway
```

---

## 4. Tabla de rutas

Regla de oro: **una ruta "bonita" solo se crea cuando el prefijo pertenece a un único servicio**.
Cuando dos servicios comparten path (p. ej. `POST /api/v1/reviews/{id}/response` existe en ms-01,
ms-03 y ms-04), no se expone por nombre corto: se usa el namespace `/svc/ms-0N/...`.

### Rutas por servicio

| Prefijo / ruta | Va a | Notas |
|---|---|---|
| `/.well-known/jwks.json` | ms-01 | firmas públicas para validar JWT |
| `/api/v1/auth/**` | ms-01 | registro, login, MFA, recuperación de contraseña |
| `/api/v1/users/**` | ms-01 | perfil, contraseña, sesiones, MFA del usuario |
| `/api/v1/public/**` | ms-01 | perfil público y resumen público de reseñas |
| `/api/v1/admin/users/**` | ms-01 | administración de cuentas |
| `/api/v1/admin/anomalies`, `/admin/anomaly-thresholds`, `/admin/audit-logs`, `/admin/monitoring` | ms-01 | observabilidad y auditoría |
| `/api/v1/admin/reviews/reported` | ms-01 | cola de reseñas reportadas |
| `/api/v1/security-reports/**` | ms-02 | reportes de seguridad y comentarios |
| `/api/v1/activities/**` | ms-03 | actividades (públicas y de gestión) |
| `/api/v1/events/**` | ms-03 | eventos y asistencia |
| `/api/v1/reservations/**` | ms-03 | reservas de actividades/eventos |
| `/api/v1/reservations/availability` (exacta) | ms-04 | disponibilidad del catálogo |
| `/api/v1/reservations/{id}/status` | ms-04 | cambio de estado de reserva de servicio |
| `/api/v1/review-reports/**` | ms-03 | bandeja de reportes de reseña |
| `/api/v1/reviews/events/**`, `/api/v1/reviews/activities/**` | ms-03 | listado y métricas de reseñas |
| `/api/v1/reviews/summary`, `/reviews/reputation` | ms-04 | reputación y resumen del catálogo |
| `/api/v1/reviews/{id}/status` | ms-04 | moderación de reseñas |
| `/api/v1/sites/**`, `/api/v1/services/**`, `/api/v1/categories/**`, `/api/v1/zones/**` | ms-04 | catálogo |
| `/api/v1/notifications/**`, `/api/v1/admin/notification-contacts/**`, `/api/v1/admin/outbox/**` | ms-04 | notificaciones y reproceso de outbox |
| `/api/v1/admin/catalog/export` | ms-04 | feed de reindexado (CATEGORIES/SITES/SERVICES) |
| `/api/v1/recommendations`, `/api/v1/preferences/**`, `/api/v1/interactions/**` | ms-05 | recomendaciones |

### Namespace `/svc/ms-0N/...`

Acceso 1:1 al resto de endpoints de cada servicio, con el prefijo recortado:

| Ruta pública | Equivale a |
|---|---|
| `/svc/ms-01/api/v1/admin/reviews/reported` | `GET ms-01:8081/api/v1/admin/reviews/reported` |
| `/svc/ms-03/api/v1/admin/catalog/export?entity=ACTIVITIES` | feed de actividades/eventos de ms-03 |
| `/svc/ms-04/api/v1/reviews/abc/response` | respuesta del gestor a una reseña en ms-04 |
| `/svc/ms-05/api/v1/recommendations` | recomendaciones (mantiene la ruta tal cual) |

Sirve para dos casos: rutas ambiguas (resuelves el servicio explícitamente) e integraciones que no
tienen un nombre corto asignado.

### Nada de esto se publica

`/actuator/**` e `/internal/**` responden **404** en el gateway, también bajo `/svc/...`. Las
métricas se consultan desde dentro de la red (`docker compose exec ms-04 curl localhost:8080/actuator/prometheus`)
o scrapeando directamente los contenedores (Prometheus de `deploy/prod`).

---

## 5. Cómo agregar una ruta nueva

1. Identifica el `@RequestMapping` del controlador en el servicio (p. ej.
   `microservices/ms-02-security-reports/.../CommentController.java` → `/api/v1/security-reports/{id}/comments`).
2. Si el prefijo no existe todavía en el gateway, añade una línea en `deploy/gateway/snippets/routes.conf`
   dentro del bloque del servicio:

   ```nginx
   location /api/v1/security-reports          { proxy_pass http://ms_02; }
   ```

   Reglas:
   - sin `/` final en `location` si quieres cubrir también la ruta exacta (`/api/v1/security-reports`);
   - `proxy_pass http://ms_02;` **sin** URI final reenvía el path tal cual; el namespace `/svc/...`
     sí termina en `/` porque debe recortar el prefijo;
   - si la ruta colisiona con otro servicio, **no** la agregues "bonita": documéntala en el namespace.
3. Valida y aplica:

   ```powershell
   docker compose exec gateway nginx -t
   docker compose restart gateway
   curl.exe -s -o NUL -w "%{http_code}" http://127.0.0.1:8088/api/v1/security-reports
   ```

4. Añade el endpoint a la tabla de este README (y, si aplica, a la tabla de puertos del README raíz).

---

## 6. Producción

El gateway también está en `deploy/prod/compose.yaml`:

- **Imagen**: `nginx:1.27-alpine` (`GATEWAY_IMAGE` en `.env.prod.example`), sin construir nada.
- **TLS**: no se genera ningún certificado. Se inyectan `gateway_tls_cert` y `gateway_tls_key`
  como secretos y se montan en `/etc/nginx/certs/tls.crt` y `tls.key`. Si faltan, nginx arranca en
  bucle de error: es intencional, nunca hay TLS "de mentira" en producción.
- **Puertos**: publica `GATEWAY_HTTP_PORT` (80) y `GATEWAY_HTTPS_PORT` (443) al host. Es el único
  contenedor pensado para escuchar fuera de `127.0.0.1`; los servicios siguen publicados solo en
  loopback.
- **Configuración**: los mismos `nginx.conf`, `conf.d/` y `snippets/` del repo. Un cambio de rutas
  es un cambio de archivo + redeploy; no requiere reconstruir imágenes de servicios.
- **Certificados reales**: `GATEWAY_TLS_CERT_FILE` debe apuntar a la cadena completa (servidor +
  intermedios) y `GATEWAY_TLS_KEY_FILE` a la clave privada. Renovación: reemplaza los archivos y
  `docker compose ... up -d gateway` (nginx relee al arrancar).

### Forzar HTTPS

Por defecto el server `:80` también enruta (útil en local para smoke tests). En producción puedes
redirigir todo a HTTPS agregando `deploy/gateway/conf.d/00-redirect.conf`:

```nginx
server {
    listen 80;
    server_name _;
    location = /healthz { access_log off; default_type text/plain; return 200 "ok\n"; }
    location / { return 308 https://$host$request_uri; }
}
```

> Ojo: ese archivo **no** debe convivir con el server `:80` de `conf.d/gateway.conf` (dos servers
> en el mismo puerto = `duplicate listen`). En producción, monta solo uno de los dos, o elimina el
> server HTTP de `gateway.conf` para ese despliegue.

### HSTS (opcional)

Cuando el dominio público ya sirve HTTPS de forma estable, añade en el server `:443`:

```nginx
add_header Strict-Transport-Security "max-age=63072000; includeSubDomains" always;
```

No lo actives antes de tener todos los subdominios en HTTPS.

---

## 7. Límites, cabeceras y comportamiento deseado

| Ajuste | Valor | Dónde | Por qué |
|---|---|---|---|
| `client_max_body_size` | 10 MB | `nginx.conf` | fotos de reportes (ms-02) y PDFs de auditoría (ms-01) |
| `proxy_read_timeout` | 60 s | `nginx.conf` | exportaciones CSV/PDF y consultas lentas |
| `proxy_connect_timeout` | 5 s | `nginx.conf` | fallar rápido si el servicio no está |
| `X-Forwarded-Proto` | `$scheme` | `nginx.conf` | los servicios generan URLs/redirects correctos |
| gzip | JSON > 512 B | `nginx.conf` | las respuestas de catálogo son grandes |
| `server_tokens` | `off` | `nginx.conf` | no revelar versión de nginx |

Cosas que el gateway **no** hace a propósito: CORS (lo resuelven los servicios con
`CORS_ALLOWED_ORIGINS`), rate limiting (lo hacen `AuthRateLimitWebFilter`,
`ReportRateLimitWebFilter` y las políticas de reservas), cacheo de respuestas ni transformación de
cuerpos. Si en el futuro se centraliza CORS aquí, hay que quitar las cabeceras equivalentes de los
servicios para no duplicarlas.

---

## 8. Verificación

```powershell
# sintaxis
docker compose exec gateway nginx -t

# salud
curl.exe -s http://127.0.0.1:8088/healthz

# JWKS a través del gateway
curl.exe -sk https://127.0.0.1:8443/.well-known/jwks.json | ConvertFrom-Json

# una ruta de cada servicio (todas deben responder algo distinto de 404/502)
curl.exe -s -o NUL -w "actividades: %{http_code}`n"  http://127.0.0.1:8088/api/v1/activities
curl.exe -s -o NUL -w "sitios:      %{http_code}`n"  http://127.0.0.1:8088/api/v1/sites
curl.exe -s -o NUL -w "seguridad:   %{http_code}`n"  http://127.0.0.1:8088/api/v1/security-reports/999999
curl.exe -s -o NUL -w "metricas:    %{http_code}`n"  http://127.0.0.1:8088/actuator/health   # debe ser 404

# flujo completo (login + negocio) por el gateway
# el backend completo se valida con el E2E del proyecto; el smoke clásico usa puertos directos.
```

`/api/v1/sites` sin token debe devolver `401` (no `502` ni `404`): eso confirma que llegó a ms-04 y
que es ms-04 quien decide sobre el token.

---

## 9. Problemas frecuentes

| Síntoma | Causa probable | Solución |
|---|---|---|
| `502 Bad Gateway` / el body dice "Servicio no disponible" | El servicio no está `healthy` o se cayó | `docker compose ps`, `docker compose logs ms-0X`; el gateway espera healthchecks al arrancar |
| `404` con "Ruta no expuesta por el gateway" | Falta la `location` en `snippets/routes.conf` | Agrega la ruta (sección 5) y reinicia el gateway |
| `404` en `/actuator/...` | Bloqueo intencional | Consulta desde dentro de la red o por Prometheus |
| `duplicate listen 80` al arrancar | Conviven dos configs que definen `:80` (p. ej. el redirect de producción) | Deja solo una de las dos |
| Aviso de certificado en el navegador | Certificado autofirmado de desarrollo | Usa `-k`/`--insecure`, o instala `deploy/gateway/certs/` como CA de confianza |
| `413 Request Entity Too Large` | Cuerpo > 10 MB | Sube `client_max_body_size` en `nginx.conf` |
| El gateway arranca en bucle en producción | Faltan `gateway_tls_cert`/`gateway_tls_key` o no son PEM válidos | Revisa `docker compose logs gateway` y los secretos |
| `401` inesperado solo a través del gateway | Falta `Authorization` o el token expira | Es el servicio quien valida: el gateway no toca el header |

---

## 10. Referencias

- `compose.yaml` (raíz): servicio `gateway`, volumen `gateway_certs`, variables `GATEWAY_*`.
- `deploy/prod/compose.yaml`: servicio `gateway` con secretos TLS reales.
- `deploy/prod/README.md`: despliegue completo, secretos y verificación de producción.
- `README.md` (raíz): arranque del stack y tabla de puertos.
- `deploy/gateway/snippets/routes.conf`: la tabla de rutas real (fuente de verdad).
