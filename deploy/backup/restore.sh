#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# P1-7 - Restauracion de la persistencia de SM-Xplore desde un backup de
# deploy/backup/backup.sh (PostgreSQL + MongoDB + Neo4j).
#
# Uso:
#   RESTORE=yes deploy/backup/restore.sh --backup DIR [--target all|postgres|mongo|neo4j]
#
#   --backup DIR    Directorio de un backup concreto (el que contiene
#                   manifest.txt). Alternativa: pasarlo como primer argumento
#                   posicional.
#   --target T      Que restaurar: all (default), postgres, mongo o neo4j.
#   --yes           No pedir confirmacion interactiva (equivale a RESTORE=yes).
#   --skip-checksums  No validar checksums.sha256 antes de restaurar.
#
# Seguridad:
#   La restauracion SOBRESCRIBE datos (pg_restore --clean, mongorestore --drop,
#   neo4j-admin database load --overwrite-destination). Por eso exige una
#   confirmacion explicita: variable RESTORE=yes o flag --yes. Antes de
#   restaurar, PARA los servicios de aplicacion para evitar conexiones activas:
#     docker compose -p <proyecto> -f deploy/prod/compose.yaml stop ms-01 ms-02 ms-03 ms-04 ms-05
#   (en dev usa el compose de la raiz).
#
# Descubrimiento de contenedores: identico a backup.sh (labels de Compose;
# COMPOSE_PROJECT / POSTGRES_CONTAINER / MONGO_CONTAINER / NEO4J_CONTAINER).
# Variables de conexion: PG_USER, PG_PASSWORD, MONGO_REPLICA_SET, MONGO_TLS,
# MONGO_CA_FILE, MONGO_ROOT_USER/PASSWORD, MONGO_OPLOG_REPLAY, NEO4J_DATABASE,
# NEO4J_IMAGE.
# ---------------------------------------------------------------------------
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "${SCRIPT_DIR}/../.." && pwd)"

# Git Bash (MSYS/Cygwin) reescribe las rutas internas del contenedor (/backup) a
# rutas de Windows y rompe `docker run -v host:/contenedor`. Convertimos el path
# de host con cygpath y desactivamos la conversion automatica de MSYS.
if command -v cygpath >/dev/null 2>&1; then
  export MSYS_NO_PATHCONV=1
  host_path() { cygpath -w "$1" | tr '\\' '/'; }
else
  host_path() { printf '%s' "$1"; }
fi

BACKUP_DIR=""
TARGET="all"
ASSUME_YES=0
CHECK_CHECKSUMS=1

PG_USER="${PG_USER:-smxplore}"
PG_PASSWORD="${PG_PASSWORD:-}"
MONGO_REPLICA_SET="${MONGO_REPLICA_SET:-rs0}"
MONGO_TLS="${MONGO_TLS:-false}"
MONGO_CA_FILE="${MONGO_CA_FILE:-/tmp/mongo-ca.pem}"
MONGO_ROOT_USER="${MONGO_ROOT_USER:-root}"
MONGO_ROOT_PASSWORD="${MONGO_ROOT_PASSWORD:-}"
MONGO_OPLOG_REPLAY="${MONGO_OPLOG_REPLAY:-true}"
NEO4J_DATABASE="${NEO4J_DATABASE:-neo4j}"
NEO4J_IMAGE="${NEO4J_IMAGE:-}"

log() { printf '[restore] %s\n' "$*"; }
warn() { printf '[restore][AVISO] %s\n' "$*" >&2; }
die() { printf '[restore][ERROR] %s\n' "$*" >&2; exit 1; }

usage() {
  cat <<'EOF'
Uso: RESTORE=yes deploy/backup/restore.sh --backup DIR [--target all|postgres|mongo|neo4j] [--yes] [--skip-checksums]

  --backup DIR      Directorio del backup a restaurar (obligatorio).
  --target T        all (default), postgres, mongo o neo4j.
  --yes             No pedir confirmacion.
  --skip-checksums  No validar checksums.sha256.
  -h, --help        Muestra esta ayuda.
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --backup) [[ $# -ge 2 ]] || die "falta el valor de --backup"; BACKUP_DIR="$2"; shift 2 ;;
    --backup=*) BACKUP_DIR="${1#*=}"; shift ;;
    --target) [[ $# -ge 2 ]] || die "falta el valor de --target"; TARGET="$2"; shift 2 ;;
    --target=*) TARGET="${1#*=}"; shift ;;
    --yes|-y) ASSUME_YES=1; shift ;;
    --skip-checksums) CHECK_CHECKSUMS=0; shift ;;
    -h|--help) usage; exit 0 ;;
    -*) die "opcion no reconocida: $1 (usa --help)" ;;
    *)
      [[ -z "${BACKUP_DIR}" ]] || die "argumento inesperado: $1"
      BACKUP_DIR="$1"
      shift
      ;;
  esac
done

case "${TARGET}" in
  all|postgres|mongo|neo4j) ;;
  *) die "--target invalido: ${TARGET} (usa all|postgres|mongo|neo4j)" ;;
esac

[[ -n "${BACKUP_DIR}" ]] || die "falta --backup DIR"
[[ -d "${BACKUP_DIR}" ]] || die "no existe el directorio de backup: ${BACKUP_DIR}"
BACKUP_DIR="$(cd "${BACKUP_DIR}" && pwd)"
[[ -f "${BACKUP_DIR}/manifest.txt" ]] || die "el directorio no parece un backup de SM-Xplore (falta manifest.txt)"

if [[ "${ASSUME_YES}" -ne 1 && "${RESTORE:-}" != "yes" ]]; then
  if [[ -t 0 ]]; then
    printf '[restore] Vas a SOBRESCRIBIR datos desde %s (target=%s).\n' "${BACKUP_DIR}" "${TARGET}" >&2
    printf "[restore] Escribe 'yes' para continuar: " >&2
    read -r answer
    [[ "${answer}" = "yes" ]] || die "restauracion cancelada (se esperaba 'yes')"
  else
    die "confirmacion requerida: define RESTORE=yes o pasa --yes"
  fi
fi

command -v docker >/dev/null 2>&1 || die "docker no esta disponible en PATH"
docker info >/dev/null 2>&1 || die "el daemon de Docker no responde"

# --- Descubrimiento de contenedores (igual que backup.sh) -------------------
detect_project() {
  if [[ -n "${COMPOSE_PROJECT:-}" ]]; then
    printf '%s' "${COMPOSE_PROJECT}"
    return 0
  fi
  local projects found=""
  projects="$(docker ps --format '{{.Label "com.docker.compose.project"}}' | grep -v '^$' | sort -u || true)"
  for candidate in smxplore-prod smxplore; do
    if printf '%s\n' "${projects}" | grep -qx "${candidate}"; then
      found="${found}${found:+,}${candidate}"
    fi
  done
  case "${found}" in
    "") die "no se detecto un stack Compose de SM-Xplore corriendo; define COMPOSE_PROJECT" ;;
    *,*) die "hay stacks 'smxplore' y 'smxplore-prod' corriendo; define COMPOSE_PROJECT para elegir" ;;
    *) printf '%s' "${found}" ;;
  esac
}

find_service_container() {
  local svc name
  for svc in "$@"; do
    name="$(docker ps \
      --filter "label=com.docker.compose.project=${PROJECT}" \
      --filter "label=com.docker.compose.service=${svc}" \
      --format '{{.Names}}' 2>/dev/null | head -n 1 || true)"
    if [[ -n "${name}" ]]; then
      printf '%s' "${name}"
      return 0
    fi
  done
  return 1
}

PROJECT="$(detect_project)"
log "proyecto Compose: ${PROJECT}"

POSTGRES_CONTAINER="${POSTGRES_CONTAINER:-$(find_service_container postgres || true)}"
MONGO_CONTAINER="${MONGO_CONTAINER:-$(find_service_container mongo1 mongo || true)}"
NEO4J_CONTAINER="${NEO4J_CONTAINER:-$(find_service_container neo4j || true)}"

[[ -n "${POSTGRES_CONTAINER}" ]] || die "no se encontro el contenedor de PostgreSQL"
[[ -n "${MONGO_CONTAINER}" ]] || die "no se encontro el contenedor de MongoDB"
[[ -n "${NEO4J_CONTAINER}" ]] || die "no se encontro el contenedor de Neo4j"
log "contenedores: postgres=${POSTGRES_CONTAINER} mongo=${MONGO_CONTAINER} neo4j=${NEO4J_CONTAINER}"

# --- Checksums --------------------------------------------------------------
if [[ "${CHECK_CHECKSUMS}" -eq 1 && -f "${BACKUP_DIR}/checksums.sha256" ]]; then
  log "validando checksums.sha256"
  if command -v sha256sum >/dev/null 2>&1; then
    ( cd "${BACKUP_DIR}" && sha256sum -c checksums.sha256 --quiet ) || die "checksums invalidos; backup corrupto (usa --skip-checksums si sabes lo que haces)"
  elif command -v shasum >/dev/null 2>&1; then
    ( cd "${BACKUP_DIR}" && shasum -a 256 -c checksums.sha256 ) || die "checksums invalidos; backup corrupto"
  else
    warn "no hay sha256sum/shasum en el host; se omite la validacion de checksums"
  fi
else
  [[ "${CHECK_CHECKSUMS}" -eq 0 ]] && warn "validacion de checksums omitida por --skip-checksums"
fi

FAILURES=0

# --- PostgreSQL -------------------------------------------------------------
pg_exec() { # args... ; ejecuta psql/pg_restore con PGPASSWORD si aplica
  local flags=(-i)
  if [[ -n "${PG_PASSWORD}" ]]; then
    flags+=(-e "PGPASSWORD=${PG_PASSWORD}")
  fi
  docker exec "${flags[@]}" "${POSTGRES_CONTAINER}" "$@"
}

restore_postgres() {
  local globals="${BACKUP_DIR}/postgres/globals.sql"
  local dump file db

  if [[ -f "${globals}" && -s "${globals}" ]]; then
    log "PostgreSQL: restaurando roles/privilegios (errores de 'ya existe' son tolerables)"
    if ! pg_exec psql -U "${PG_USER}" -d postgres -v ON_ERROR_STOP=0 < "${globals}"; then
      warn "pg_dumpall --globals-only devolvio errores; revisa roles a mano"
    fi
  else
    warn "no hay postgres/globals.sql; se omiten roles/privilegios"
  fi

  shopt -s nullglob
  local dumps=("${BACKUP_DIR}"/postgres/*.dump)
  shopt -u nullglob
  [[ ${#dumps[@]} -gt 0 ]] || { warn "no hay dumps de PostgreSQL en el backup"; return 1; }

  for file in "${dumps[@]}"; do
    db="$(basename "${file}" .dump)"
    log "PostgreSQL: restaurando base ${db}"

    # Crea la base si no existe (pg_restore no la crea).
    if ! pg_exec psql -U "${PG_USER}" -d postgres -tAc "SELECT 1 FROM pg_database WHERE datname='${db}'" | grep -q 1; then
      log "PostgreSQL: creando base ${db}"
      pg_exec createdb -U "${PG_USER}" "${db}" || { warn "no se pudo crear la base ${db}"; FAILURES=$((FAILURES + 1)); continue; }
    fi

    if pg_exec pg_restore -U "${PG_USER}" -d "${db}" --clean --if-exists < "${file}"; then
      log "PostgreSQL: ${db} restaurada"
    else
      warn "pg_restore de ${db} devolvio errores (revisa objetos dependientes de sesiones activas)"
      FAILURES=$((FAILURES + 1))
    fi
  done
}

# --- MongoDB ----------------------------------------------------------------
restore_mongo() {
  local archive="${BACKUP_DIR}/mongo/mongo.archive.gz"
  [[ -f "${archive}" && -s "${archive}" ]] || { warn "no hay mongo/mongo.archive.gz en el backup"; return 1; }

  log "MongoDB: restaurando archivo (--drop) en el replica set ${MONGO_REPLICA_SET}"
  local cmd=(docker exec -i "${MONGO_CONTAINER}" mongorestore
    "--uri=mongodb://127.0.0.1:27017/?replicaSet=${MONGO_REPLICA_SET}"
    --archive --gzip --drop)

  if [[ "${MONGO_OPLOG_REPLAY}" = "true" ]]; then
    cmd+=(--oplogReplay)
  fi
  if [[ "${MONGO_TLS}" = "true" ]]; then
    [[ -n "${MONGO_ROOT_PASSWORD}" ]] || die "MONGO_ROOT_PASSWORD es obligatorio con MONGO_TLS=true"
    cmd+=(--tls --tlsCAFile "${MONGO_CA_FILE}")
  fi
  if [[ -n "${MONGO_ROOT_PASSWORD}" ]]; then
    cmd+=(--username "${MONGO_ROOT_USER}" --password "${MONGO_ROOT_PASSWORD}" --authenticationDatabase admin)
  fi

  if "${cmd[@]}" < "${archive}"; then
    log "MongoDB: restauracion completada"
  else
    warn "mongorestore devolvio errores"
    return 1
  fi
}

# --- Neo4j (offline) --------------------------------------------------------
NEO4J_STOPPED=0
neo4j_restart() {
  if [[ "${NEO4J_STOPPED}" -eq 1 ]]; then
    log "Neo4j: rearrancando ${NEO4J_CONTAINER}"
    docker start "${NEO4J_CONTAINER}" >/dev/null 2>&1 || warn "no se pudo rearrancar ${NEO4J_CONTAINER}; arrancalo a mano"
    NEO4J_STOPPED=0
  fi
}
trap neo4j_restart EXIT

restore_neo4j() {
  local dump="${BACKUP_DIR}/neo4j/${NEO4J_DATABASE}.dump"
  [[ -f "${dump}" && -s "${dump}" ]] || { warn "no hay neo4j/${NEO4J_DATABASE}.dump en el backup"; return 1; }

  NEO4J_IMAGE="${NEO4J_IMAGE:-$(docker inspect -f '{{.Config.Image}}' "${NEO4J_CONTAINER}" 2>/dev/null || true)}"
  [[ -n "${NEO4J_IMAGE}" ]] || die "no se pudo determinar la imagen de Neo4j; define NEO4J_IMAGE"

  log "Neo4j: carga offline de la base ${NEO4J_DATABASE} (se detiene el contenedor)"
  docker stop "${NEO4J_CONTAINER}" >/dev/null || { warn "no se pudo detener ${NEO4J_CONTAINER}"; return 1; }
  NEO4J_STOPPED=1

  if ! docker run --rm \
    --volumes-from "${NEO4J_CONTAINER}" \
    --user 7474:7474 \
    -v "$(host_path "${BACKUP_DIR}/neo4j"):/backup:ro" \
    "${NEO4J_IMAGE}" \
    neo4j-admin database load "${NEO4J_DATABASE}" --from-path=/backup --overwrite-destination=true; then
    warn "fallo neo4j-admin database load"
    neo4j_restart
    return 1
  fi

  neo4j_restart
  log "Neo4j: restauracion completada"
}

case "${TARGET}" in
  postgres) restore_postgres || FAILURES=$((FAILURES + 1)) ;;
  mongo) restore_mongo || FAILURES=$((FAILURES + 1)) ;;
  neo4j) restore_neo4j || FAILURES=$((FAILURES + 1)) ;;
  all)
    restore_postgres || FAILURES=$((FAILURES + 1))
    restore_mongo || FAILURES=$((FAILURES + 1))
    restore_neo4j || FAILURES=$((FAILURES + 1))
    ;;
esac

if [[ "${FAILURES}" -gt 0 ]]; then
  die "restauracion completada con ${FAILURES} fallo(s); revisa los avisos antes de reiniciar los servicios"
fi

log "OK. Restauracion completada desde ${BACKUP_DIR}"
log "siguiente paso: verifica datos y vuelve a arrancar los servicios de aplicacion"
