#!/bin/sh
# ---------------------------------------------------------------------------
# P1-7 - Inicializa el replica set MongoDB autenticado de produccion.
#
# Este script lo ejecuta el servicio one-shot `mongo-init` de
# deploy/prod/compose.yaml como:
#
#   entrypoint: ["/bin/sh", "-c"]
#   command: exec sh /scripts/init-replica-auth.sh
#
# Por eso es POSIX sh (dash), no bash, y no usa arrays ni `local`.
#
# Que hace (idempotente, se puede re-ejecutar):
#   1. Espera a que un nodo responda.
#   2. Si el replica set no esta iniciado, ejecuta rs.initiate con los
#      miembros de MONGO_HOSTS.
#   3. Crea el usuario root en `admin` (solo si no existe).
#   4. Crea los usuarios de aplicacion readWrite en SU propia base.
#   5. Espera a que el replica set tenga todos los miembros sanos.
#   6. Valida que cada usuario de aplicacion puede autenticarse.
#
# Bootstrap del primer usuario (localhost exception):
#   Con --auth + --keyFile, MongoDB solo permite crear el PRIMER usuario desde
#   el loopback. Por eso el servicio `mongo-init` del compose de produccion
#   declara `network_mode: "service:mongo1"`: comparte el namespace de red de
#   mongo1 y puede conectar a 127.0.0.1:27017. Este script intenta 127.0.0.1
#   PRIMERO y luego cae a MONGO_HOSTS; si el modo de red no esta configurado,
#   el bootstrap remoto falla con Unauthorized (code 13) y el script termina
#   con un error explicativo.
#
# Variables de entorno (las inyecta deploy/prod/compose.yaml):
#   MONGO_REPLICA_SET      Nombre del RS (default: rs0).
#   MONGO_HOSTS            Miembros "host:puerto" separados por coma
#                          (default: mongo1:27017,mongo2:27017,mongo3:27017).
#   MONGO_TLS              "true" para conectar con TLS (default: false).
#   MONGO_CA_FILE          CA usada para validar el servidor cuando
#                          MONGO_TLS=true (path DENTRO del contenedor, p.ej.
#                          /run/secrets/mongo_ca). Los nodos usan
#                          --tlsAllowConnectionsWithoutCertificates, asi que no
#                          se necesita certificado de cliente.
#   MONGO_ROOT_USER        Usuario root en `admin` (default: root).
#   MONGO_ROOT_PASSWORD    Password del root (obligatorio).
#   MONGO_USER_MS01..MS04  Usuarios de aplicacion (MS05 no usa Mongo).
#   MONGO_PASSWORD_MS01..MS04  Passwords de esos usuarios.
#   MONGO_DB_MS01..MS04    Override opcional de la base de cada usuario
#                          (defaults: smxplore_reviews, security_reports,
#                          reviews, smxplore).
#   MONGO_CONNECT_HOST     Nodo alternativo para el bootstrap (opcional;
#                          default: probar 127.0.0.1 y luego el primer host).
#   MONGO_CONNECT_PORT     Puerto del bootstrap (default: 27017).
#   MONGO_WAIT_SECONDS     Timeout total de espera (default: 180).
#   MONGO_POLL_INTERVAL    Intervalo de polling en segundos (default: 2).
#
# Los usuarios de aplicacion se crean en su propia base con rol readWrite y se
# autentican con authSource=<su base>. Las bases desde las que conectan los
# servicios son:
#   ms-01 -> smxplore_reviews
#   ms-02 -> security_reports
#   ms-03 -> reviews
#   ms-04 -> smxplore
#
# Nota: si cambias un password en .env.prod, el script NO lo actualiza en caliente
# (solo tolera usuarios existentes). Para rotar passwords usa
# db.getSiblingDB(...).changeUserPassword(...) de forma explicita.
# ---------------------------------------------------------------------------
set -eu

log() { printf '[init-replica-auth] %s\n' "$*"; }
warn() { printf '[init-replica-auth][AVISO] %s\n' "$*" >&2; }
die() { printf '[init-replica-auth][ERROR] %s\n' "$*" >&2; exit 1; }

command -v mongosh >/dev/null 2>&1 || die "mongosh no esta disponible en el contenedor"

MONGO_REPLICA_SET="${MONGO_REPLICA_SET:-rs0}"
MONGO_HOSTS="${MONGO_HOSTS:-mongo1:27017,mongo2:27017,mongo3:27017}"
MONGO_TLS="${MONGO_TLS:-false}"
MONGO_CA_FILE="${MONGO_CA_FILE:-}"
MONGO_ROOT_USER="${MONGO_ROOT_USER:-root}"
MONGO_ROOT_PASSWORD="${MONGO_ROOT_PASSWORD:-}"
MONGO_CONNECT_HOST="${MONGO_CONNECT_HOST:-}"
MONGO_CONNECT_PORT="${MONGO_CONNECT_PORT:-27017}"
MONGO_WAIT_SECONDS="${MONGO_WAIT_SECONDS:-180}"
MONGO_POLL_INTERVAL="${MONGO_POLL_INTERVAL:-2}"

[ -n "${MONGO_ROOT_PASSWORD}" ] || die "MONGO_ROOT_PASSWORD es obligatorio"
[ -n "${MONGO_HOSTS}" ] || die "MONGO_HOSTS no puede estar vacio"

if [ "${MONGO_TLS}" = "true" ]; then
  [ -n "${MONGO_CA_FILE}" ] || die "MONGO_CA_FILE es obligatorio cuando MONGO_TLS=true"
fi

# --- Helpers de conexion ----------------------------------------------------
# mongosh es Node: leemos usuarios/passwords desde process.env dentro del JS,
# asi no hay que escapar passwords en JSON ni en la linea de comandos.

mongosh_direct() { # host port js
  if [ "${MONGO_TLS}" = "true" ]; then
    mongosh --quiet --host "$1" --port "$2" \
      --tls --tlsCAFile "${MONGO_CA_FILE}" --eval "$3"
  else
    mongosh --quiet --host "$1" --port "$2" --eval "$3"
  fi
}

mongosh_rs_root() { # js ; autenticado como root
  # serverSelectionTimeoutMS corto: si el RS aun no esta iniciado no queremos
  # esperar 30s por intento en los bucles de espera.
  if [ "${MONGO_TLS}" = "true" ]; then
    mongosh --quiet "mongodb://${MONGO_HOSTS}/?replicaSet=${MONGO_REPLICA_SET}&serverSelectionTimeoutMS=5000" \
      --username "${MONGO_ROOT_USER}" --password "${MONGO_ROOT_PASSWORD}" \
      --authenticationDatabase admin \
      --tls --tlsCAFile "${MONGO_CA_FILE}" --eval "$1"
  else
    mongosh --quiet "mongodb://${MONGO_HOSTS}/?replicaSet=${MONGO_REPLICA_SET}&serverSelectionTimeoutMS=5000" \
      --username "${MONGO_ROOT_USER}" --password "${MONGO_ROOT_PASSWORD}" \
      --authenticationDatabase admin --eval "$1"
  fi
}

wait_root_auth() { # 0 si el RS acepta autenticacion root antes del timeout
  WAIT_ELAPSED=0
  while [ "${WAIT_ELAPSED}" -lt "${MONGO_WAIT_SECONDS}" ]; do
    if mongosh_rs_root 'db.runCommand({ ping: 1 }).ok' >/dev/null 2>&1; then
      return 0
    fi
    sleep "${MONGO_POLL_INTERVAL}"
    WAIT_ELAPSED=$((WAIT_ELAPSED + MONGO_POLL_INTERVAL))
  done
  return 1
}

# --- Candidatos para el bootstrap (loopback primero) ------------------------
FIRST_HOST="$(printf '%s' "${MONGO_HOSTS}" | tr ',' ' ' | awk '{print $1}')"
FIRST_HOST_NAME="${FIRST_HOST%%:*}"
FIRST_HOST_PORT="${FIRST_HOST#*:}"
[ "${FIRST_HOST_PORT}" = "${FIRST_HOST_NAME}" ] && FIRST_HOST_PORT="${MONGO_CONNECT_PORT}"

CANDIDATES="127.0.0.1:${MONGO_CONNECT_PORT}"
if [ -n "${MONGO_CONNECT_HOST}" ]; then
  CANDIDATES="${CANDIDATES}
${MONGO_CONNECT_HOST}:${MONGO_CONNECT_PORT}"
fi
CANDIDATES="${CANDIDATES}
${FIRST_HOST_NAME}:${FIRST_HOST_PORT}"

# --- 1) Espera a un nodo alcanzable ----------------------------------------
log "esperando un nodo MongoDB (candidatos: $(printf '%s' "${CANDIDATES}" | tr '\n' ' '))"
NODE_HOST=""
NODE_PORT=""
ELAPSED=0
while [ "${ELAPSED}" -lt "${MONGO_WAIT_SECONDS}" ]; do
  for CAND in ${CANDIDATES}; do
    CAND_HOST="${CAND%%:*}"
    CAND_PORT="${CAND#*:}"
    [ "${CAND_PORT}" = "${CAND_HOST}" ] && CAND_PORT="${MONGO_CONNECT_PORT}"
    if mongosh_direct "${CAND_HOST}" "${CAND_PORT}" 'db.runCommand({ ping: 1 }).ok' >/dev/null 2>&1; then
      NODE_HOST="${CAND_HOST}"
      NODE_PORT="${CAND_PORT}"
      break
    fi
  done
  [ -n "${NODE_HOST}" ] && break
  sleep "${MONGO_POLL_INTERVAL}"
  ELAPSED=$((ELAPSED + MONGO_POLL_INTERVAL))
done
[ -n "${NODE_HOST}" ] || die "ningun nodo responde tras ${MONGO_WAIT_SECONDS}s (revisa MONGO_HOSTS/TLS/CA)"
log "nodo alcanzable: ${NODE_HOST}:${NODE_PORT}"

# --- 2) Usuarios existentes? ----------------------------------------------
# Comprobacion ACOTADA: si el replica set ya estaba inicializado y el root
# existe, esto pasa a la primera. Si no, no esperamos MONGO_WAIT_SECONDS aqui
# (cada intento contra un RS sin iniciar tarda ~5s por serverSelectionTimeoutMS)
# y pasamos directamente al bootstrap, que es idempotente.
USERS_READY=0
AUTH_ATTEMPT=0
while [ "${AUTH_ATTEMPT}" -lt 3 ]; do
  AUTH_ATTEMPT=$((AUTH_ATTEMPT + 1))
  if mongosh_rs_root 'db.runCommand({ ping: 1 }).ok' >/dev/null 2>&1; then
    USERS_READY=1
    break
  fi
  sleep "${MONGO_POLL_INTERVAL}"
done

if [ "${USERS_READY}" -eq 1 ]; then
  log "el replica set ya responde autenticado como '${MONGO_ROOT_USER}'; se omite el bootstrap"
else
  # --- 3) Bootstrap del primer usuario (loopback exception) ---------------
  log "bootstrap inicial del replica set '${MONGO_REPLICA_SET}'"

  MEMBERS=""
  IDX=0
  for HOSTPORT in $(printf '%s' "${MONGO_HOSTS}" | tr ',' ' '); do
    PRIORITY=1
    [ "${IDX}" -eq 0 ] && PRIORITY=2
    MEMBERS="${MEMBERS}${MEMBERS:+,}{\"_id\":${IDX},\"host\":\"${HOSTPORT}\",\"priority\":${PRIORITY}}"
    IDX=$((IDX + 1))
  done

  BOOTSTRAP_OK=0
  for CAND in ${CANDIDATES}; do
    CAND_HOST="${CAND%%:*}"
    CAND_PORT="${CAND#*:}"
    [ "${CAND_PORT}" = "${CAND_HOST}" ] && CAND_PORT="${MONGO_CONNECT_PORT}"

    log "intentando bootstrap en ${CAND_HOST}:${CAND_PORT}"

    # rs.initiate() esta permitido por la localhost exception (solo mientras no
    # exista ningun usuario). Reintentar initiate devuelve AlreadyInitialized
    # (code 23), que se trata como exito para que el script sea idempotente.
    INIT_JS="const cfg = { _id: \"${MONGO_REPLICA_SET}\", members: [${MEMBERS}] };
try {
  rs.initiate(cfg);
  print(\"RS_INITIATED\");
} catch (e) {
  if (e.code === 23) { print(\"RS_ALREADY_INITIALIZED\"); }
  else { print(\"RS_ERROR:\" + e.code + \":\" + e.message); quit(2); }
}"

    if ! mongosh_direct "${CAND_HOST}" "${CAND_PORT}" "${INIT_JS}"; then
      warn "no se pudo iniciar el RS via ${CAND_HOST}:${CAND_PORT}; se prueba el siguiente candidato"
      continue
    fi

    # Espera a que el nodo sea primario (o secundario con primario elegido).
    PRIMARY_OK=0
    ELAPSED=0
    while [ "${ELAPSED}" -lt "${MONGO_WAIT_SECONDS}" ]; do
      STATE="$(mongosh_direct "${CAND_HOST}" "${CAND_PORT}" 'db.hello().isWritablePrimary' 2>/dev/null || true)"
      if [ "${STATE}" = "true" ]; then
        PRIMARY_OK=1
        break
      fi
      sleep "${MONGO_POLL_INTERVAL}"
      ELAPSED=$((ELAPSED + MONGO_POLL_INTERVAL))
    done
    if [ "${PRIMARY_OK}" -ne 1 ]; then
      warn "el nodo ${CAND_HOST}:${CAND_PORT} no alcanzo el estado primario; se prueba el siguiente candidato"
      continue
    fi

    log "creando usuario root '${MONGO_ROOT_USER}' en admin"
    ROOT_JS='const admin = db.getSiblingDB("admin");
const user = process.env.MONGO_ROOT_USER;
const pass = process.env.MONGO_ROOT_PASSWORD;
if (admin.getUser(user)) {
  print("ROOT_EXISTS");
} else {
  admin.createUser({ user: user, pwd: pass, roles: [{ role: "root", db: "admin" }] });
  print("ROOT_CREATED");
}'

    if ROOT_OUT="$(mongosh_direct "${CAND_HOST}" "${CAND_PORT}" "${ROOT_JS}" 2>&1)"; then
      log "root: ${ROOT_OUT}"
      BOOTSTRAP_OK=1
      break
    fi

    if printf '%s' "${ROOT_OUT}" | grep -qE '"code": 13|code: 13|Unauthorized|requires authentication'; then
      warn "bootstrap rechazado en ${CAND_HOST}:${CAND_PORT} por la excepcion de loopback (Unauthorized);"
      warn "asegurate de que mongo-init corre con network_mode: \"service:mongo1\""
    else
      warn "fallo creando el root via ${CAND_HOST}:${CAND_PORT}: ${ROOT_OUT}"
    fi
  done

  if [ "${BOOTSTRAP_OK}" -ne 1 ]; then
    # Puede que el RS ya estuviera inicializado y el fallo fuera transitorio, o
    # que la localhost exception ya no aplique porque existe algun usuario.
    if wait_root_auth; then
      warn "no se creo un root nuevo; el replica set ya estaba inicializado y autentica"
    else
      die "no fue posible crear el usuario root; revisa network_mode de mongo-init y los secretos TLS/keyfile"
    fi
  elif ! wait_root_auth; then
    die "el replica set no acepta autenticacion root tras el bootstrap"
  fi
fi

# --- 4) Usuarios de aplicacion ---------------------------------------------
log "creando/verificando usuarios de aplicacion (idempotente)"
APP_JS='const bases = [
  { key: "MS01", db: "smxplore_reviews" },
  { key: "MS02", db: "security_reports" },
  { key: "MS03", db: "reviews" },
  { key: "MS04", db: "smxplore" }
];
for (const item of bases) {
  const dbName = process.env["MONGO_DB_" + item.key] || item.db;
  const user = process.env["MONGO_USER_" + item.key];
  const pass = process.env["MONGO_PASSWORD_" + item.key];
  if (!user && !pass) {
    print("SKIP " + item.key + " (sin MONGO_USER_" + item.key + ")");
    continue;
  }
  if (!user || !pass) {
    print("ERROR " + item.key + ": MONGO_USER_" + item.key + " y MONGO_PASSWORD_" + item.key + " deben definirse juntos");
    quit(3);
  }
  const target = db.getSiblingDB(dbName);
  if (target.getUser(user)) {
    print("EXISTS " + dbName + "/" + user);
    continue;
  }
  target.createUser({ user: user, pwd: pass, roles: [{ role: "readWrite", db: dbName }] });
  print("CREATED " + dbName + "/" + user);
}'

if ! APP_OUT="$(mongosh_rs_root "${APP_JS}" 2>&1)"; then
  die "fallo creando usuarios de aplicacion: ${APP_OUT}"
fi
printf '%s\n' "${APP_OUT}" | while IFS= read -r line; do log "usuario app: ${line}"; done

# --- 5) Espera a que todos los miembros esten sanos -------------------------
EXPECTED_MEMBERS="$(printf '%s' "${MONGO_HOSTS}" | tr ',' ' ' | wc -w | tr -d ' ')"
log "esperando ${EXPECTED_MEMBERS} miembros sanos en el replica set"
ELAPSED=0
HEALTHY=0
while [ "${ELAPSED}" -lt "${MONGO_WAIT_SECONDS}" ]; do
  MEMBER_JS='try {
  const members = rs.status().members.map((m) => m.stateStr);
  print(JSON.stringify(members));
} catch (e) {
  print("STATUS_ERROR:" + e.code);
}'
  MEMBER_STATES="$(mongosh_rs_root "${MEMBER_JS}" 2>/dev/null || true)"
  case "${MEMBER_STATES}" in
    *PRIMARY*)
      HEALTHY_COUNT="$(printf '%s' "${MEMBER_STATES}" | grep -oE 'PRIMARY|SECONDARY|ARBITER' | wc -l | tr -d ' ')"
      if [ "${HEALTHY_COUNT}" -ge "${EXPECTED_MEMBERS}" ]; then
        HEALTHY=1
        log "miembros: ${MEMBER_STATES}"
        break
      fi
      ;;
  esac
  sleep "${MONGO_POLL_INTERVAL}"
  ELAPSED=$((ELAPSED + MONGO_POLL_INTERVAL))
done
if [ "${HEALTHY}" -ne 1 ]; then
  warn "el RS no reporta ${EXPECTED_MEMBERS} miembros sanos tras ${MONGO_WAIT_SECONDS}s (ultimo estado: ${MEMBER_STATES:-desconocido})"
  warn "revisa rs.status() y la conectividad entre mongo1/mongo2/mongo3"
fi

# --- 6) Validacion de cada usuario de aplicacion ---------------------------
log "validando autenticacion de usuarios de aplicacion"
VALIDATION_FAILED=0
for N in 01 02 03 04; do
  eval "APP_USER=\${MONGO_USER_MS${N}:-}"
  eval "APP_PASS=\${MONGO_PASSWORD_MS${N}:-}"
  eval "APP_DB_OVERRIDE=\${MONGO_DB_MS${N}:-}"
  [ -n "${APP_USER}" ] || continue

  case "${N}" in
    01) APP_DB="smxplore_reviews" ;;
    02) APP_DB="security_reports" ;;
    03) APP_DB="reviews" ;;
    04) APP_DB="smxplore" ;;
    *) APP_DB="" ;;
  esac
  [ -n "${APP_DB_OVERRIDE}" ] && APP_DB="${APP_DB_OVERRIDE}"

  if [ "${MONGO_TLS}" = "true" ]; then
    AUTH_TEST="$(mongosh --quiet "mongodb://${MONGO_HOSTS}/${APP_DB}?replicaSet=${MONGO_REPLICA_SET}&serverSelectionTimeoutMS=5000" \
      --username "${APP_USER}" --password "${APP_PASS}" --authenticationDatabase "${APP_DB}" \
      --tls --tlsCAFile "${MONGO_CA_FILE}" \
      --eval 'db.runCommand({ ping: 1 }).ok' 2>&1 || true)"
  else
    AUTH_TEST="$(mongosh --quiet "mongodb://${MONGO_HOSTS}/${APP_DB}?replicaSet=${MONGO_REPLICA_SET}&serverSelectionTimeoutMS=5000" \
      --username "${APP_USER}" --password "${APP_PASS}" --authenticationDatabase "${APP_DB}" \
      --eval 'db.runCommand({ ping: 1 }).ok' 2>&1 || true)"
  fi

  if [ "${AUTH_TEST}" = "1" ]; then
    log "OK ${APP_DB}/${APP_USER}"
  else
    warn "fallo la validacion de ${APP_DB}/${APP_USER}: ${AUTH_TEST}"
    VALIDATION_FAILED=1
  fi
done

[ "${VALIDATION_FAILED}" -eq 0 ] || die "algunos usuarios de aplicacion no pudieron autenticarse"

log "replica set '${MONGO_REPLICA_SET}' listo: bootstrap, usuarios y validacion completados"
