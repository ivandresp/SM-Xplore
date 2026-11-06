#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# P1-7 - Backup de la persistencia de SM-Xplore (PostgreSQL + MongoDB + Neo4j).
#
# Genera un directorio con timestamp bajo deploy/backups/ (o --out) con:
#   postgres/<base>.dump   pg_dump formato custom (-Fc) por base
#   postgres/globals.sql   pg_dumpall --globals-only (roles/privilegios)
#   mongo/mongo.archive.gz mongodump --archive --gzip [--oplog]
#   neo4j/neo4j.dump       neo4j-admin database dump (offline)
#   manifest.txt           que se copio, tamanios y contenedores usados
#   checksums.sha256       SHA-256 de todos los archivos del backup
#
# Uso:
#   deploy/backup/backup.sh [--out DIR] [--keep-days N]
#
#   --out DIR       Directorio raiz de backups (default: deploy/backups).
#   --keep-days N   Purga backups de mas de N dias (default: 7; 0 = no purgar).
#
# Descubrimiento de contenedores:
#   Usa los labels de Docker Compose. Project default: se autodetecta entre
#   `smxplore-prod` (produccion) y `smxplore` (dev). Puedes forzarlo con
#   COMPOSE_PROJECT; los contenedores individuales con POSTGRES_CONTAINER,
#   MONGO_CONTAINER y NEO4J_CONTAINER. Se asume la nomenclatura por defecto de
#   Compose: <proyecto>-<servicio>-1 (p.ej. smxplore-prod-postgres-1).
#
# Variables de entorno opcionales:
#   COMPOSE_PROJECT        Proyecto Compose (default: autodetectado).
#   POSTGRES_CONTAINER     Nombre del contenedor de PostgreSQL.
#   MONGO_CONTAINER        Nombre del contenedor de Mongo (default: el servicio
#                          mongo1, o mongo en dev).
#   NEO4J_CONTAINER        Nombre del contenedor de Neo4j.
#   PG_DATABASES           Bases a volcar (default: "smxplore_users activities_events smxplore").
#   PG_USER                Usuario de pg_dump (default: smxplore).
#   PG_PASSWORD            Password de PG si hiciera falta (se pasa como PGPASSWORD).
#   MONGO_REPLICA_SET      Nombre del RS (default: rs0).
#   MONGO_TLS              "true" para mongodump con TLS (default: false).
#   MONGO_CA_FILE          CA dentro del contenedor Mongo (default: /tmp/mongo-ca.pem).
#   MONGO_ROOT_USER        Usuario de backup Mongo (default: root).
#   MONGO_ROOT_PASSWORD    Password del usuario de backup Mongo.
#   MONGO_OPLOG            "true"/"false" para mongodump --oplog (default: true).
#   NEO4J_DATABASE         Base de Neo4j (default: neo4j).
#   NEO4J_IMAGE            Imagen para el dump offline (default: la del contenedor).
#   BACKUP_RETENTION_DAYS  Equivalente a --keep-days.
#
# Requisitos: docker con el stack levantado. No requiere Docker Desktop 4.3x
# en el host: todos los comandos se ejecutan DENTRO de los contenedores.
# ---------------------------------------------------------------------------
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "${SCRIPT_DIR}/../.." && pwd)"

OUT_DIR="${REPO_ROOT}/deploy/backups"
KEEP_DAYS="${BACKUP_RETENTION_DAYS:-7}"

PG_DATABASES="${PG_DATABASES:-smxplore_users activities_events smxplore}"
PG_USER="${PG_USER:-smxplore}"
PG_PASSWORD="${PG_PASSWORD:-}"
MONGO_REPLICA_SET="${MONGO_REPLICA_SET:-rs0}"
MONGO_TLS="${MONGO_TLS:-false}"
MONGO_CA_FILE="${MONGO_CA_FILE:-/tmp/mongo-ca.pem}"
MONGO_ROOT_USER="${MONGO_ROOT_USER:-root}"
MONGO_ROOT_PASSWORD="${MONGO_ROOT_PASSWORD:-}"
MONGO_OPLOG="${MONGO_OPLOG:-true}"
NEO4J_DATABASE="${NEO4J_DATABASE:-neo4j}"
NEO4J_IMAGE="${NEO4J_IMAGE:-}"

log() { printf '[backup] %s\n' "$*"; }
warn() { printf '[backup][AVISO] %s\n' "$*" >&2; }
die() { printf '[backup][ERROR] %s\n' "$*" >&2; exit 1; }

usage() {
  cat <<'EOF'
Uso: deploy/backup/backup.sh [--out DIR] [--keep-days N]

  --out DIR       Directorio raiz de backups (default: deploy/backups).
  --keep-days N   Purga backups de mas de N dias (default: 7; 0 = no purgar).
  -h, --help      Muestra esta ayuda.
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --out) [[ $# -ge 2 ]] || die "falta el valor de --out"; OUT_DIR="$2"; shift 2 ;;
    --out=*) OUT_DIR="${1#*=}"; shift ;;
    --keep-days) [[ $# -ge 2 ]] || die "falta el valor de --keep-days"; KEEP_DAYS="$2"; shift 2 ;;
    --keep-days=*) KEEP_DAYS="${1#*=}"; shift ;;
    -h|--help) usage; exit 0 ;;
    *) die "opcion no reconocida: $1 (usa --help)" ;;
  esac
done

case "${KEEP_DAYS}" in
  ''|*[!0-9]*) die "--keep-days debe ser un entero >= 0 (recibido: ${KEEP_DAYS})" ;;
esac

command -v docker >/dev/null 2>&1 || die "docker no esta disponible en PATH"
docker info >/dev/null 2>&1 || die "el daemon de Docker no responde; levanta el stack antes del backup"

# --- Descubrimiento de contenedores ----------------------------------------
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

find_service_container() { # servicio1 [servicio2 ...]
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

if [[ "${MONGO_TLS}" = "true" ]]; then
  [[ -n "${MONGO_ROOT_PASSWORD}" ]] || die "MONGO_ROOT_PASSWORD es obligatorio con MONGO_TLS=true"
fi

NEO4J_IMAGE="${NEO4J_IMAGE:-$(docker inspect -f '{{.Config.Image}}' "${NEO4J_CONTAINER}" 2>/dev/null || true)}"
[[ -n "${NEO4J_IMAGE}" ]] || die "no se pudo determinar la imagen de Neo4j; define NEO4J_IMAGE"

log "contenedores: postgres=${POSTGRES_CONTAINER} mongo=${MONGO_CONTAINER} neo4j=${NEO4J_CONTAINER}"

mkdir -p "${OUT_DIR}"
OUT_DIR="$(cd "${OUT_DIR}" && pwd)"
TIMESTAMP="$(date -u +%Y%m%dT%H%M%SZ)"
DEST="${OUT_DIR}/${TIMESTAMP}"
[[ ! -e "${DEST}" ]] || die "ya existe ${DEST}; reintenta en unos segundos"
mkdir -p "${DEST}/postgres" "${DEST}/mongo" "${DEST}/neo4j"

FAILURES=0

# --- PostgreSQL -------------------------------------------------------------
backup_postgres() {
  log "PostgreSQL: volcando bases (${PG_DATABASES}) con usuario ${PG_USER}"
  local db out file
  local pg_exec_flags=(-i)
  if [[ -n "${PG_PASSWORD}" ]]; then
    pg_exec_flags+=(-e "PGPASSWORD=${PG_PASSWORD}")
  fi
  for db in ${PG_DATABASES}; do
    file="${DEST}/postgres/${db}.dump"
    if ! docker exec "${pg_exec_flags[@]}" "${POSTGRES_CONTAINER}" \
      pg_dump -U "${PG_USER}" -Fc -d "${db}" > "${file}"; then
      warn "fallo pg_dump de la base ${db}"
      rm -f "${file}"
      return 1
    fi
    [[ -s "${file}" ]] || { warn "dump vacio para la base ${db}"; rm -f "${file}"; return 1; }
    log "PostgreSQL: ${db} -> postgres/${db}.dump ($(du -h "${file}" | cut -f1))"
  done

  log "PostgreSQL: volcando roles/privilegios globales"
  if ! docker exec "${pg_exec_flags[@]}" "${POSTGRES_CONTAINER}" \
    pg_dumpall -U "${PG_USER}" --globals-only > "${DEST}/postgres/globals.sql"; then
    warn "fallo pg_dumpall --globals-only"
    return 1
  fi
  [[ -s "${DEST}/postgres/globals.sql" ]] || { warn "globals.sql vacio"; return 1; }
}

# --- MongoDB ----------------------------------------------------------------
mongo_dump_args() {
  printf '%s\n' '--archive'
  printf '%s\n' '--gzip'
  if [[ "${MONGO_OPLOG}" = "true" ]]; then
    printf '%s\n' '--oplog'
  fi
  printf '%s\n' "--uri=mongodb://127.0.0.1:27017/?replicaSet=${MONGO_REPLICA_SET}"
}

backup_mongo() {
  log "MongoDB: mongodump del replica set ${MONGO_REPLICA_SET}"
  local args=()
  local line
  while IFS= read -r line; do args+=("${line}"); done < <(mongo_dump_args)

  local cmd=(docker exec -i "${MONGO_CONTAINER}" mongodump)
  cmd+=("${args[@]}")

  if [[ "${MONGO_TLS}" = "true" ]]; then
    cmd+=(--tls --tlsCAFile "${MONGO_CA_FILE}")
  fi
  if [[ -n "${MONGO_ROOT_PASSWORD}" ]]; then
    cmd+=(--username "${MONGO_ROOT_USER}" --password "${MONGO_ROOT_PASSWORD}" --authenticationDatabase admin)
  elif [[ "${MONGO_TLS}" = "true" ]]; then
    die "MONGO_ROOT_PASSWORD es obligatorio con MONGO_TLS=true"
  fi

  local file="${DEST}/mongo/mongo.archive.gz"
  if ! "${cmd[@]}" > "${file}"; then
    warn "fallo mongodump"
    rm -f "${file}"
    return 1
  fi
  [[ -s "${file}" ]] || { warn "archivo mongodump vacio"; rm -f "${file}"; return 1; }
  log "MongoDB -> mongo/mongo.archive.gz ($(du -h "${file}" | cut -f1))"
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

backup_neo4j() {
  log "Neo4j: dump offline de la base ${NEO4J_DATABASE} (se detiene el contenedor)"
  docker stop "${NEO4J_CONTAINER}" >/dev/null || { warn "no se pudo detener ${NEO4J_CONTAINER}"; return 1; }
  NEO4J_STOPPED=1

  # El dump se escribe en el bind mount del host; el contenedor efimero corre
  # como el usuario neo4j (uid 7474), por eso el directorio debe ser escribible.
  mkdir -p "${DEST}/neo4j"
  chmod 0777 "${DEST}/neo4j" 2>/dev/null || true

  if ! docker run --rm \
    --volumes-from "${NEO4J_CONTAINER}" \
    --user 7474:7474 \
    -v "${DEST}/neo4j:/backup" \
    "${NEO4J_IMAGE}" \
    neo4j-admin database dump "${NEO4J_DATABASE}" --to-path=/backup; then
    warn "fallo neo4j-admin database dump"
    neo4j_restart
    return 1
  fi

  neo4j_restart
  [[ -s "${DEST}/neo4j/${NEO4J_DATABASE}.dump" ]] || { warn "dump de Neo4j vacio o ausente"; return 1; }
  log "Neo4j -> neo4j/${NEO4J_DATABASE}.dump ($(du -h "${DEST}/neo4j/${NEO4J_DATABASE}.dump" | cut -f1))"
}

if ! backup_postgres; then FAILURES=$((FAILURES + 1)); fi
if ! backup_mongo; then FAILURES=$((FAILURES + 1)); fi
if ! backup_neo4j; then FAILURES=$((FAILURES + 1)); fi

# --- Manifest y checksums ---------------------------------------------------
{
  printf 'created_at=%s\n' "$(date -u +%Y-%m-%dT%H:%M:%SZ)"
  printf 'host=%s\n' "$(hostname 2>/dev/null || echo desconocido)"
  printf 'compose_project=%s\n' "${PROJECT}"
  printf 'postgres_container=%s\n' "${POSTGRES_CONTAINER}"
  printf 'mongo_container=%s\n' "${MONGO_CONTAINER}"
  printf 'neo4j_container=%s\n' "${NEO4J_CONTAINER}"
  printf 'postgres_databases=%s\n' "${PG_DATABASES}"
  printf 'postgres_user=%s\n' "${PG_USER}"
  printf 'mongo_replica_set=%s\n' "${MONGO_REPLICA_SET}"
  printf 'mongo_tls=%s\n' "${MONGO_TLS}"
  printf 'mongo_oplog=%s\n' "${MONGO_OPLOG}"
  printf 'neo4j_database=%s\n' "${NEO4J_DATABASE}"
  printf 'neo4j_image=%s\n' "${NEO4J_IMAGE}"
  printf 'failures=%s\n' "${FAILURES}"
} > "${DEST}/manifest.txt"

sha256_cmd=""
if command -v sha256sum >/dev/null 2>&1; then
  sha256_cmd="sha256sum"
elif command -v shasum >/dev/null 2>&1; then
  sha256_cmd="shasum -a 256"
fi
if [[ -n "${sha256_cmd}" ]]; then
  ( cd "${DEST}" && find . -type f ! -name checksums.sha256 -print0 \
      | sort -z | xargs -0 ${sha256_cmd} > checksums.sha256 )
  log "checksums: checksums.sha256"
else
  warn "no hay sha256sum/shasum en el host; se omite checksums.sha256"
fi

# --- Retencion --------------------------------------------------------------
if [[ "${KEEP_DAYS}" -gt 0 ]]; then
  log "retencion: eliminando backups de mas de ${KEEP_DAYS} dias en ${OUT_DIR}"
  find "${OUT_DIR}" -mindepth 1 -maxdepth 1 -type d \
    -name '[0-9][0-9][0-9][0-9][0-9][0-9][0-9][0-9]T[0-9][0-9][0-9][0-9][0-9][0-9]Z' \
    -mtime "+${KEEP_DAYS}" -print -exec rm -rf {} + 2>/dev/null || true
fi

if [[ "${FAILURES}" -gt 0 ]]; then
  die "backup completado con ${FAILURES} fallo(s); revisa los avisos. Directorio: ${DEST}"
fi

log "OK. Backup completo en ${DEST}"
log "verifica la integridad con: deploy/backup/verify-backup.sh --backup ${DEST}"
