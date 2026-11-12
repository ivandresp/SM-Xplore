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
├── Dockerfile                 # nginxinc/nginx-unprivileged:1.27-alpine + openssl (PLAT-12)
├── entrypoint.sh              # genera/valida el certificado TLS antes de arrancar nginx
├── nginx.conf                 # configuración global (http, logs, upstreams, cabeceras, límites)
├── conf.d/
│   └── gateway.conf           # servers 8080 y 8443 de DESARROLLO (ambos con las mismas rutas)
├── prod/
│   └── gateway.conf           # servers de PRODUCCIÓN: 8080 solo redirige a HTTPS (PLAT-03)
├── snippets/
│   ├── routes.conf            # tabla de rutas ruta -> servicio (lo que más vas a editar)
│   └── security-headers.conf  # cabeceras de seguridad comunes (PLAT-16)
└── README.md                  # este documento
```

| Archivo | Se monta en el contenedor | Para qué |
|---|---|---|
| `nginx.conf` | `/etc/nginx/nginx.conf` | `worker_processes`, logs, tiempos, límites de conexión, zonas de rate limiting, upstreams `ms_01..ms_05`, cabeceras |
| `conf.d/gateway.conf` | `/etc/nginx/conf.d/gateway.conf` (dev) | servers `8080` y `8443`; `include` de las rutas y de las cabeceras de seguridad |
| `prod/gateway.conf` | `/etc/nginx/conf.d/default.conf` (prod) | `8443` con rutas y HSTS; `8080` solo `/healthz` + redirect 308 |
| `snippets/` | `/etc/nginx/snippets/` | `routes.conf` y `security-headers.conf`, incluidos dentro de cada server |

`deploy/prod/compose.yaml` monta `prod/gateway.conf` **sobre `/etc/nginx/conf.d/default.conf`**: la
imagen de producción (`GATEWAY_IMAGE`, por defecto `nginxinc/nginx-unprivileged:1.27-alpine`) no se
construye desde el Dockerfile del repo, así que si no se reemplaza, su `default.conf` quedaría como
virtual host por defecto y serviría su página de bienvenida. En producción la API nunca se sirve por
HTTP (ver sección 9).

Todo se monta **desde el repositorio** (no se copia dentro de la imagen): para cambiar el ruteo
basta editar el archivo en el repo y reiniciar el contenedor. El certificado autofirmado de
desarrollo se escribe en un `tmpfs` (`/etc/nginx/certs:mode=1777`), no en un volumen: el contenedor
corre como `nginx` y nunca necesita escribir en el sistema de archivos de la imagen.

El servicio está declarado en `compose.yaml` (raíz) y en `deploy/prod/compose.yaml`.

---

## 2. Cómo funciona

1. El cliente llama `https://127.0.0.1:8443/api/v1/activities`.
2. nginx busca la `location` más específica en `snippets/routes.conf` (aquí `/api/v1/activities`)
   y hace `proxy_pass http://ms_03`, que usa el upstream `ms-03:8082` de la red `smxplore-stack`.
3. La respuesta vuelve tal cual: el gateway **no reescribe códigos, ni cuerpos, ni cabeceras de
   negocio**. Un `401` del servicio sigue siendo un `401`; un `400` con `application/problem+json`
   sigue siendo el mismo `400`.
4. Antes de reenviar, nginx aplica **rate limiting por IP** (sección 7). Si el cliente supera el
   cubo de su zona, responde `429` con `application/problem+json` y `Retry-After` sin llegar al
   servicio.

Cabeceras que el gateway añade a toda petición reenviada:

| Cabecera | Valor | Para qué |
|---|---|---|
| `Host` | host original | que los servicios vean el host público, no el del contenedor |
| `X-Real-IP` | IP del cliente | auditoría y rate limiting por IP en los servicios |
| `X-Forwarded-For` | **IP del cliente (sobrescrita)** | idem; se reemplaza el valor recibido, no se concatena (ver nota) |
| `X-Forwarded-Proto` | `http` o `https` | que los servicios sepan si el cliente vino por TLS |
| `X-Forwarded-Port` | puerto original | idem |
| `X-Request-Id` | el del cliente o uno generado | correlación entre el log del gateway y los logs de los servicios |

> **`X-Forwarded-For` se sobrescribe con `$remote_addr`** (no se usa `$proxy_add_x_forwarded_for`).
> Si el cliente envía un `X-Forwarded-For` propio, se descarta. Así un atacante no puede rotar la
> cabecera para caer en otro cubo de rate limit (MS01-01) ni falsear la IP en los logs. Los servicios
> deben leer `X-Real-IP` (o la IP TCP de la conexión), nunca confiar en la cadena de XFF recibida.

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

`entrypoint.sh` genera un certificado autofirmado al arrancar y lo deja en un `tmpfs` (`/etc/nginx/certs`),
de modo que el contenedor no necesita escribir en el sistema de archivos de la imagen ni corre como
root (PLAT-12). El certificado se regenera en cada arranque del contenedor; los clientes deben usar
`-k` / `--insecure` o confiar en `deploy/gateway/certs/` si generas los certificados en el host.

Para regenerarlo (por ejemplo, si cambiaste `GATEWAY_TLS_CN`), basta con recrear el contenedor:

```powershell
docker compose up -d --force-recreate gateway
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

Por defecto el server `:80` de **desarrollo** también enruta la API (útil en local para smoke tests).
En **producción** ya está resuelto: `deploy/prod/compose.yaml` monta `deploy/gateway/prod/gateway.conf`,
que sirve la API solo por `:443` y en `:80` únicamente responde `/healthz` y redirige con `308` al
mismo host en HTTPS (PLAT-03). No hace falta ningún archivo extra:

```nginx
# fragmento de prod/gateway.conf
server {
    listen 80;
    location = /healthz { access_log off; default_type text/plain; return 200 "ok\n"; }
    location / { return 308 https://$host$request_uri; }
}
```

Si en algún momento necesitas el mismo redirect en local, copia `prod/gateway.conf` sobre
`conf.d/gateway.conf`; **no** montes los dos a la vez (dos servers en `:80` = `duplicate listen`).

### HSTS

`prod/gateway.conf` ya envía `Strict-Transport-Security "max-age=31536000; includeSubDomains"` en el
server `:443`. Si el dominio público aún no tiene todos los subdominios en HTTPS, quita esa línea de
`prod/gateway.conf` hasta que los tenga: un HSTS prematuro deja esos subdominios inaccesibles por HTTP
durante un año.

---

## 7. Límites, cabeceras y comportamiento deseado

| Ajuste | Valor | Dónde | Por qué |
|---|---|---|---|
| `client_max_body_size` | 10 MB | `nginx.conf` | fotos de reportes (ms-02) y PDFs de auditoría (ms-01) |
| `proxy_read_timeout` | 60 s | `nginx.conf` | exportaciones CSV/PDF y consultas lentas |
| `proxy_connect_timeout` | 5 s | `nginx.conf` | fallar rápido si el servicio no está |
| `X-Forwarded-Proto` | `$scheme` | `nginx.conf` | los servicios generan URLs/redirects correctos |
| `X-Forwarded-For` | `$remote_addr` (sobrescrita) | `nginx.conf` | el rate limit del borde y los logs usan la IP real, no una cabecera falseable |
| gzip | JSON > 512 B | `nginx.conf` | las respuestas de catálogo son grandes |
| `server_tokens` | `off` | `nginx.conf` | no revelar versión de nginx |
| rate limit `auth` | 30 r/m, burst 10 `nodelay` | `nginx.conf` + `routes.conf` | frenar fuerza bruta de login/MFA sin encolar |
| rate limit `public` | 120 r/m, burst 40 `nodelay` | `nginx.conf` + `routes.conf` | catálogo público y recomendaciones: absorber picos, cortar ráfagas abusivas |
| rate limit `api` | 600 r/m, burst 100 `nodelay` | `nginx.conf` + `routes.conf` | resto de la superficie publicada |
| `limit_req_status` | `429` | `nginx.conf` | el exceso se rechaza en formato `problem+json` (con `Retry-After`) |

### Rate limiting por IP (MS01-01)

El gateway limita **en el borde** con `limit_req` + `limit_req_zone`. La clave es
`$binary_remote_addr`: la IP TCP de la conexión tal como la ve nginx, que el cliente **no puede
falsear** (a diferencia de `X-Forwarded-For`, que el gateway además sobrescribe).

| Zona | Rate | Burst | Dónde se aplica |
|---|---|---|---|
| `auth` | 30 r/m (1 cada 2 s) | 10, `nodelay` | `location /api/v1/auth/` **y** `location /svc/ms-01/api/v1/auth/` → ms-01 (login, MFA, recuperación; el namespace no rebaja el límite) |
| `public` | 120 r/m (2 por s) | 40, `nodelay` | GET de catálogo público: `/api/v1/activities`, `/events`, `/reviews/*`, `/sites`, `/services`, `/categories`, `/zones`, `/recommendations` |
| `api` | 600 r/m (10 por s) | 100, `nodelay` | resto de rutas publicadas (usuarios, admin, reservas, preferencias, interacciones, namespace `/svc/...`) |

- **Cómo se comporta un exceso:** nginx responde `429` con cuerpo
  `{"type":"about:blank","title":"Demasiadas peticiones",...}` y `Retry-After: 1`. La petición **no
  llega al servicio** (no consume cupo de sus filtros internos).
- **Las tres zonas usan `nodelay`:** la ráfaga se atiende de inmediato y, cuando se agota el
  `burst`, el exceso responde `429` en el acto. Es clave para páginas que disparan varias consultas
  en paralelo: sin `nodelay` nginx las **encolaba** a `1/rate` por petición (con `public` a
  120 r/m eran ~500 ms cada una) y el catálogo tardaba segundos en cargar aunque los servicios
  respondieran en milisegundos.
- **Cubo compartido por IP:** si muchos clientes salen por la misma IP (NAT, proxy corporativo,
  runner de CI), comparten el cubo. Curl/Postman desde la misma máquina también. Si una suite E2E
  dispara >10 logins seguidos en frío o >40 lecturas de catálogo en un segundo, verá `429`: espacia
  las peticiones o sube el `burst` (ver siguiente punto), no bajes el rate sin justificarlo.
- **Cómo ajustar:** el **rate** de cada zona se cambia en `nginx.conf`
  (`limit_req_zone ... rate=30r/m`; admite `r/s` y `r/m`); el **burst** (y `nodelay`) se cambia en
  la `location` correspondiente de `snippets/routes.conf`. Tras editar: `nginx -t` y
  `docker compose restart gateway`. Las zonas son independientes: subir `public` no afecta a `auth`.
- **Memoria:** `10m` por zona ≈ 160.000 IPs tracked; suficiente para el despliegue actual. Si se
  agranda, recuerda que es memoria compartida por worker.
- **Qué NO limita el gateway:** sigue siendo responsabilidad de cada servicio su propia política de
  negocio (por ejemplo, intentos de login por cuenta, cuotas por usuario, capacidad de reservas).
  El borde protege contra volumen por IP, no sustituye esos límites.

Cosas que el gateway **no** hace a propósito: CORS (lo resuelven los servicios con
`CORS_ALLOWED_ORIGINS`), rate limiting por **usuario/cuenta** (solo por IP; los límites de negocio
siguen en los servicios: `AuthRateLimitWebFilter`, `ReportRateLimitWebFilter` y las políticas de
reservas), cacheo de respuestas ni transformación de cuerpos. Si en el futuro se centraliza CORS
aquí, hay que quitar las cabeceras equivalentes de los servicios para no duplicarlas.

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

### Verificar el rate limiting

```bash
# 1) auth: el burst es 10 y el rate 30 r/m; 20 logins seguidos deben acabar en 429
for i in $(seq 1 20); do
  code=$(curl -sk -o /dev/null -w '%{http_code}' -X POST https://127.0.0.1:8443/api/v1/auth/login \
    -H 'Content-Type: application/json' -d '{"email":"nadie@example.com","password":"x"}')
  echo "$i -> $code"
done
# esperado: los primeros ~10 dan 401/400 y el resto 429

# 2) cuerpo problem+json y Retry-After del 429
curl -sk -D - -o - -X POST https://127.0.0.1:8443/api/v1/auth/login \
  -H 'Content-Type: application/json' -d '{"email":"nadie@example.com","password":"x"}'

# 3) el rate limit NO se evade rotando X-Forwarded-For: la cabecera se sobrescribe
for i in $(seq 1 20); do
  curl -sk -o /dev/null -w '%{http_code} ' -X POST https://127.0.0.1:8443/api/v1/auth/login \
    -H "X-Forwarded-For: 203.0.113.$i" \
    -H 'Content-Type: application/json' -d '{"email":"nadie@example.com","password":"x"}'
done; echo
# esperado: también acaba en 429 (misma IP real)

# 4) catálogo público: tras agotar el burst de la zona public debe aparecer 429
for i in $(seq 1 60); do
  curl -sk -o /dev/null -w '%{http_code} ' https://127.0.0.1:8443/api/v1/activities
done; echo
```

`/api/v1/sites` sin token debe devolver `200` (no `502` ni `404`): el catálogo es lectura pública y
es ms-04 quien lo sirve. Una ruta privada como `/api/v1/reservations` sí debe devolver `401`.

---

## 9. Límites de conexión y modo producción (PLAT-03 / PLAT-10)

**Tiempos y conexiones por IP** (`nginx.conf`, fase 2 de seguridad): `keepalive_timeout 30s`,
`keepalive_requests 1000`, `client_header_timeout 10s`, `send_timeout 30s`,
`reset_timedout_connection on`, zona `limit_conn_zone $binary_remote_addr zone=conn_per_ip:10m` y
`limit_conn conn_per_ip 20` en ambos servers. El 429 de conexión sale con el mismo
`application/problem+json` y `Retry-After` que el de rate limiting.

- 20 conexiones simultáneas por IP es holgado para un navegador o un móvil (HTTP/2 multiplexa las
  peticiones en una conexión) y evita que una sola IP agote `worker_connections 1024`.
- Un cliente detrás de un NAT compartido (oficina, operador móvil) puede acercarse al límite: si
  necesitas subirlo, `limit_conn conn_per_ip 50` en `prod/gateway.conf` y en `conf.d/gateway.conf`.
- El límite de **conexiones** es independiente del de **peticiones**: una petición lenta (`slowloris`
  de cuerpo completo) está acotada por `client_body_timeout 30s` y `send_timeout 30s`, no por
  `limit_conn`.

**Producción** (`deploy/prod/compose.yaml`): monta `prod/gateway.conf`, donde `:443` sirve la API con
TLS y HSTS y `:80` solo responde `/healthz` y redirige con `308` a `https://$host$request_uri`. El
gateway es el único borde público (`0.0.0.0`); los cinco servicios se publican solo en
`127.0.0.1:${MS0X_PORT}` para operación y la infraestructura no publica puertos. En desarrollo
(`conf.d/gateway.conf`) el `:80` sigue sirviendo la API para no romper el smoke ni las pruebas E2E.

```bash
# comprobar el redirect de producción (sin Docker: no aplica; con el stack arriba)
curl.exe -s -o NUL -w "%{http_code} %{redirect_url}\n" http://127.0.0.1/api/v1/activities
# esperado: 308 https://.../api/v1/activities
curl.exe -s -o NUL -w "%{http_code}\n" http://127.0.0.1/healthz   # 200 ok
```

---

## 10. Problemas frecuentes

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
| `429` con "Demasiadas peticiones" en pruebas locales | La misma IP superó el cubo de `auth` o `public` (común en suites E2E) | Espera unos segundos, espacia las peticiones o sube el `burst` de esa `location` (sección 7). No es un fallo del servicio |
| `429` inmediato con muchas pestañas o peticiones paralelas | Se superaron las 20 conexiones simultáneas por IP (`limit_conn conn_per_ip 20`) | Cierra conexiones o sube el límite (sección 9); no afecta a un cliente normal |
| El `:80` devuelve `308` en producción | Comportamiento intencional: la API solo se sirve por HTTPS | Usa `https://`; `/healthz` sigue respondiendo `200` en claro |

---

## 12. Endurecimiento del contenedor y cabeceras (fase 3: PLAT-12 / PLAT-16)

**Contenedor sin privilegios (PLAT-12).** El gateway corre como usuario `nginx`, no como root:

- `deploy/gateway/Dockerfile` parte de `nginxinc/nginx-unprivileged:1.27-alpine`; `USER root` solo
  se usa para instalar `openssl` y borrar `default.conf`, y vuelve a `USER nginx` antes del arranque.
- nginx escucha en **8080/8443** (puertos no privilegiados) y en `nginx.conf` el `pid` y las rutas
  temporales viven en `/tmp`; los logs van a `stdout`/`stderr` (`/dev/stdout`, `/dev/stderr`).
- El certificado autofirmado de desarrollo se escribe en un `tmpfs` con `mode=1777` montado en
  `/etc/nginx/certs`; `entrypoint.sh` comprueba que el directorio es escribible y falla con un
  mensaje claro si no lo es.
- En producción (`deploy/prod/compose.yaml`) el contenedor es `read_only: true`, con `tmpfs` en
  `/tmp`, y el material TLS entra por secretos de solo lectura en `/etc/nginx/certs`.
- El healthcheck usa `http://127.0.0.1:8080/healthz` dentro del contenedor.

**Cabeceras de seguridad (PLAT-16).** `snippets/security-headers.conf` aplica a todas las respuestas
del gateway (desarrollo y producción):

| Cabecera | Valor |
|---|---|
| `X-Content-Type-Options` | `nosniff` |
| `X-Frame-Options` | `DENY` |
| `Referrer-Policy` | `no-referrer` |
| `Content-Security-Policy` | `default-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'` |
| `Permissions-Policy` | `camera=(), microphone=(), geolocation=(), payment=()` |
| `Cache-Control` | `no-store` (las respuestas pueden llevar datos personales) |
| `Strict-Transport-Security` | solo en el server TLS de producción (`prod/gateway.conf`) |

Detalles que importan:

- nginx **no** hereda `add_header` en un contexto hijo que declara sus propias cabeceras; por eso
  `security-headers.conf` se incluye también dentro de `@gateway_bad_gateway` y
  `@gateway_rate_limited` (las respuestas de error 503/429 también llevan las cabeceras).
- `nginx.conf` añade `proxy_hide_header Server;` y `proxy_hide_header X-Powered-By;` para no filtrar
  la versión del upstream.
- HSTS se mantiene **solo sobre TLS**: el server `8080` de producción no lo envía (un cliente HTTP
  plano no debe fijar HSTS).

```bash
# sin Docker: comprobar que los includes y las cabeceras están declarados
grep -n "security-headers.conf" deploy/gateway/conf.d/gateway.conf deploy/gateway/prod/gateway.conf
grep -n "proxy_hide_header" deploy/gateway/nginx.conf
# con el stack arriba (desarrollo):
curl.exe -s -D - -o NUL http://127.0.0.1:8088/healthz | Select-String "X-Content-Type-Options|Content-Security-Policy"
```

---

## 13. Referencias

- `compose.yaml` (raíz): servicio `gateway`, `tmpfs` de certificados, variables `GATEWAY_*`.
- `deploy/prod/compose.yaml`: servicio `gateway` con secretos TLS reales y `read_only`.
- `deploy/prod/README.md`: despliegue completo, secretos y verificación de producción.
- `README.md` (raíz): arranque del stack y tabla de puertos.
- `deploy/gateway/snippets/routes.conf`: la tabla de rutas real (fuente de verdad).
