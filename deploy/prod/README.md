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
| gateway | 80 / 443 | `${GATEWAY_HTTP_PORT}` = 80 y `${GATEWAY_HTTPS_PORT}` = 443, **en todas las interfaces** | `/healthz` en `:80` |
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
`ghcr.io/ivandresp/sm-xplore-<servicio>` con tres etiquetas: `<sha>`, `<versión>` y `latest`. El
resumen del job imprime el digest de cada imagen. Los PR no publican nada. También se puede lanzar
a mano (`workflow_dispatch`) indicando la versión.

## 2. Pinear los digests

```bash
docker login ghcr.io
deploy/prod/pin-images.sh v1.2.3
```

`images.env` queda con las cinco referencias `repo@sha256:...`. El script es idempotente, falla si
alguna imagen no existe y no toca nada hasta haber resuelto los cinco digests.

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
imagen `nginx:1.27-alpine`, que si no sería el virtual host por defecto en `:80`):

- `:443` sirve `routes.conf` (toda la API) con TLS 1.2/1.3 y HSTS (`max-age=31536000`).
- `:80` **no** publica la API: solo conserva `location = /healthz` (healthcheck del contenedor) y
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
