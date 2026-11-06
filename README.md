# SM-Xplore

Plataforma de gestion turistica para Santa Marta. Monorepo de cinco microservicios backend
(Java 25 + Spring Boot 4.1.2 + WebFlux), contratos de eventos versionados e infraestructura
local.

Este README es la puerta de entrada al **stack integrado**. Cada servicio conserva su propio
`README.md`, `CHANGELOG.md`, `Dockerfile` y `docker-compose.yml` para trabajar aislado.

## Arranque completo (un solo comando)

Requisitos: Docker Desktop con al menos **7 GB libres** (8 GB recomendados; el stack en reposo
usa ~3,5 GiB). La suma de los `mem_limit` de `compose.yaml` es **7,5 GiB** (antes 10 GiB), así
que una VM de 8 GiB cubre el techo completo del stack local. Las pruebas de carga (`loadtests/`)
documentan el presupuesto por contenedor y los SLOs. También deben estar libres los puertos que
lista la tabla de abajo.

```powershell
Copy-Item .env.example .env   # opcional: todos los valores tienen default
docker compose up -d --build --wait --wait-timeout 600
```

```bash
cp .env.example .env          # opcional
docker compose up -d --build
```

El primer arranque compila las cinco imagenes (Gradle dentro de Docker) y puede tardar varios
minutos. El flag `--wait` hace que el comando termine solo cuando todos los healthchecks estan
`healthy`; sin el, "arrancado" no significa "listo". Los servicios esperan a que la
infraestructura este sana y a que ms-01 publique su JWKS. Verifica el estado con:

```powershell
docker compose ps
bash scripts/smoke.sh          # o: pwsh scripts/smoke.ps1
```

Cuando todo esta `healthy`, la plataforma queda disponible en `http://127.0.0.1:8088` (gateway,
unico punto de entrada: HTTP en 8088 y HTTPS en 8443) y, si se necesitan, en los puertos directos
de la tabla siguiente.

## Servicios y puertos

| Componente | Host | Contenedor | Notas |
|---|---:|---:|---|
| gateway (nginx) | 8088 / 8443 | 80 / 443 | Punto de entrada: `/api/v1/**` y `/svc/ms-0N/**`; TLS autofirmado en desarrollo |
| ms-01 users-and-anomalies | 8081 | 8081 | Emisor JWT; JWKS en `/.well-known/jwks.json` |
| ms-02 security-reports | 8080 | 8080 | Reportes y comentarios |
| ms-03 activities-and-events | 8082 | 8082 | Actividades, eventos, reservas y reseñas |
| ms-04 sites-and-services | 8084 | 8080 | Catalogo, reservas, notificaciones y reseñas |
| ms-05 recommendations | 8085 | 8080 | Neo4j, cache Redis y consumidores Kafka |
| PostgreSQL | 15432 | 5432 | Bases `smxplore_users`, `activities_events`, `smxplore` |
| MongoDB | 27019 | 27017 | Replica set `rs0` de un nodo |
| Kafka (host) | 29092 | 9092 | Un broker KRaft; topics autocreados en desarrollo |
| Kafka UI | 8090 | 8080 | `http://localhost:8090` |
| Redis | 6379 | 6379 | Cache de ms-05 |
| Neo4j | 7474 / 7687 | 7474 / 7687 | Navegador y Bolt |
| Mailpit | 8025 / 1025 | 8025 / 1025 | UI de correo y SMTP |

Todos los puertos se publican en `127.0.0.1`; no hay colisiones entre servicios. PostgreSQL y
MongoDB usan puertos de host no estandar (15432/27019) para no chocar con instalaciones locales
ni con los compose por servicio.

## Que hay dentro

- `microservices/ms-01..ms-05`: servicios autocontenidos (build Gradle propio).
- `contracts/`: sobres de eventos y esquemas versionados (`v1`, `v2`).
- `infrastructure/`: compose solo de Kafka, Kafka UI y Redis para trabajar sin el stack completo.
- `deploy/`: scripts de inicializacion del stack integrado (bases y replica set).
- `deploy/gateway/`: API gateway nginx (rutas, TLS y runbook paso a paso en su `README.md`).
- `compose.yaml`: composicion integrada de infraestructura + cinco servicios.
- `scripts/smoke.*`: verificacion del stack completo: salud, JWKS, endpoints publicos,
  `/actuator/prometheus` en los cinco servicios y un flujo de negocio real (categoria, zona, sitio,
  actividad, resena y reserva de un turista registrado, con comprobacion del outbox cuando hay docker).
- `.github/workflows/stack-ci.yml`: valida `compose.yaml`, cada compose por servicio, `deploy/**` y
  `scripts/**`, y despues levanta el stack y corre el smoke (los workflows `ms-0X-ci.yml` validan un
  servicio cada uno contra sus propias dependencias).
- `docs/`: referencia de modelos, historias de usuario y SRS (solo lectura).

## Orden de arranque

1. `postgres`, `mongo` (replica set) y `kafka` arrancan con healthcheck.
2. `mongo-init` inicia `rs0` y espera al primario (ms-02 y ms-04 usan transacciones).
3. `ms-01` migra `smxplore_users`, genera la pareja RSA en el volumen `ms01_data` y publica JWKS.
4. `ms-04` migra `smxplore`; `ms-02`, `ms-03` y `ms-05` esperan a ms-01 (y ms-03/ms-05 a ms-04).
5. `ms-03` corre Flyway como `ms03_flyway` y la aplicacion conecta como `ms03_app`.

El stack define `SPRING_PROFILES_ACTIVE=local` en cada servicio: el flag
`JWT_ALLOW_INSECURE_JWKS=true` (necesario porque ms-01 se alcanza por HTTP en la red privada) solo
se acepta con un perfil local, y el perfil `prod` lo prohibe y exige emisor y audiencia.

## Credenciales de desarrollo

| Que | Valor |
|---|---|
| Administrador inicial ms-01 | `admin@smxplore.local` / `ChangeMe123!` |
| PostgreSQL | `smxplore` / `smxplore` |
| Roles ms-03 | `ms03_flyway` / `ms03_flyway`, `ms03_app` / `ms03_app` |
| Neo4j | `neo4j` / `change-me` |

El perfil por defecto de ms-01 es `dev`: genera y persiste las llaves JWT en el volumen
`ms01_data`. Si borras el volumen, los tokens emitidos dejan de valer.

## Notas de seguridad (solo local)

Este compose es de **desarrollo**. No usa TLS, MongoDB corre sin autenticacion (bases
separadas por servicio) y los secretos viven en `.env`. En produccion:

- Perfil `prod` en los cinco servicios (activa `ver`, MFA administrativo y validaciones de
  arranque).
- MongoDB con autenticacion y usuarios por servicio; PostgreSQL con roles acotados.
- TLS terminado en un gateway/reverse proxy comun.
- Secretos en un gestor, no en `.env`; Kafka y Redis con autenticacion.

## Operacion diaria

```powershell
docker compose ps                  # estado y salud
docker compose logs -f ms-03       # logs de un servicio
docker compose up -d --build --wait ms-03 # reconstruir uno solo
docker compose down                # detener (conserva datos: los volumenes y Kafka persisten)
docker compose down -v             # detener y borrar volumenes (reset total)
```

Los contenedores rotan logs (`10 MB x 3` por servicio) para no llenar el disco. Cada servicio
tambien puede levantarse aislado con su propio compose; en ese caso necesita la
red `smxplore-net` (la crea `infrastructure/compose.yaml`) y sus propias bases. No ejecutes
`infrastructure/compose.yaml` al mismo tiempo que este stack: ambos publican Kafka y Redis.

## Verificacion

```bash
bash scripts/smoke.sh
```

El script comprueba:

- `/actuator/health` de los cinco servicios (estado `UP`).
- JWKS de ms-01 con al menos una llave.
- Endpoints publicos de catalogo de ms-03.
- Que Kafka UI, Mailpit y Neo4j respondan en sus puertos (Kafka y Redis se validan a traves de la salud de los servicios que los usan).

Para las suites por servicio:

```bash
cd microservices/ms-03-activities-and-events && ./gradlew test
```

Las pruebas de integracion usan Testcontainers y requieren Docker.

## Problemas frecuentes

| Sintoma | Causa / solucion |
|---|---|
| `port is already allocated` | Otro proceso ocupa el puerto. Cambia el puerto en `.env` o detiene el otro proceso. |
| ms-02..05 arrancan pero rechazan tokens | ms-01 no esta sano o el volumen `ms01_data` se recreo. Revisa `docker compose logs ms-01` y vuelve a iniciar sesion. |
| ms-03 falla en Flyway | El volumen de Postgres se creo con otro usuario/rol. `docker compose down -v` para reinicializar. |
| MongoDB desde el host falla en transacciones | El replica set se anuncia como `mongo:27017`. Usa `mongodb://localhost:27019/?directConnection=true`. |
| Kafka sin topics | Se autocrean al publicar. Revisa `http://localhost:8090`. |
| Consumo alto de memoria | Asigna mas memoria a Docker Desktop o levanta solo lo necesario con `docker compose up -d postgres mongo mongo-init ms-01 ms-03`. |

## Estado del proyecto

Los servicios compilan y sus suites pasan. El gateway nginx ya es el punto de entrada del stack
local (TLS en produccion sigue pendiente de la decision P3-11: certificados reales y terminacion
TLS definitiva).

Entregado y verificado: P0 (reindexado completo de ms-05, durabilidad de eventos de resena,
eventos de zona y respuesta de resena, higiene de contratos), P1 (imagenes y manifiestos de
produccion, topicos y DLQ explicitos, alertas, backup/restore, pruebas de carga) y P2
(endpoint interno de contactos, canal push y alerta de cupo bajo RF66, pruebas de arquitectura y
de contrato del lado consumidor, trazabilidad de los requisitos del SRS).

Lo que sigue abierto son las decisiones de equipo P3 (reservas multi-item, mapeo canonico de
estados, PSE e itinerario, auth servicio-a-servicio, consumidor de `security.report.changed`,
taxonomia de categorias, TLS de produccion y despliegue del stack productivo), el frontend y la
documentacion UML de los cinco servicios. El detalle por decision esta en los `README.md` de cada
servicio y en el resumen de integracion del equipo.
