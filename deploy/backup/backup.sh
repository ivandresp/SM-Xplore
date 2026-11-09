#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# P1-7 - Backup de la persistencia de SM-Xplore (PostgreSQL + MongoDB + Neo4j).
#
# Genera un directorio con timestamp bajo deploy/backups/ (o --out) con:
#   postgres/<base>.dump.age  pg_dump formato custom (-Fc) cifrado por base
#   postgres/globals.sql.age  pg_dumpall --globals-only (roles/privilegios)
#   mongo/mongo.archive.gz.age mongodump --archive --gzip [--oplog]
#   neo4j/neo4j.dump.age      neo4j-admin database dump (offline)
#   manifest.txt              que se copio, tamanios, contenedores y cifrado
#   checksums.sha256          SHA-256 de todos los archivos del backup
#   checksums.sha256.hmac     firma HMAC-SHA256 de los checksums (PLAT-07)
#
# Desde PLAT-07 los volcados se cifran en reposo (age por defecto) y se borra el
# original: el backup ya no deja datos personales en claro. Con
# BACKUP_ENCRYPTION=none (y ALLOW_PLAINTEXT_BACKUPS=1) se conserva el formato
# anterior sin la extension .age/.gpg.
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
#   BACKUP_ENCRYPTION      age | gpg | none (default: age).
#   AGE_RECIPIENT          Clave publica age o identificador de clave gpg.
#   AGE_RECIPIENTS_FILE    Archivo con recipients age (alternativa a AGE_RECIPIENT).
#   BACKUP_HMAC_KEY_FILE   Clave HMAC-SHA256 para firmar checksums.sha256.
#   ALLOW_PLAINTEXT_BACKUPS 1 para aceptar BACKUP_ENCRYPTION=none.
#
# Requisitos: docker con el stack levantado. No requiere Docker Desktop 4.3x
# en el host: todos los comandos se ejecutan DENTRO de los contenedores.
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

# PLAT-07: los dumps contienen datos personales (usuarios, reservas, reseñas,
# reportes de seguridad), asi que se cifran en reposo antes de dejar el
# directorio del backup y el manifiesto se firma con HMAC.
#   age | gpg | none   algoritmo de cifrado (default: age; "none" exige
#                      ALLOW_PLAINTEXT_BACKUPS=1 y deja un AVISO).
#   AGE_RECIPIENT      clave publica age (age1...) o identificador gpg.
#   AGE_RECIPIENTS_FILE  archivo con una o mas recipients age (alternativa).
#   BACKUP_HMAC_KEY_FILE archivo con la clave HMAC-SHA256 para firmar
#                        checksums.sha256 (si falta, solo se documenta el hash).
BACKUP_ENCRYPTION="${BACKUP_ENCRYPTION:-age}"
AGE_RECIPIENT="${AGE_RECIPIENT:-}"
AGE_RECIPIENTS_FILE="${AGE_RECIPIENTS_FILE:-}"
BACKUP_HMAC_KEY_FILE="${BACKUP_HMAC_KEY_FILE:-}"
ALLOW_PLAINTEXT_BACKUPS="${ALLOW_PLAINTEXT_BACKUPS:-0}"

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

# --- Validacion del cifrado (PLAT-07) ---------------------------------------
# Se valida ANTES de volcar nada: un backup que no se puede cifrar no debe
# quedar escrito en claro por un fallo a mitad de camino.
case "${BACKUP_ENCRYPTION}" in
  age)
    command -v age >/dev/null 2>&1 || die "BACKUP_ENCRYPTION=age pero 'age' no esta en PATH (instala age, o usa BACKUP_ENCRYPTION=gpg|none)"
    if [[ -z "${AGE_RECIPIENT}" && -z "${AGE_RECIPIENTS_FILE}" ]]; then
      die "define AGE_RECIPIENT (age1...) o AGE_RECIPIENTS_FILE para cifrar los backups"
    fi
    if [[ -n "${AGE_RECIPIENTS_FILE}" && ! -r "${AGE_RECIPIENTS_FILE}" ]]; then
      die "AGE_RECIPIENTS_FILE no existe o no es legible: ${AGE_RECIPIENTS_FILE}"
    fi
    ;;
  gpg)
    command -v gpg >/dev/null 2>&1 || die "BACKUP_ENCRYPTION=gpg pero 'gpg' no esta en PATH"
    [[ -n "${AGE_RECIPIENT}" ]] || die "define AGE_RECIPIENT con el id de la clave gpg de destino"
    ;;
  none)
    [[ "${ALLOW_PLAINTEXT_BACKUPS}" = "1" ]] || die "BACKUP_ENCRYPTION=none dejaria los dumps en claro; exporta ALLOW_PLAINTEXT_BACKUPS=1 si es intencional"
    warn "BACKUP_ENCRYPTION=none: los backups quedaran SIN cifrar"
    ;;
  *)
    die "BACKUP_ENCRYPTION no valido: ${BACKUP_ENCRYPTION} (age|gpg|none)"
    ;;
esac

if [[ -n "${BACKUP_HMAC_KEY_FILE}" ]]; then
  [[ -r "${BACKUP_HMAC_KEY_FILE}" ]] || die "BACKUP_HMAC_KEY_FILE no existe o no es legible: ${BACKUP_HMAC_KEY_FILE}"
  log "firma HMAC habilitada (checksums.sha256.hmac)"
else
  warn "sin BACKUP_HMAC_KEY_FILE: el manifiesto no se firma; cualquiera con acceso al directorio podria reescribir dumps y checksums"
fi

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

# PLAT-07: permisos restrictivos desde el primer fichero. umask 077 crea el
# directorio y los dumps solo para el usuario que ejecuta el backup; el resto de
# permisos se refuerza explicitamente mas abajo.
umask 077
mkdir -p "${OUT_DIR}"
OUT_DIR="$(cd "${OUT_DIR}" && pwd)"
TIMESTAMP="$(date -u +%Y%m%dT%H%M%SZ)"
DEST="${OUT_DIR}/${TIMESTAMP}"
[[ ! -e "${DEST}" ]] || die "ya existe ${DEST}; reintenta en unos segundos"
mkdir -p "${DEST}/postgres" "${DEST}/mongo" "${DEST}/neo4j"
chmod 0700 "${OUT_DIR}" "${DEST}" "${DEST}/postgres" "${DEST}/mongo" 2>/dev/null || true

FAILURES=0

# --- PostgreSQL -------------------------------------------------------------
backup_postgres() {
  log "PostgreSQL: volcando bases (${PG_DATABASES}) con usuario ${PG_USER}"
  local db out file
  local pg_exec_flags=(-i)
  if [[ -n "${PG_PASSWORD}" ]]; then
    # PLAT-11: la password se hereda del entorno del proceso (docker exec -e VAR),
    # no se escribe en argv (`docker exec -e VAR=valor` queda visible en `ps`).
    pg_exec_flags+=(-e PGPASSWORD)
    export PGPASSWORD="${PG_PASSWORD}"
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
# PLAT-11: las credenciales no van en argv (visibles en `ps`). mongodump lee
# usuario/password/TLS de un archivo de configuracion YAML temporal que se
# escribe dentro del contenedor por stdin con umask 077 y se borra al terminar.
MONGO_CONFIG_PATH=""

mongo_config_cleanup() {
  if [[ -n "${MONGO_CONFIG_PATH}" ]]; then
    docker exec "${MONGO_CONTAINER}" rm -f "${MONGO_CONFIG_PATH}" >/dev/null 2>&1 || true
    MONGO_CONFIG_PATH=""
  fi
}

# Comillas de un escalar YAML de una linea (comillas simples, '' escapa ').
yaml_quote() {
  printf "'%s'" "$(printf '%s' "$1" | sed "s/'/''/g")"
}

mongo_config_yaml() {
  printf 'uri: %s\n' "$(yaml_quote "mongodb://127.0.0.1:27017/?replicaSet=${MONGO_REPLICA_SET}")"
  if [[ -n "${MONGO_ROOT_PASSWORD}" ]]; then
    printf 'username: %s\n' "$(yaml_quote "${MONGO_ROOT_USER}")"
    printf 'password: %s\n' "$(yaml_quote "${MONGO_ROOT_PASSWORD}")"
    printf 'authenticationDatabase: %s\n' "$(yaml_quote "admin")"
  fi
  if [[ "${MONGO_TLS}" = "true" ]]; then
    printf 'tls: true\n'
    printf 'tlsCAFile: %s\n' "$(yaml_quote "${MONGO_CA_FILE}")"
  fi
}

mongo_dump_args() {
  printf '%s\n' '--archive'
  printf '%s\n' '--gzip'
  if [[ "${MONGO_OPLOG}" = "true" ]]; then
    printf '%s\n' '--oplog'
  fi
}

backup_mongo() {
  log "MongoDB: mongodump del replica set ${MONGO_REPLICA_SET}"
  local args=()
  local line
  while IFS= read -r line; do args+=("${line}"); done < <(mongo_dump_args)

  if [[ "${MONGO_TLS}" = "true" && -z "${MONGO_ROOT_PASSWORD}" ]]; then
    die "MONGO_ROOT_PASSWORD es obligatorio con MONGO_TLS=true"
  fi

  MONGO_CONFIG_PATH="/tmp/smxplore-mongodump-$$.yaml"
  if ! mongo_config_yaml | docker exec -i "${MONGO_CONTAINER}" \
    sh -c 'umask 077; cat > "$1"' sh "${MONGO_CONFIG_PATH}"; then
    warn "no se pudo escribir la configuracion temporal de mongodump"
    MONGO_CONFIG_PATH=""
    return 1
  fi

  local cmd=(docker exec -i "${MONGO_CONTAINER}" mongodump --config "${MONGO_CONFIG_PATH}")
  cmd+=("${args[@]}")

  local file="${DEST}/mongo/mongo.archive.gz"
  if ! "${cmd[@]}" > "${file}"; then
    warn "fallo mongodump"
    rm -f "${file}"
    mongo_config_cleanup
    return 1
  fi
  mongo_config_cleanup
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
  # como el usuario neo4j (uid 7474). PLAT-07: en lugar de 0777 (escribible por
  # cualquier cuenta del host) el directorio queda 0770 y el contenedor corre con
  # el grupo 0, que es el grupo dueno, para poder escribir.
  mkdir -p "${DEST}/neo4j"
  chmod 0770 "${DEST}/neo4j" 2>/dev/null || true

  if ! docker run --rm \
    --volumes-from "${NEO4J_CONTAINER}" \
    --user 7474:0 \
    -v "$(host_path "${DEST}/neo4j"):/backup" \
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

# --- Cifrado en reposo (PLAT-07) -------------------------------------------
# Un dump en claro es una copia completa de la base de datos: cualquiera que lea
# el directorio (o se lleve una copia del disco) obtiene datos personales sin
# necesidad de comprometer el stack. Se cifra cada volcado y se borra el
# original; el manifiesto y los checksums quedan en claro y firmados.
encrypt_backups() {
  local f out
  while IFS= read -r -d '' f; do
    case "${BACKUP_ENCRYPTION}" in
      none) return 0 ;;
      age)
        out="${f}.age"
        if [[ -n "${AGE_RECIPIENTS_FILE}" ]]; then
          age --encrypt --recipients-file "${AGE_RECIPIENTS_FILE}" --output "${out}" "${f}" \
            || { warn "no se pudo cifrar ${f}"; return 1; }
        else
          age --encrypt --recipient "${AGE_RECIPIENT}" --output "${out}" "${f}" \
            || { warn "no se pudo cifrar ${f}"; return 1; }
        fi
        ;;
      gpg)
        out="${f}.gpg"
        gpg --batch --yes --trust-model always --recipient "${AGE_RECIPIENT}" \
          --output "${out}" --encrypt "${f}" \
          || { warn "no se pudo cifrar ${f}"; return 1; }
        ;;
    esac
    rm -f -- "${f}"
  done < <(find "${DEST}" -type f \( -name '*.dump' -o -name '*.gz' -o -name '*.sql' \) -print0)
  return 0
}

if [[ "${BACKUP_ENCRYPTION}" != "none" ]]; then
  log "cifrando volcados con ${BACKUP_ENCRYPTION}"
  if ! encrypt_backups; then
    FAILURES=$((FAILURES + 1))
    warn "quedaron volcados SIN cifrar en ${DEST}"
  fi
elif [[ "${BACKUP_ENCRYPTION}" = "none" ]]; then
  warn "volcados SIN cifrar: proteger el directorio con permisos y cifrado de disco"
fi

# Ningun archivo del backup debe ser legible por otros usuarios del host.
chmod -R go-rwx "${DEST}" 2>/dev/null || true

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
  printf 'encryption=%s\n' "${BACKUP_ENCRYPTION}"
  printf 'hmac_signed=%s\n' "$([[ -n "${BACKUP_HMAC_KEY_FILE}" ]] && echo true || echo false)"
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

# PLAT-07: firmar los checksums. Solo un SHA-256 demuestra integridad frente a
# corrupcion; cualquiera con escritura en el directorio puede recalcularlo. El
# HMAC con una clave que no vive junto al backup demuestra autenticidad.
if [[ -n "${BACKUP_HMAC_KEY_FILE}" && -f "${DEST}/checksums.sha256" ]]; then
  command -v openssl >/dev/null 2>&1 || die "openssl no esta en PATH; es necesario para firmar los checksums"
  if ( cd "${DEST}" && openssl dgst -sha256 \
        -hmac "$(cat "${BACKUP_HMAC_KEY_FILE}")" checksums.sha256 \
        | sed 's/^.*= //' > checksums.sha256.hmac ); then
    chmod 0600 "${DEST}/checksums.sha256.hmac" 2>/dev/null || true
    log "firma: checksums.sha256.hmac"
  else
    warn "no se pudo firmar checksums.sha256"
    FAILURES=$((FAILURES + 1))
  fi
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
