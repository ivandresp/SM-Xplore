# loadtests — pruebas de carga del stack SM-Xplore (SPEC P1-8)

Pruebas de rendimiento con [k6](https://k6.io/) para el stack integrado de `compose.yaml`.
Miden latencia y tasa de error de los caminos de lectura (recomendaciones y catálogo) y del
flujo de negocio de un turista (reseña + reserva) contra el stack local levantado con
`docker compose up -d --wait`.

No forman parte del build de los servicios: son scripts independientes que se ejecutan en un
contenedor efímero de k6 conectado a la red `smxplore-stack`.

## SLOs medidos

| Script | Endpoint(s) | Escenario | Threshold (SLO) |
|---|---|---|---|
| `k6/recommendations.js` | `GET /api/v1/recommendations` (ms-05, bearer) | `smoke` (1 VU, 10s) y `load` (rampa) | `http_req_failed < 1%` y `http_req_duration{scenario:load}` **p95 < 600 ms** |
| `k6/catalog.js` | `GET /api/v1/sites` (ms-04, bearer) + `GET /api/v1/activities` y `GET /api/v1/events` (ms-03, públicos) | `smoke` y `load` | `http_req_failed < 1%` y **p95 < 300 ms** por endpoint |
| `k6/flows.js` | login turista + `GET /api/v1/activities/{id}` + `POST /api/v1/reviews` + `POST /api/v1/reservations` + `GET /api/v1/reservations/{id}` (ms-03) | 1 iteración por VU | thresholds laxos: fallos < 2%, p95 < 1500 ms, checks > 95% (hay escrituras y eventos de dominio detrás) |

Los thresholds viven en `options.thresholds` de cada script; k6 termina con código distinto de 0
si alguno se incumple, así que `run.sh` sirve tal cual en CI o en una puerta manual de release.

## Cómo correr

Requisitos:

1. Docker Desktop encendido con **≥ 8 GiB** de memoria asignada a la VM (ver presupuesto abajo).
2. Stack levantado y sano: `docker compose up -d --wait`.
3. Bash (Git Bash en Windows, `C:\Program Files\Git\bin\bash.exe`) para `run.sh`.

```bash
# Credencial del administrador del stack local: obligatoria, sin valor por defecto (PLAT-23).
export ADMIN_PASSWORD='<password>'   # Git Bash: export ... ; PowerShell: $env:ADMIN_PASSWORD='<password>'

# Humo de recomendaciones (1 VU, 10 s) contra la red smxplore-stack
./loadtests/run.sh recommendations

# Carga de catálogo desde el host (host.docker.internal + puertos publicados en 127.0.0.1)
./loadtests/run.sh catalog --target local

# Carga de recomendaciones: 20 VUs, meseta de 2 minutos
./loadtests/run.sh recommendations --scenario load --vus 20 --duration 2m

# Los tres scripts en secuencia
./loadtests/run.sh all --scenario load --vus 10
```

Cada ejecución deja un resumen JSON en `loadtests/results/<script>-<escenario>-<timestamp>.json`
(`--summary-export`). Ese directorio se crea solo y queda como salida no versionada; añádelo a
`.gitignore` si quieres mantener `git status` limpio.

Parámetros de `run.sh`: `--target docker|local`, `--scenario smoke|load`, `--vus N`,
`--duration 60s`, `--image IMAGEN`. Credenciales por entorno: `ADMIN_USER` / `ADMIN_PASSWORD`
(`ADMIN_USER` tiene default `admin@smxplore.local`; **`ADMIN_PASSWORD` no tiene default y el
lanzador falla con `exit 2` si falta** — PLAT-23); `USER` y `PASSWORD` se aceptan como alias. En
`recommendations.js` también se pueden pasar `TOURIST_USER` / `TOURIST_PASSWORD` para reutilizar un
turista existente en vez de registrar uno efímero.

### Credenciales y alcance (PLAT-23)

- Estos scripts son **solo para el stack local** de `compose.yaml` (datos de prueba, sin TLS).
  No los apuntes a un entorno con datos reales: el turista efímero se registra con
  `PASSWORD_TOURIST` (`PerfTest12345`, sobreescribible con `TOURIST_PASSWORD`) y el admin se
  autentica contra ms-01.
- `run.sh` pasa las credenciales al contenedor con `-e ADMIN_PASSWORD=...`, así que aparecen en
  `docker inspect` del contenedor efímero mientras dura la ejecución. Para el stack local es
  aceptable; si algún día se ejecutan contra un entorno compartido, hay que pasar el secreto por
  archivo (`--env-file` con permisos `0600`) y no por `-e`.
- Rota la credencial del admin en el stack local cambiando `BOOTSTRAP_ADMIN_PASSWORD` en `.env`
  (solo aplica al primer arranque; si el admin ya existe, cambia la contraseña por la API) y
  define el mismo valor en `ADMIN_PASSWORD`. Las credenciales de `scripts/smoke.sh` /
  `scripts/smoke.ps1` se leen de `BOOTSTRAP_ADMIN_PASSWORD` y también son rotables por `.env`.

### Destinos

| `--target` | Red de k6 | ms-01 (auth) | ms-03 | ms-04 | ms-05 |
|---|---|---|---|---|---|
| `docker` (default) | `smxplore-stack` | `http://ms-01:8081` | `http://ms-03:8082` | `http://ms-04:8080` | `http://ms-05:8080` |
| `local` | bridge + `host.docker.internal` | `:8081` | `:8082` | `:8084` | `:8085` |

`docker` mide la latencia real entre contenedores (sin NAT del host); `local` sirve para probar
sin unirse a la red del compose. `run.sh` verifica que la red exista antes de lanzar k6 cuando
el target es `docker`.

## Qué mide cada script

### `k6/recommendations.js`

- `setup()`: login de administrador y de turista. Si no se pasan `TOURIST_USER`/`TOURIST_PASSWORD`
  registra un turista efímero (`perf-rec-…@smxplore.local`) y lo loguea.
- Cada iteración: `GET /api/v1/recommendations?limit=20&lat=11.2408&lng=-74.1990` con bearer.
  80% con token de turista (camino real con perfil) y 20% con token de admin (soporte).
- Comprueba estado 200, `items[]` presente y `source` dentro de
  `AFFINITY|QUALITY|NEARBY|FALLBACK|MIXED`. `items[]` puede llegar vacío mientras la proyección
  de Neo4j se pone al día: eso **no** es un fallo del SLO.
- Métrica propia: `recommendation_payload_ok` (tasa de respuestas con el contrato esperado).

### `k6/catalog.js`

- `setup()`: login de administrador porque **ms-04 protege `GET /api/v1/sites` con JWT**
  (cualquier rol autenticado); el SPEC asumía `/sites` público, pero el stack real exige token, así
  que el script inicia sesión y manda el bearer solo en ese listado. `activities` y `events` de
  ms-03 son públicos y se piden sin token.
- Comprueba estado 200 y `PageResponse.content[]` en los tres listados.
- Métricas propias: `catalog_items` (Gauge, elementos por página y endpoint) y
  `catalog_empty_pages` (Rate de páginas vacías). **ms-03 no trae datos semilla** en
  `src/main/resources` (solo migraciones Flyway), así que en un stack recién levantado es normal
  ver `catalog_empty_pages = 1`; `flows.js` crea su propio catálogo.

### `k6/flows.js`

- `setup()`: login admin; reutiliza actividades `PUBLISHED` con fechas futuras y capacidad, o crea
  categoría, zona, sitio y `VUS` actividades (publicadas con `PATCH …/status`) si no hay catálogo;
  después registra **un turista único por VU** y lo loguea.
- Cada VU: lee su actividad, crea **una reseña** (`POST /api/v1/reviews`, 201) y **una reserva**
  (`POST /api/v1/reservations`, 201) para sí mismo, y la relee (`GET`, 200).
- **Diseño de datos único por VU** por las reglas de negocio reales:
  una reseña por turista e ítem (`CreateReviewUseCase`: *"The user already reviewed this item."*) y
  máximo 5 reservas activas por turista (`MAX_ACTIVE_RESERVATIONS_PER_TOURIST = 5`). Por eso el
  escenario es `per-vu-iterations` con 1 iteración por VU; subir `--vus` añade turistas y
  actividades nuevos, no repite escrituras sobre los mismos ítems.
- Deja datos de prueba en el stack local (nombres `Perf … <timestamp>`). Es un script de escritura:
  no apuntarlo a un entorno compartido.

## Presupuesto de memoria

`compose.yaml` fija `mem_limit` por contenedor (raíz del repo). Suma real de los límites:

| Contenedor | `mem_limit` | Línea en `compose.yaml` |
|---|---:|---:|
| postgres | 512 MiB | 53 |
| mongo | 1 GiB (1024 MiB) | 77 |
| mongo-init | 256 MiB | 95 |
| kafka | 1536 MiB | 107 |
| kafka-ui | 512 MiB | 139 |
| redis | 256 MiB | 153 |
| neo4j | 2 GiB (2048 MiB) | 169 |
| mailpit | 256 MiB | 191 |
| ms-01 … ms-05 (×5) | 768 MiB c/u | 207, 249, 277, 322, 362 |
| **Total** | **10 240 MiB = 10.0 GiB** | |

Desglose: infraestructura 6400 MiB (6.25 GiB) + microservicios 3840 MiB (3.75 GiB).
Cada servicio corre con `JDK_JAVA_OPTIONS: "-Xms64m -Xmx512m -XX:MaxDirectMemorySize=128m"`
(heap 512 MiB + direct 128 MiB + metaspace/overhead dentro de 768 MiB).

Comparación con el README raíz (`README.md:12-13`):

> Docker Desktop con al menos **7 GB libres** (8 GB recomendados; el stack en reposo usa ~3,5 GiB).

Es decir: el README pide 7–8 GiB, pero la **suma de techos es 10 GiB**, unos 2 GiB por encima.
No es un fallo en reposo (los límites son techos, no reservas: el stack usa ~3.5 GiB), pero si
todo el stack se acerca a sus límites durante una prueba de carga, una VM de 8 GiB puede quedar
sin memoria y Docker empieza a matar procesos (OOM).

Propuesta de límites para un presupuesto de **8 GiB** (solo recomendación; el SPEC P1-8 prohíbe
editar `compose.yaml`):

| Contenedor | Actual | Recomendado 8 GiB | Ahorro | Motivo |
|---|---:|---:|---:|---|
| neo4j | 2048 MiB | 1536 MiB | 512 | heap máx. 1G → 768m y pagecache 256m → 192m (ms-05 solo proyecta un grafo pequeño) |
| kafka | 1536 MiB | 1024 MiB | 512 | un broker KRaft con 7 topics y tráfico de demo |
| mongo | 1024 MiB | 768 MiB | 256 | replica set de un nodo para reseñas |
| ms-01 … ms-05 | 768 MiB c/u | 640 MiB c/u | 640 | con `-Xmx384m -XX:MaxDirectMemorySize=96m`; 640 MiB cubre heap + direct + metaspace |
| kafka-ui | 512 MiB | 192 MiB | 320 | UI de desarrollo |
| mongo-init | 256 MiB | 128 MiB | 128 | contenedor de un solo uso |
| mailpit | 256 MiB | 128 MiB | 128 | captura SMTP de desarrollo |
| redis | 256 MiB | 192 MiB | 64 | caché de ms-05 |
| postgres | 512 MiB | 512 MiB | 0 | tres bases pequeñas, se mantiene |
| **Total** | **10 240 MiB** | **7680 MiB = 7.5 GiB** | **2560 MiB** | deja ~0.5 GiB para el motor Docker y el contenedor de k6 |

Notas de aplicación si se adopta (fuera del alcance de P1-8):

- Cambiar `mem_limit` **y** los `JDK_JAVA_OPTIONS` de los servicios a la vez: 640 MiB con
  `-Xmx512m` + 128 MiB directos no deja sitio para metaspace.
- Con Neo4j a 1536 MiB hay que bajar `NEO4J_server_memory_heap_max__size` a `768m` y
  `NEO4J_server_memory_pagecache_size` a `192m` en el mismo bloque.
- Configurar Docker Desktop → Resources → Memory a 8 GiB y swap de 2 GiB.
- Verificar con `docker stats --no-stream` durante la meseta de `--scenario load` y con
  `docker compose config` (valida el compose).
- k6 no está en `compose.yaml`: su contenedor consume ~120–250 MiB con 10–20 VUs y corre
  **fuera** de los 7.5 GiB anteriores.

## Limitaciones y riesgos

- Los SLOs están pensados para el **stack local en una sola máquina**, no para producción: el
  target `docker` elimina el NAT del host y da la latencia más representativa del diseño.
- `flows.js` escribe (reseñas y reservas) y dispara eventos de dominio (Kafka → ms-05). No
  ejecutarlo contra entornos compartidos; cada corrida deja datos `Perf …`.
- Los tokens de ms-01 expiran; una corrida de carga muy larga (> `expiresInSeconds`) haría fallar
  las peticiones por 401. Para mesetas largas, repetir `run.sh` en tramos en vez de una sola corrida.
- Si `ms-01` exige MFA para la cuenta usada, los scripts abortan en `setup()` con un mensaje
  explícito; usar una cuenta sin MFA (el admin bootstrap local no la exige).
- `catalog.js` tolera catálogo vacío, pero un `p95 < 300 ms` con tablas vacías mide poco: para una
  medición realista, ejecutar antes `flows.js` (crea catálogo) o `scripts/smoke.sh`.
- El registro de turistas (`POST /api/v1/auth/register/turista`) se llama `VUS` veces en `setup()`;
  con cientos de VUs conviene reutilizar credenciales (`TOURIST_USER`/`TOURIST_PASSWORD`) en
  `recommendations.js`.
