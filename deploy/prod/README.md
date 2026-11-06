# Despliegue de producción de SM-Xplore (P1-5)

Stack de producción en un solo host con Docker Compose. Cubre build/publicación de las cinco
imágenes, pineado por digest, secretos, arranque, verificación y rollback.

**Fuera de alcance (diferido):**
- **Gateway y TLS: P3-11.** Este compose NO termina TLS ni incluye proxy. Se asume un proxy
  externo (Nginx, Traefik, ALB…) que termina TLS y reenvía a los puertos publicados en
  `127.0.0.1` de este host. Por eso `SECURITY_HSTS_ENABLED=true` y `server.forward-headers-strategy`
  (perfil prod) esperan las cabeceras `X-Forwarded-*` del proxy.
- **Tópicos y alertas:** `deploy/kafka/create-topics.sh`, `deploy/kafka/topics.env`,
  `deploy/observability/prometheus.yml` y `deploy/observability/alerts.yml` los mantiene P1-6.
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

## Servicios y puertos

| Servicio | Puerto interno | Puerto host (`127.0.0.1`) | Salud |
| --- | --- | --- | --- |
| ms-01-users-and-anomalies | 8081 | `${MS01_PORT}` = 8081 | `/actuator/health` |
| ms-02-security-reports | 8080 | `${MS02_PORT}` = 8080 | `/actuator/health` |
| ms-03-activities-and-events | 8082 | `${MS03_PORT}` = 8082 | `/actuator/health` |
| ms-04-sites-and-services | 8080 | `${MS04_PORT}` = 8084 | `/actuator/health` |
| ms-05-recommendations | 8080 | `${MS05_PORT}` = 8085 | `/actuator/health` |
| postgres / mongo1-3 / kafka / redis / neo4j / prometheus | internos | **ninguno** | healthcheck interno |

Ninguna base de datos, broker, caché, Prometheus ni exporter publica puertos: solo se exponen los
`808x` de las aplicaciones, atados a loopback para que el proxy externo sea el único ingreso.

Orden de arranque: Postgres, Mongo (3 nodos) y Kafka sanos → `mongo-init` (replica set + usuarios)
→ `ms-01` (crea el admin inicial y firma JWT) → `ms-04` → `ms-02`/`ms-03`/`ms-05`.
`kafka-init` corre una vez que Kafka está sano y crea los tópicos.

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

# Clave AES para cifrar los secretos TOTP.
openssl rand -base64 32        # valor de MFA_SECRET_KEY en .env.prod
```

Copia el keyfile y los certificados a `deploy/prod/secrets/` o apunta `MONGO_KEYFILE_FILE`,
`MONGO_CA_FILE` y `MONGO_SERVER_CERT_FILE` a donde estén. Las rutas de `secrets.file` se resuelven
relativas a este `compose.yaml`. Los archivos deben existir antes de `docker compose up`.
Nada de esto se versiona.

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

# Tópicos creados por kafka-init.
docker compose -f deploy/prod/compose.yaml exec kafka \
  /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list

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

## 9. Decisiones de despliegue (P3-8 y P3-11, firmadas el 2026-11-06)

- **Quién termina TLS (P3-11, opción C)**: el balanceador o proxy de la infraestructura, no este
  stack. El servicio `gateway` es el punto único de entrada y admite dos modos:
  - TLS en el propio gateway (`TLS_SELF_SIGNED=false` y secretos `gateway_tls_cert` /
    `gateway_tls_key` montados en `/etc/nginx/certs`), o
  - HTTP detrás del balanceador (el balanceador termina TLS, inyecta `X-Forwarded-Proto: https` y
    redirige 80→443). En este modo el gateway no redirige para no duplicar el `listen`; si se
    quiere forzar HTTPS dentro del stack, se agrega un `conf.d/00-redirect.conf` como explica
    `deploy/gateway/README.md`.
- **Auth entre servicios (P3-8, opción A)**: los servicios se autentican con el JWT del usuario y la
  red privada `smxplore-stack`; solo el gateway publica 80/443 y las 808x quedan en 127.0.0.1 para
  operación. No hay `client_credentials` en esta entrega: si se agrega, ms-01 emite un token de
  servicio y cada resource server lo declara como emisor permitido.
- **Pagos (P3-7)**: no se procesan pagos de ningún tipo. Las reservas nacen `CONFIRMED`;
  `paymentMethod` es solo una preferencia declarativa.
- **Catálogo semilla (P3-10)**: `CATALOG_SEED_ENABLED=false` por defecto en producción; se enciende
  solo en una instalación nueva y se apaga después del primer arranque.
