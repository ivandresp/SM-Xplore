# Despliegue de producción de SM-Xplore (P1-5)

Stack de producción en un solo host con Docker Compose. Cubre build/publicación de las cinco
imágenes, pineado por digest, secretos, arranque, verificación y rollback.

**Fuera de alcance (diferido):**
- **TLS de extremo a extremo / HA del borde: P3-11.** Este compose **sí** incluye el gateway nginx
  como único borde público: `:443` sirve la API con TLS (certificados en secretos
  `gateway_tls_cert`/`gateway_tls_key`) y `:80` solo redirige con 308 (PLAT-03, sección 11). Lo que
  queda diferido es un balanceador/HA por delante y la rotación automática de certificados (p. ej.
  ACME); si se añade un proxy externo, este puede seguir terminando TLS y reenviar al `:443` del
  gateway. `SECURITY_HSTS_ENABLED=true` y `server.forward-headers-strategy` (perfil prod) siguen
  esperando las cabeceras `X-Forwarded-*` que inyecta el gateway.
- **Tópicos y alertas:** `deploy/kafka/topics.env`, `deploy/observability/prometheus.yml` y
  `deploy/observability/alerts.yml` los mantiene P1-6. El arranque de Kafka **sí** forma parte de
  este stack: formato del volumen, material TLS, usuarios SCRAM, ACLs y creación de tópicos
  (`kafka-format` → `kafka` → `kafka-init`; ver sección 10).
- **Mongo con autenticación/TLS:** los scripts `deploy/mongo/gen-keyfile.sh`, `gen-tls.sh` e
  `init-replica-auth.sh` los mantiene P1-7.
- **Backups:** P1-7 (`deploy/backup/`).

## Contenido

| Archivo | Para qué |
| --- | --- |
| `compose.yaml` | stack completo (apps, Postgres, Mongo RS 3 nodos, Kafka, Redis, Neo4j, Prometheus, exporters) |
| `.env.prod.example` | todas las variables obligatorias; copiar a `.env.prod` |
| `images.env` | referencias `repo@sha256:...` de las cinco imágenes; lo escribe `pin-images.sh` |
| `pin-images.sh` | resuelve digests con `docker buildx imagetools inspect` y reescribe `images.env` |
| `../kafka/gen-tls.sh` | CA + keystore/truststore PKCS12 del broker (una vez por instalación) |
| `../kafka/create-acls.sh` | usuarios SCRAM y ACLs mínimas por servicio (lo ejecuta `kafka-init`) |
| `../kafka/render-client-config.sh` | materializa un `client.properties` 0600 desde variables de entorno |
| `../kafka/client.properties.example` | plantilla/ejemplo para scripts y verificación manual |

## Servicios y puertos

| Servicio | Puerto interno | Puerto host | Salud |
| --- | --- | --- | --- |
| ms-01-users-and-anomalies | 8081 | `127.0.0.1:${MS01_PORT}` = 8081 | `/actuator/health` |
| ms-02-security-reports | 8080 | `127.0.0.1:${MS02_PORT}` = 8080 | `/actuator/health` |
| ms-03-activities-and-events | 8082 | `127.0.0.1:${MS03_PORT}` = 8082 | `/actuator/health` |
| ms-04-sites-and-services | 8080 | `127.0.0.1:${MS04_PORT}` = 8084 | `/actuator/health` |
| ms-05-recommendations | 8080 | `127.0.0.1:${MS05_PORT}` = 8085 | `/actuator/health` |
| gateway | 8080 / 8443 (host 80 / 443) | `${GATEWAY_HTTP_PORT}` = 80 → 8080 y `${GATEWAY_HTTPS_PORT}` = 443 → 8443, **en todas las interfaces** | `/healthz` en `:8080` |
| postgres / mongo1-3 / kafka / redis / neo4j / prometheus / exporters | internos | **ninguno** | healthcheck interno |

Ninguna base de datos, broker, caché, Prometheus ni exporter publica puertos: solo se exponen los
`808x` de las aplicaciones (atados a loopback para operación) y el gateway en `0.0.0.0`, que es el
único ingreso público y solo sirve la API por TLS (PLAT-03).

Orden de arranque: Postgres y Mongo (3 nodos) sanos → `mongo-init` (replica set + usuarios) →
`kafka-format` (formatea el volumen y siembra el usuario admin SCRAM) → `kafka` (SASL_SSL) →
`kafka-init` (usuarios SCRAM por servicio, ACLs y tópicos) → `ms-01` (crea el admin inicial y firma
JWT) → `ms-04` → `ms-02`/`ms-03`/`ms-05`. Los servicios que hablan con Kafka declaran
`depends_on: kafka-init: service_completed_successfully`: si el aprovisionamiento del broker falla,
no arrancan (fail-closed).

## 1. Publicar las imágenes

```bash
git tag v1.2.3 && git push origin v1.2.3
```

`.github/workflows/release-images.yml` construye los cinco `Dockerfile` en matriz y publica en
`ghcr.io/ivandresp/sm-xplore-<servicio>` con **dos** etiquetas: `<sha>` y `<versión>` (PLAT-14: no se
publica `latest`, para que nada consuma una imagen flotante). El resumen del job imprime el digest
de cada imagen. Los PR no publican nada. También se puede lanzar a mano (`workflow_dispatch`)
indicando la versión.

## 2. Pinear los digests

```bash
docker login ghcr.io
deploy/prod/pin-images.sh v1.2.3               # servicios + infraestructura + gateway
deploy/prod/pin-images.sh v1.2.3 --apps-only   # solo las cinco imágenes de servicios
```

`images.env` queda con las cinco referencias `repo@sha256:...` de los servicios y, salvo
`--apps-only`, también las de infraestructura y gateway (PLAT-13). El script es idempotente, falla si
alguna imagen no existe y no toca nada hasta haber resuelto todos los digests. Las imágenes de
infraestructura se pinean en el tag por defecto de `.env.prod.example`; para subir de versión exporta
la referencia (`POSTGRES_REF=postgres:17.4-alpine deploy/prod/pin-images.sh v1.2.3`). Las imágenes
base de los `Dockerfile` de los servicios (`eclipse-temurin`) las fija el dueño de cada servicio.

## 3. Preparar los secretos

```bash
mkdir -p deploy/prod/secrets && chmod 700 deploy/prod/secrets

# Par de llaves RSA del emisor JWT: el archivo de ms-01 lleva privada + pública.
openssl genpkey -algorithm RSA -pkeyopt rsa_keygen_bits:2048 -out /tmp/jwt-private.pem
openssl rsa -in /tmp/jwt-private.pem -pubout -out /tmp/jwt-public.pem
cat /tmp/jwt-private.pem /tmp/jwt-public.pem > deploy/prod/secrets/jwt-keys.pem
cp /tmp/jwt-public.pem deploy/prod/secrets/jwt-public.pem
rm -f /tmp/jwt-private.pem /tmp/jwt-public.pem

# Keyfile y certificados del replica set (scripts de P1-7).
deploy/mongo/gen-keyfile.sh    # deja KEYFILE en deploy/mongo/secrets/
deploy/mongo/gen-tls.sh        # deja CA + servidor en deploy/mongo/certs/

# Material TLS del broker Kafka (PLAT-01: SASL_SSL). Genera CA, keystore y truststore PKCS12.
# Guarda los dos passwords en .env.prod (KAFKA_KEYSTORE_PASSWORD / KAFKA_TRUSTSTORE_PASSWORD).
deploy/kafka/gen-tls.sh --keystore-password "$(openssl rand -hex 16)" \
                        --truststore-password "$(openssl rand -hex 16)"

# Clave AES para cifrar los secretos TOTP.
openssl rand -base64 32        # valor de MFA_SECRET_KEY en .env.prod
```

Copia el keyfile y los certificados a `deploy/prod/secrets/` o apunta `MONGO_KEYFILE_FILE`,
`MONGO_CA_FILE` y `MONGO_SERVER_CERT_FILE` a donde estén. Las rutas de `secrets.file` se resuelven
relativas a este `compose.yaml`. Los archivos deben existir antes de `docker compose up`.
Nada de esto se versiona.

Para Kafka, copia el material a `deploy/prod/secrets/kafka/` y ajusta `KAFKA_CA_FILE`,
`KAFKA_KEYSTORE_FILE` y `KAFKA_TRUSTSTORE_FILE` (en `.env.prod.example` ya apuntan ahí):

```bash
mkdir -p deploy/prod/secrets/kafka && chmod 700 deploy/prod/secrets/kafka
install -m 0444 deploy/kafka/certs/kafka-ca.crt          deploy/prod/secrets/kafka/
install -m 0400 deploy/kafka/certs/kafka.keystore.p12    deploy/prod/secrets/kafka/
install -m 0444 deploy/kafka/certs/kafka.truststore.p12  deploy/prod/secrets/kafka/
```

> Aviso de integración: `deploy/mongo/init-replica-auth.sh` (P1-7) recibe por entorno
> `MONGO_REPLICA_SET`, `MONGO_HOSTS`, `MONGO_TLS`, `MONGO_CA_FILE`, `MONGO_ROOT_USER`,
> `MONGO_ROOT_PASSWORD` y los pares `MONGO_USER_MS0x`/`MONGO_PASSWORD_MS0x`. Si P1-7 usa otros
> nombres, hay que alinear uno de los dos lados.

## 4. Configurar `.env.prod`

```bash
cp deploy/prod/.env.prod.example deploy/prod/.env.prod
$EDITOR deploy/prod/.env.prod     # reemplazar todos los `cambiar-...`
```

Todas las variables obligatorias aparecen con `${VAR:?}` en el compose: si falta una, `docker
compose` falla al interpolar en lugar de arrancar con un valor por defecto inseguro.

### Variables de entorno relevantes (tabla completa de controles)

Las que tienen default en el compose no hace falta declararlas; se listan para que la tabla de
controles y variables quede cerrada (fase BAJA):

| Variable | Defecto | Servicio | Para qué |
|---|---|---|---|
| `NOTIFICATIONS_DLQ_TOPIC` | `kafka.dlq.bookings.reservation.changed` | ms-04 | Topic de descarte del listener de `bookings.reservation.changed` (MS04-11). Lo crea `deploy/kafka/topics.env`. |
| `REVIEW_REQUIRE_COMPLETED_BOOKING` | `true` | ms-04 | Solo se puede reseñar con una reserva `COMPLETED` (MS04-12). |
| `REVIEW_AI_FAIL_OPEN` | `false` | ms-01, ms-03, ms-04 | Si el filtro IA no responde: `false` = fail-closed (503, la reseña no se publica); `true` = publicar igual. En prod se fuerza `false`. |
| `HSTS_ENABLED` | `true` (prod) / `false` (dev) | ms-02 | Cabecera `Strict-Transport-Security` del propio servicio; solo tiene sentido detrás de TLS (el gateway ya la envía). |
| `MAILPIT_UI_AUTH` | `mailpit:mailpit-dev` (dev) | mailpit | Basic auth de la UI de correo (`usuario:password`); no se usa en producción. |
| `KAFKA_UI_USER` / `KAFKA_UI_PASSWORD` | `admin` / `kafka-ui-dev` (dev) | kafka-ui | Credenciales de la UI de Kafka (`AUTH_TYPE=LOGIN_FORM`); no se usa en producción. |
| `NEO4J_INITIALIZE_SCHEMA` | `false` (prod) | ms-05 | Ejecuta el DDL de esquema de Neo4j; paso explícito de bootstrap (MS05-08). |
| `BOOTSTRAP_ADMIN_ENABLED` | `false` (prod) | ms-01 | Alta del administrador inicial como paso explícito (PLAT-17). |
| `POSTGRES_IMAGE` / `MONGO_IMAGE` / `NEO4J_IMAGE` | tags de `.env.prod.example` | infra | Permiten fijar un digest concreto; `pin-images.sh` los escribe en `images.env` (PLAT-13). |
| `POSTGRES_R2DBC_QUERY` / `POSTGRES_JDBC_QUERY` / `POSTGRES_SSLMODE` | vacío / `disable` | ms-01/03/04 + exporter | Activan TLS de PostgreSQL sin editar el compose (PLAT-05). |

MongoDB de desarrollo (`compose.yaml`) no pide credenciales a propósito (MS02-13): solo escucha en
`127.0.0.1` y en la red interna, y usa una base por servicio. En este compose de producción el
replica set es de tres nodos con `--auth`, keyfile y TLS, y cada servicio entra con su usuario
`MONGO_USER_MS0x`. Para habilitar auth en desarrollo, el bloque comentado del servicio `mongo` en
`compose.yaml` explica los tres pasos.

## 5. Levantar el stack

```bash
# Desde la raíz del repositorio. El segundo --env-file gana: los digests de images.env mandan.
docker compose \
  --env-file deploy/prod/.env.prod \
  --env-file deploy/prod/images.env \
  -f deploy/prod/compose.yaml up -d

docker compose -f deploy/prod/compose.yaml ps
```

Si el `up` no espera: `up -d --wait --wait-timeout 900` (los one-shot `mongo-init` y `kafka-init`
deben terminar en `Exited (0)`).

## 6. Verificar

```bash
for p in 8081 8080 8082 8084 8085; do curl -fsS "http://127.0.0.1:$p/actuator/health"; echo; done

# Replica set: debe salir un PRIMARY y dos SECONDARY.
docker compose -f deploy/prod/compose.yaml exec mongo1 mongosh --quiet \
  --tls --tlsCAFile /tmp/mongo-ca.pem --eval 'rs.status().members.map(m => m.stateStr)'

# Tópicos creados por kafka-init (el broker exige SASL_SSL: usa un client.properties).
docker compose -f deploy/prod/compose.yaml run --rm --no-deps kafka-init bash -lc \
  'bash /scripts/render-client-config.sh --out /tmp/admin-client.properties && \
   bash /scripts/create-acls.sh --list --command-config /tmp/admin-client.properties'

# Usuarios SCRAM y ACLs mínimas (idempotente; lo mismo que ejecuta kafka-init).
docker compose -f deploy/prod/compose.yaml run --rm --no-deps kafka-init bash -lc \
  'bash /scripts/render-client-config.sh --out /tmp/admin-client.properties && \
   bash /scripts/create-acls.sh --command-config /tmp/admin-client.properties'

# Prometheus ve los cinco servicios y los exporters.
docker compose -f deploy/prod/compose.yaml exec prometheus \
  wget -qO- http://localhost:9090/api/v1/targets | head -c 2000
```

Además: revisar `docker compose logs -f kafka-init mongo-init` (deben terminar sin error) y,
tras el primer login de administrador, poner `BOOTSTRAP_ADMIN_ENABLED=false` en `.env.prod` y
recrear `ms-01`.

## 7. Rollback

Las imágenes son inmutables por digest, así que el rollback no recompila:

```bash
cp deploy/prod/images.env deploy/prod/images.env.bak
deploy/prod/pin-images.sh <tag-anterior>     # o editar los digests a mano
docker compose --env-file deploy/prod/.env.prod --env-file deploy/prod/images.env \
  -f deploy/prod/compose.yaml up -d --no-deps ms-01 ms-02 ms-03 ms-04 ms-05
```

Flyway aplica migraciones hacia adelante: un rollback de imagen NO revierte el esquema. Si la
versión anterior es incompatible con las migraciones nuevas, hay que restaurar el backup (P1-7)
antes de volver.

## 8. Limitaciones conocidas

- **Un solo broker Kafka** (KRaft, replicación 1): no hay tolerancia a la caída del broker. Un
  clúster de 3 con RF 3 requiere cambiar `KAFKA_*` y `topics.env` (P1-6).
- **Mongo TLS con CA propia**: los servicios apuntan a `/run/secrets/mongo_ca` con
  `tlsCAFile=%2Frun%2Fsecrets%2Fmongo_ca`. Si se rota la CA hay que recrear los contenedores.
- **Correo saliente apagado** (`NOTIFICATIONS_EMAIL_ENABLED=false` y `spring.mail` de ms-01 sin
  autenticación): configurar un SMTP real antes de habilitar notificaciones.
- **Índices de Mongo**: `MONGO_AUTO_INDEX_CREATION=true` para que la primera ejecución cree los
  índices; P1-7 documenta el aprovisionamiento y luego puede ponerse en `false`.
- **Sin HA de aplicación**: una réplica por servicio; el proxy externo debería reintentar.
- **Secretos en archivos del host**: Docker Compose los monta en `/run/secrets` (0444). Rotarlos es
  recrear contenedores; para producción real, usar el gestor de secretos del orquestador.
- **Passwords SCRAM de Kafka en `.env.prod`**: Compose no puede inyectar el contenido de un archivo
  secreto dentro de `sasl.jaas.config`, así que `KAFKA_*_PASSWORD` viajan como variables de entorno
  (visibles en `docker inspect`). El material TLS, en cambio, sí va por `secrets.file`. En un
  despliegue real, inyéctalas desde el gestor de secretos del orquestador o un `.env.prod` generado
  en el arranque, nunca versionado.
- **Sin mTLS en Kafka** (`KAFKA_SSL_CLIENT_AUTH=none`): los clientes se autentican solo con
  SCRAM-SHA-512 sobre TLS; si se requiere identidad por certificado, hay que distribuir un
  certificado por servicio y cambiar `ssl.client.auth` a `required`.
- **PostgreSQL sin TLS en esta entrega** (PLAT-05): los roles por servicio ya están separados, pero
  el tráfico dentro de `smxplore-prod` sigue en claro hasta montar certificados y activar
  `POSTGRES_R2DBC_QUERY`/`POSTGRES_JDBC_QUERY`/`POSTGRES_SSLMODE` (sección 11).
- **Claves de backup fuera del host**: sin `AGE_IDENTITY_FILE`/`BACKUP_HMAC_KEY_FILE` un backup
  cifrado no se puede restaurar ni verificar (PLAT-07); guárdalas en el gestor de secretos, nunca
  junto a `deploy/backups/`.
- **Neo4j con usuario único** (MS05-08): Community no tiene RBAC multiusuario; solo existe `neo4j`,
  con password obligatoria, sin puertos al host y con `NEO4J_INITIALIZE_SCHEMA=false` por defecto.
- **MongoDB de desarrollo sin autenticación** (MS02-13, riesgo aceptado): solo en `compose.yaml`
  (loopback + red interna, datos de prueba). Producción usa replica set de 3 nodos con keyfile,
  usuarios por base y TLS.
- **Sin `latest` ni tags flotantes** (PLAT-13/14): si `images.env` conserva `sha256:REPLACE_ME`, el
  `pull` falla a propósito; ejecuta `pin-images.sh` tras cada release.

## 9. Decisiones de despliegue (P3-8 y P3-11, firmadas el 2026-11-06)

- **Quién termina TLS (P3-11, opción C)**: el balanceador o proxy de la infraestructura, no este
  stack. El servicio `gateway` es el punto único de entrada y admite dos modos:
  - TLS en el propio gateway (`TLS_SELF_SIGNED=false` y secretos `gateway_tls_cert` /
    `gateway_tls_key` montados en `/etc/nginx/certs`), o
  - HTTP detrás del balanceador (el balanceador termina TLS, inyecta `X-Forwarded-Proto: https` y
    redirige 80→443). En este modo el gateway **también** redirige (PLAT-03: `deploy/gateway/prod/gateway.conf`
    montado en el compose), así que un cliente que llegue por HTTP nunca recibe la API en claro; el
    doble redirect del balanceador y del gateway es inocuo.
- **Auth entre servicios (P3-8, opción A)**: los servicios se autentican con el JWT del usuario y la
  red privada `smxplore-stack`; solo el gateway publica 80/443 y las 808x quedan en 127.0.0.1 para
  operación. No hay `client_credentials` en esta entrega: si se agrega, ms-01 emite un token de
  servicio y cada resource server lo declara como emisor permitido.
- **Pagos (P3-7)**: no se procesan pagos de ningún tipo. Las reservas nacen `CONFIRMED`;
  `paymentMethod` es solo una preferencia declarativa.
- **Catálogo semilla (P3-10)**: `CATALOG_SEED_ENABLED=false` por defecto en producción; se enciende
  solo en una instalación nueva y se apaga después del primer arranque.

## 10. Kafka: SASL/SCRAM-SHA-512, TLS y ACLs (PLAT-01)

El broker ya no acepta conexiones anónimas: los listeners `INTERNAL` y `CONTROLLER` son `SASL_SSL`
con `SCRAM-SHA-512` obligatorio, autorizador `StandardAuthorizer`
(`allow.everyone.if.no.acl.found=false`) y `super.users=User:<KAFKA_ADMIN_USERNAME>`. El arranque
tiene tres one-shot antes de las aplicaciones:

| Servicio | Cuándo | Qué hace |
| --- | --- | --- |
| `kafka-format` | antes del broker | formatea `kafka_data` (`kafka-storage.sh format`) y siembra el usuario admin SCRAM con `--add-scram` |
| `kafka` | tras `kafka-format` con éxito | broker SASL_SSL; healthcheck autenticado con client.properties temporal |
| `kafka-init` | tras `kafka` sano | `render-client-config.sh` → `create-acls.sh` (usuarios + ACLs) → `create-topics.sh` |

Las apps declaran `depends_on: kafka-init: service_completed_successfully`: si el aprovisionamiento
falla, no arrancan (fail-closed).

### Material TLS

`deploy/kafka/gen-tls.sh` crea una CA propia (RSA 4096, 10 años) y un certificado de servidor
CN=`kafka` con SAN `DNS:kafka,DNS:localhost,IP:127.0.0.1` — `kafka` es el nombre que usan todos los
clientes dentro de `smxplore-prod`. Genera `kafka-ca.crt` (PEM, para kafka-exporter), 
`kafka.keystore.p12` (broker) y `kafka.truststore.p12` (clientes). Cópialos a
`deploy/prod/secrets/kafka/` (sección 3) y guarda los dos passwords en `.env.prod`; Compose monta el
keystore/truststore en `/etc/kafka/secrets` del broker y el truststore en
`/run/secrets/kafka_truststore` de las apps.

### Matriz de permisos (mínimo privilegio)

Los usuarios los crea `create-acls.sh`, que también aplica las ACLs. Ningún servicio usa el usuario
admin: solo `kafka-init` y el healthcheck del broker.

| Usuario | Produce | Consume | Grupo |
| --- | --- | --- | --- |
| `ms-02` | `security.report.changed` | — | — |
| `ms-03` | `activities.*`, `bookings.reservation.changed`, `reviews.review.changed`, `catalog.site.changed.DLT` | `catalog.site.changed` | `ms-03-site-events` |
| `ms-04` | `catalog.*`, `bookings.reservation.changed`, `reviews.review.changed` | `bookings.reservation.changed` | `ms-04-notifications` |
| `ms-05` | los 7 `<topic>.dlq` | los 7 tópicos de negocio | `ms-05-recommendations` |
| `dlq-replay` | los 8 tópicos de negocio | DLQ/DLT | `kafka-dlq-replay` |
| `kafka-exporter` | — | `Describe` en tópicos, grupos y clúster | — |

Todos los productores reciben además `IdempotentWrite` sobre el clúster (ACL `--cluster`).
`scripts/kafka-dlq-replay.sh` acepta `--command-config` y `--group`; usa siempre el usuario
`dlq-replay`, nunca el de un servicio.

### Rotación

- **Password SCRAM de un servicio**: cambia `KAFKA_MS0N_PASSWORD` en `.env.prod`, ejecuta
  `create-acls.sh` (re-aplica `--alter --add-config`) y recrea solo ese servicio.
- **Password admin**: cámbialo, re-aplica `create-acls.sh`, recrea `kafka` y el healthcheck lo toma
  del entorno; `kafka-format` no se re-ejecuta (es idempotente con `--ignore-formatted`).
- **Material TLS**: regenera con `gen-tls.sh --force` (rota también la CA), copia a
  `deploy/prod/secrets/kafka/` y recrea `kafka` y todos los clientes en la misma ventana: un cliente
  con el truststore viejo no valida el certificado nuevo. Sin cortes, rota con una CA nueva en
  paralelo y un truststore que contenga ambas, y retira la vieja después.
- **`kafka_data` existente**: no se reformatea; si se pierde el usuario admin, recréalo con
  `create-acls.sh` usando una sesión admin todavía válida.

## 11. Correcciones MEDIA de plataforma (fase 2 de seguridad)

### PLAT-03 — TLS obligatorio y superficie publicada

`compose.yaml` monta `deploy/gateway/prod/gateway.conf` **sobre `/etc/nginx/conf.d/default.conf`** en
lugar de montar el directorio `conf.d/` de desarrollo (así también desaparece el `default.conf` de la
imagen si existiera, que si no sería el virtual host por defecto en el puerto HTTP). Desde PLAT-12 el
contenedor escucha en **8080/8443** (usuario `nginx`); el host publica 80→8080 y 443→8443:

- `8443` sirve `routes.conf` (toda la API) con TLS 1.2/1.3 y HSTS (`max-age=31536000`).
- `8080` **no** publica la API: solo conserva `location = /healthz` (healthcheck del contenedor) y
  responde `308 https://$host$request_uri` a cualquier otra ruta.

El gateway sigue escuchando en `0.0.0.0` porque es el único borde público; las cinco aplicaciones se
publican en `127.0.0.1:${MS0X_PORT}` (operación) y la infraestructura (PostgreSQL, MongoDB, Kafka,
Redis, Neo4j, exporters, Prometheus) no publica ningún puerto. Si hay un balanceador por delante que
ya redirige, se puede montar `conf.d/gateway.conf` otra vez (ver sección 9) sin efectos de seguridad:
el 308 es idempotente.

### PLAT-05 — Roles de PostgreSQL por servicio y TLS documentado

`deploy/postgres/01-databases.sh` crea un rol LOGIN por servicio:

| Base | Rol dueño | Uso |
| --- | --- | --- |
| `smxplore_users` | `ms01_app` | ms-01 (migra con Flyway con este mismo rol) |
| `activities_events` | `ms03_flyway` (DDL) / `ms03_app` (DML) | ms-03 |
| `smxplore` | `ms04_app` | ms-04 |

`POSTGRES_USER`/`POSTGRES_PASSWORD` quedan como cuenta administrativa del clúster (initdb, backups y
`postgres-exporter`); ningún servicio la usa. Además se ejecuta
`REVOKE ALL ON DATABASE <base> FROM PUBLIC` para que un rol LOGIN cualquiera no pueda conectarse a
bases ajenas. Las contraseñas se declaran con `:?` en el compose: `MS01_APP_PASSWORD`,
`MS03_APP_PASSWORD`, `MS03_FLYWAY_PASSWORD`, `MS04_APP_PASSWORD`.

**TLS opcional (fuerte).** El contenedor de PostgreSQL de esta entrega no trae certificados, pero los
servicios aceptan activarlo sin editar el compose. Con un `postgres` con TLS (`ssl=on`,
`ssl_cert_file`, `ssl_key_file` montados y `POSTGRES_INITDB_ARGS`/`postgresql.conf` equivalente),
basta con:

```bash
POSTGRES_R2DBC_QUERY="?sslMode=verify-full"   # r2dbc-postgresql
POSTGRES_JDBC_QUERY="?sslmode=verify-full"    # JDBC (Flyway)
POSTGRES_SSLMODE=verify-full                  # postgres-exporter
```

`POSTGRES_R2DBC_QUERY`/`POSTGRES_JDBC_QUERY` se concatenan a las URLs de los servicios; vacías (por
defecto) el tráfico sigue en claro dentro de la red `smxplore-prod`. Las bases existentes no se
migran solas: para un volumen ya creado hay que ejecutar el `CREATE ROLE`/`GRANT`/`REVOKE` del script
a mano y reiniciar los contenedores con las nuevas credenciales.

### PLAT-06 — Datos personales y retención en Kafka

`deploy/kafka/topics.env` documenta qué payloads llevan datos personales y por qué cada retención es
la que es:

| Tópico | Dato personal | Retención |
| --- | --- | --- |
| `security.report.changed` | `reporterId`, `title`, `latitude`, `longitude` | **3 días** (recortada desde 7; no tiene consumidor) |
| `bookings.reservation.changed` | `touristId`, `guideId`, `managerId`, `providerId` | 7 días |
| `reviews.review.changed` | `touristId`, `response.text` | 7 días |
| `catalog.*`, `activities.*` | solo `managerId` (identificador interno) | 7 días |
| DLQ / DLT | mismos campos que su tópico | 14 días |

Reducir el contenido (quitar `title`/`latitude`/`longitude` de los reportes o no publicar
`touristId` a proyecciones que no lo necesitan) exige una versión nueva de contrato en `contracts/`
y a los consumidores preparados: queda como recomendación para el equipo de contratos, no se cambió
ningún esquema en esta fase.

`create-topics.sh` usa `--if-not-exists`: en un clúster que ya existe la retención nueva **no** se
aplica sola. Para alinear los tópicos ya creados:

```bash
docker compose --env-file .env.prod --env-file images.env -f deploy/prod/compose.yaml \
  run --rm --no-deps kafka-init bash -lc '
    /scripts/render-client-config.sh --out /tmp/admin.properties &&
    kafka-configs.sh --bootstrap-server kafka:9092 --command-config /tmp/admin.properties \
      --entity-type topics --entity-name security.report.changed \
      --alter --add-config retention.ms=259200000'
```

### PLAT-07 — Backups cifrados, firmados y con permisos restrictivos

`deploy/backup/backup.sh` ahora:

- cifra cada volcado con **age** (por defecto) o gpg y **borra el original**:
  `postgres/<base>.dump.age`, `postgres/globals.sql.age`, `mongo/mongo.archive.gz.age`,
  `neo4j/neo4j.dump.age`;
- firma `checksums.sha256` con HMAC-SHA256 (`checksums.sha256.hmac`) cuando hay
  `BACKUP_HMAC_KEY_FILE` — el SHA-256 solo prueba integridad, el HMAC prueba autenticidad;
- crea el directorio con `umask 077`, `0700` en el árbol y `chmod -R go-rwx` al terminar; el dump de
  Neo4j ya no necesita un directorio `0777` (se usa `chmod 0770` y el contenedor efímero corre como
  `7474:0`).

Variables: `BACKUP_ENCRYPTION` (`age|gpg|none`), `AGE_RECIPIENT` o `AGE_RECIPIENTS_FILE`,
`BACKUP_HMAC_KEY_FILE`; `BACKUP_ENCRYPTION=none` exige además `ALLOW_PLAINTEXT_BACKUPS=1`.

`restore.sh` y `verify-backup.sh` descifran a un directorio temporal `0700` que borran al salir
(`AGE_IDENTITY_FILE`, `GPG_PASSPHRASE_FILE`), verifican el HMAC si existe, y
`--skip-checksums` exige `ALLOW_UNVERIFIED=1`. **Guarda `AGE_IDENTITY_FILE` y la clave HMAC fuera del
host de backups**: sin ellas un backup cifrado es irrecuperable y sin HMAC no se detecta una
alteración.

### PLAT-08 — UIs de desarrollo

`kafka-ui` (raíz e `infrastructure/compose.yaml`) exige `AUTH_TYPE=LOGIN_FORM` con
`KAFKA_UI_USER`/`KAFKA_UI_PASSWORD`; `mailpit` exige basic auth en la UI
(`MAILPIT_UI_AUTH`, formato `usuario:password`). Ambos siguen en `127.0.0.1` y **no están en
`deploy/prod/compose.yaml`**. `scripts/smoke.sh`/`smoke.ps1` aceptan `401` como prueba de que la UI
está arriba y protegida. Cambia los valores por defecto en `.env` si el host es compartido.

### PLAT-10 — Límites de conexión y tiempos en el gateway

`nginx.conf` fija `keepalive_timeout 30s`, `keepalive_requests 1000`, `client_header_timeout 10s`,
`send_timeout 30s` y `reset_timedout_connection on`, define la zona `conn_per_ip` (10m) y
`limit_conn_status 429`; los dos servers aplican `limit_conn conn_per_ip 20`. El 429 usa el mismo
`application/problem+json` con `Retry-After` que el rate limiting por petición. Con
HTTP/2 una conexión multiplexa muchas peticiones: el límite de 20 conexiones es holgado para clientes
legítimos y corta el agotamiento de `worker_connections` (1024).

### Pruebas de regresión de la fase 2

```bash
# PLAT-03: por HTTP solo healthz y redirect; la API exige HTTPS
curl -s -o /dev/null -w '%{http_code}\n' http://HOST/healthz                     # 200
curl -s -o /dev/null -w '%{http_code} %{redirect_url}\n' http://HOST/api/v1/activities  # 308 https://...
curl -s -o /dev/null -w '%{http_code}\n' -k https://HOST/api/v1/activities       # 200

# PLAT-05: cada servicio conecta con su rol, no con el superusuario
docker compose -f deploy/prod/compose.yaml exec postgres \
  psql -U smxplore -d postgres -c "\du" | grep -E 'ms01_app|ms03_app|ms03_flyway|ms04_app'
# y desde ms-01: SELECT current_user;  -> ms01_app

# PLAT-06: retención efectiva
docker compose -f deploy/prod/compose.yaml exec kafka \
  kafka-configs.sh --bootstrap-server localhost:9092 --command-config /tmp/admin.properties \
  --entity-type topics --entity-name security.report.changed --describe   # retention.ms=259200000

# PLAT-07: un backup se verifica y se restaura cifrado
BACKUP_ENCRYPTION=age AGE_RECIPIENT=age1... bash deploy/backup/backup.sh
AGE_IDENTITY_FILE=... bash deploy/backup/verify-backup.sh --backup deploy/backups/<ts>
AGE_IDENTITY_FILE=... bash deploy/backup/restore.sh --backup deploy/backups/<ts> --yes

# PLAT-08: las UIs de desarrollo piden credenciales
curl -s -o /dev/null -w '%{http_code}\n' http://127.0.0.1:8090/    # 401
curl -s -o /dev/null -w '%{http_code}\n' http://127.0.0.1:8025/    # 401

# PLAT-10: 429 de conexión con 21 conexiones simultáneas
seq 1 21 | xargs -P21 -I{} curl -sk -o /dev/null -w '%{http_code}\n' https://HOST/api/v1/activities
```

---

## 12. Correcciones BAJA de plataforma (fase 3 de seguridad)

Riesgos aceptados y pendientes reales, en un solo lugar (PLAT-19): §8 es la lista viva de limitaciones
bloqueantes; esta sección describe qué cambió y cómo verificarlo.

### PLAT-11 — credenciales fuera de la línea de comandos

Visible en `ps`/`/proc/<pid>/cmdline` dentro del contenedor y en los logs de arranque.

- `deploy/mongo/init-replica-auth.sh` ya no pasa `--username/--password` a `mongosh` ni a
  `mongodump`: autentica con `db.getSiblingDB("admin").auth(process.env.MONGO_ROOT_USER,
  process.env.MONGO_ROOT_PASSWORD)` desde variables de entorno del contenedor. Los `eval` del bloque
  de validación se sustituyeron por `printenv` + `validate_chars()` (solo `[A-Za-z0-9_.-]`).
- `deploy/backup/{backup,restore,verify-backup}.sh` escriben un `mongodump`/`mongorestore`
  `--config` en YAML dentro del contenedor vía stdin (`umask 077`, borrado al terminar) y usan
  `export PGPASSWORD` + `docker exec -e PGPASSWORD` en vez de `-e PGPASSWORD=...`.
- `deploy/kafka/render-client-config.sh`: `--password <pass>` desapareció; el password SCRAM entra
  por `KAFKA_CLIENT_PASSWORD` o `--password-stdin`.
- `deploy/persistence/INDICES.md`: el ejemplo con password literal usa `read -rsp` + `mongosh --eval`.

### PLAT-12 — gateway sin privilegios

`nginxinc/nginx-unprivileged:1.27-alpine`, `USER nginx`, puertos **8080/8443**, `pid` y temporales en
`/tmp`, logs a stdout/stderr, certificados en `tmpfs` (`/etc/nginx/certs:mode=1777`) y, en producción,
`read_only: true` con `tmpfs: /tmp`. `entrypoint.sh` falla con mensaje claro si el directorio de
certificados no es escribible. Detalle en `deploy/gateway/README.md` §12.

### PLAT-13 — imágenes pineadas por digest

- `deploy/prod/pin-images.sh` pinea ahora **también** las imágenes de infraestructura, exporters y
  gateway (`POSTGRES_IMAGE`, `MONGO_IMAGE`, `REDIS_IMAGE`, `NEO4J_IMAGE`, `KAFKA_IMAGE`,
  `KAFKA_EXPORTER_IMAGE`, `POSTGRES_EXPORTER_IMAGE`, `REDIS_EXPORTER_IMAGE`, `PROMETHEUS_IMAGE`,
  `GATEWAY_IMAGE`) en `deploy/prod/images.env`; `--apps-only` conserva el comportamiento P1-5.
- `compose.yaml` (raíz) usa `${POSTGRES_IMAGE:-postgres:17-alpine}`, `${MONGO_IMAGE:-mongo:8.2}` y
  `${NEO4J_IMAGE:-neo4j:5.26-community}` (antes `${*_VERSION}`), de modo que un digest se puede
  fijar por entorno también en desarrollo.
- `NEO4J_INITIALIZE_SCHEMA` (prod) pasa a `${NEO4J_INITIALIZE_SCHEMA:-false}`: el DDL de esquema se
  ejecuta solo como paso explícito de bootstrap (ver MS05-08).
- **Pendiente de otro dueño:** las imágenes base de los servicios
  (`eclipse-temurin:25-jdk`/`25-jre`) y el `apt-get upgrade -y` de sus `Dockerfile` (microservices/).

### PLAT-14 — release sin etiquetas flotantes ni artefactos sin firmar

`release-images.yml` publica solo `<sha>` y `<versión>`, con `provenance: mode=max` y `sbom: true`.
Ya no se publica `latest`.

### PLAT-15 — `JAVA_OPTS` citado en el entrypoint de los servicios

Fuera de alcance de plataforma (microservices/): el `ENTRYPOINT ["sh","-c","exec java $JAVA_OPTS …"]`
de ms-02..ms-05 queda para el dueño de cada servicio; aquí solo se documenta.

### PLAT-16 — cabeceras de seguridad

`snippets/security-headers.conf` (`nosniff`, `X-Frame-Options: DENY`, `Referrer-Policy:
no-referrer`, CSP `default-src 'none'`, `Permissions-Policy`, `Cache-Control: no-store`) se incluye
en los servers de desarrollo y producción y también en las respuestas 503/429; HSTS solo en el server
TLS de producción. `nginx.conf` oculta `Server` y `X-Powered-By` del upstream.

### PLAT-17 — administrador inicial apagado por defecto

`BOOTSTRAP_ADMIN_ENABLED` pasa a `${BOOTSTRAP_ADMIN_ENABLED:-false}` y email/password a vacío por
defecto; `.env.prod.example` trae `false`. Alta del primer admin como paso explícito:

```bash
# 1) en .env.prod: BOOTSTRAP_ADMIN_ENABLED=true, EMAIL y PASSWORD (openssl rand -base64 24)
docker compose --env-file deploy/prod/.env.prod --env-file deploy/prod/images.env \
  -f deploy/prod/compose.yaml up -d ms-01
docker compose -f deploy/prod/compose.yaml logs ms-01 | grep -i "administrador inicial"
# 2) volver a false en .env.prod y recrear ms-01
```

Si `enabled=true` y faltan email/password, ms-01 arranca igual y omite el alta con un warning
(`BootstrapAdminRunner`).

### PLAT-18 — procedencia del backup

`deploy/backup/restore.sh` compara el `compose_project` del `manifest.txt` con el proyecto activo y
aborta si no coincide (salvo `ALLOW_OTHER_PROJECT=1`); avisa si el `host` difiere. El HMAC
(`checksums.sha256.hmac`, `BACKUP_HMAC_KEY_FILE`) y `--skip-checksums` con `ALLOW_UNVERIFIED=1`
vienen de la fase MEDIA.

### PLAT-19 — documentación de despliegue alineada

- Este README y `deploy/gateway/README.md` describen el estado real: gateway con TLS y redirect,
  Kafka con SASL_SSL, imágenes pineadas, bootstrap admin apagado.
- §8 "Limitaciones conocidas" es la lista de pendientes bloqueantes; cualquier afirmación de
  seguridad de `README.md` (raíz) debe poder rastrearse a un archivo de `deploy/`.

### MS02-13 — MongoDB de desarrollo sin autenticación (riesgo aceptado)

`compose.yaml` mantiene MongoDB sin auth **a propósito**: solo escucha en `127.0.0.1` y en la red
interna `smxplore-stack`; no hay datos reales y ms-02/ms-04 necesitan el replica set para las
transacciones del outbox. No exponer ese puerto fuera del host. Producción usa replica set de tres
nodos con `--auth`, keyfile, usuarios por base y TLS (`deploy/prod/compose.yaml`).

### MS05-08 — Neo4j: usuario único y DDL opt-in

Neo4j Community (la del stack) solo tiene el usuario `neo4j`; el RBAC multiusuario es Enterprise, así
que no se puede crear un "usuario de aplicación" separado. Medidas aplicadas: contraseña obligatoria
(`${NEO4J_PASSWORD:?}`), sin puertos publicados (solo red interna), `NEO4J_INITIALIZE_SCHEMA=false`
por defecto en producción (el DDL se ejecuta una vez de forma explícita y luego se apaga) y
`neo4j` como usuario de solo-aplicación en documentación. Queda como limitación aceptada.

### Pruebas de regresión de la fase 3

```bash
# PLAT-13: images.env tiene digests para infra y gateway
grep -c '@sha256:' deploy/prod/images.env            # 5 con --apps-only; 15 completo

# PLAT-17: bootstrap apagado salvo que se pida
grep -n 'BOOTSTRAP_ADMIN_ENABLED' deploy/prod/compose.yaml deploy/prod/.env.prod.example

# PLAT-11: sin credenciales en argv (sin Docker)
grep -n -- '--password' deploy/mongo/init-replica-auth.sh deploy/backup/*.sh

# PLAT-12: puertos no privilegiados y usuario nginx
grep -n 'listen \|USER nginx' deploy/gateway/conf.d/gateway.conf deploy/gateway/Dockerfile

# PLAT-16: cabeceras en respuestas de error (con el stack arriba)
curl.exe -s -D - -o NUL http://127.0.0.1:8088/api/v1/activities/0 | Select-String "X-Frame-Options"

# MS05-08: sin DDL implícito
grep -n 'NEO4J_INITIALIZE_SCHEMA' deploy/prod/compose.yaml deploy/prod/.env.prod.example
```

Variables nuevas que deben existir en `.env.prod`: `NEO4J_INITIALIZE_SCHEMA` (opcional, `false`).
Variables de la integracion MS04-11 / PLAT-19 (tabla completa en §4): `NOTIFICATIONS_DLQ_TOPIC`
(topic `kafka.dlq.bookings.reservation.changed`, creado por `deploy/kafka/topics.env`; ACL de
escritura para ms-04 y lectura para `dlq-replay` en `deploy/kafka/create-acls.sh`),
`REVIEW_REQUIRE_COMPLETED_BOOKING` (`true`), `REVIEW_AI_FAIL_OPEN` (`false`), `HSTS_ENABLED`
(`true` en ms-02), y en desarrollo `MAILPIT_UI_AUTH`, `KAFKA_UI_USER`, `KAFKA_UI_PASSWORD`. Para
reproducir el DLQ: `bash scripts/kafka-dlq-replay.sh --dlq kafka.dlq.bookings.reservation.changed
--target bookings.reservation.changed --command-config <admin>.properties` (el nombre no permite deducir
el destino quitando el sufijo).
Variables opcionales para desarrollo/reproducibilidad: `POSTGRES_IMAGE`, `MONGO_IMAGE`, `NEO4J_IMAGE`
(en `.env.example`/entorno local; por defecto mantienen los tags actuales).

## 13. Metricas de los servicios endurecidos (fase BAJA)

Desde el endurecimiento de seguridad, `/actuator/metrics` y `/actuator/prometheus` ya no
son anonimos fuera de los perfiles de desarrollo:

- `ms-01`: solo `health` e `info` publicos (`ACTUATOR_PUBLIC_METRICS=true` los abre, uso
  previsto para una red de gestion aislada).
- `ms-02`, `ms-03`, `ms-05`: exigen rol `ADMIN` y su exposicion viene apagada por defecto
  (`MANAGEMENT_EXPOSED_ENDPOINTS`, `MANAGEMENT_EXPOSURE`, `MANAGEMENT_ENDPOINTS`).
- `ms-04`: mantiene el actuator publico dentro de la red de Compose.

Consecuencia practica: el `prometheus.yml` de `deploy/observability/` seguira recibiendo
401/403 de `ms-01`, `ms-02`, `ms-03` y `ms-05` hasta que se elija una de estas opciones
(pendiente, no bloquea esta entrega porque el stack de produccion no se despliega aqui):

1. Publicar dichas metricas con un flag de operacion y aislarlas por red (puerto de
   gestion interno, sin publicar al host).
2. Scrapear con un token de operacion emitido por `ms-01` (rol de solo lectura).
3. Desplegar un sidecar/agente por servicio que exponga las metricas sin abrir el
   actuator.

Las metricas de infraestructura (kafka-exporter, postgres-exporter, redis-exporter,
Neo4j) no cambian y siguen disponibles.
## 14. Correcciones INFORMATIVAS de plataforma (fase 4 de seguridad)

Estos cuatro hallazgos no eran explotables por si mismos, pero ampliaban superficie (dependencias sin
vigilar, logs con datos sensibles en artefactos, contexto de build de mas y credenciales de desarrollo
implicitas). Se corrigen para no dejar controles "a medias".

### PLAT-20 - Dependabot cubre los cinco servicios (.github/dependabot.yml)

- Antes solo se vigilaba ms-01 (Gradle y Docker) mas github-actions.
- Ahora hay cinco entradas gradle (una por servicio, con grupos spring y 	estcontainers),
  cinco entradas docker para los Dockerfile de servicio, docker para deploy/gateway,
  /, /infrastructure y /deploy/prod (tags de los compose) y github-actions en /.
- Las dependencias de produccion (imagenes pineadas por digest) siguen fuera del alcance de
  Dependabot a proposito: se actualizan con pin-images.sh y revision manual.

### PLAT-21 - Los artefactos de CI no suben logs completos del stack (.github/workflows/stack-ci.yml)

- El paso "Estado del stack si algo fallo" sigue volcando docker compose ps, pero el log se
  reduce a --tail 5000 lineas y se filtra con
  grep -viE 'password|passwd|token|authorization|cookie|set-cookie|secret|bearer|api[_-]?key|reset'
  antes de escribirlo en stack-logs/compose.log.
- El upload-artifact del stack pasa a etention-days: 3 y el paso emite un ::warning en el
  resumen del job avisando de que el log esta filtrado (puede faltar contexto).
- Riesgo aceptado: un log filtrado puede ocultar la causa de un fallo. Para depurar en local se usa
  docker compose logs sin filtro; en CI se prefiere no publicar credenciales de prueba.

### PLAT-22 - .dockerignore completo en ms-02 y ms-03

- Ambos servicios no excluian docs/ ni las claves de test, mientras que las capas del build si se
  cachean en GitHub Actions (	ype=gha).
- Ahora .dockerignore excluye .gradle, uild, out, .git, .gitignore, .idea, *.iml,
  .env, README.md, docs, src/test/**/keys y src/test/**/*.pem.
- Verificado contra el Dockerfile: el build solo necesita gradlew, settings.gradle.kts,
  uild.gradle.kts, gradle/ y src/main (ootJar), asi que la imagen no cambia.

### PLAT-23 - Credenciales de desarrollo explicitas y rotables

- loadtests/run.sh ya no tiene valor por defecto para ADMIN_PASSWORD: falla con exit 2 y un
  mensaje accionable si falta. Los k6 (catalog.js, lows.js, ecommendations.js) validan
  ADMIN_PASSWORD en setup() y abortan con error claro.
- loadtests/README.md documenta que las pruebas son solo para el stack local, que la credencial
  viaja con -e (visible en docker inspect) y que el turista efimero usa PerfTest12345
  (sobreescribible con TOURIST_PASSWORD).
- README.md (raiz) deja claro que las credenciales de desarrollo no son secretos, se rotan por
  .env y no se reutilizan; y que el volumen ms01_data contiene la pareja RSA del perfil dev
  (secreto de desarrollo: no copiar a backups, borrarlo invalida los tokens).
- Riesgo aceptado: el password se pasa al contenedor de k6 con -e; para el stack local es
  aceptable y queda documentado.

### Pruebas de regresion de la fase 4

| Control | Como comprobarlo |
|---|---|
| PLAT-20 | Revisar el PR semanal de Dependabot: aparecen 5 grupos Gradle y 5 de Docker de servicio. |
| PLAT-21 | Forzar un fallo del smoke en CI y descargar el artefacto: solo 3 dias de retencion y sin lineas con `token`/`password`. |
| PLAT-22 | `docker build microservices/ms-02-security-reports` y `.../ms-03-activities-and-events` compilan; `tar -cf - --exclude-from=.dockerignore <ctx>` no incluye `docs` ni `src/test/**/keys`. |
| PLAT-23 | ./loadtests/run.sh recommendations sin ADMIN_PASSWORD termina en exit 2; con la variable definida arranca k6. |