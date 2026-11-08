#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# P1-7 - Verificacion de integridad de un backup de SM-Xplore generado por
# deploy/backup/backup.sh (PostgreSQL + MongoDB + Neo4j).
#
# Uso:
#   deploy/backup/verify-backup.sh --backup DIR [--target all|postgres|mongo|neo4j]
#
# Comprueba:
#   - manifest.txt presente y con failures=0.
#   - checksums.sha256 (si existe): todos los archivos coinciden.
#   - PLAT-07: checksums.sha256.hmac si existe (BACKUP_HMAC_KEY_FILE).
#   - PLAT-07: si los volcados estan cifrados (.age/.gpg) se descifran a un
#     directorio temporal 0700 (AGE_IDENTITY_FILE o GPG_PASSPHRASE_FILE) para
#     poder ejecutar las comprobaciones profundas.
#   - PostgreSQL: cada .dump se lee con `pg_restore --list` (>= 1 entrada) y
#     globals.sql es legible; avisa si no contiene CREATE ROLE.
#   - MongoDB: mongo.archive.gz no vacio y `mongorestore --dryRun` lo abre y
#     enumera sin escribir (conecta al RS, por eso el stack debe estar arriba).
#   - Neo4j: neo4j.dump existe, no vacio y con un tamanio minimo razonable.
#     La validacion completa exige cargarlo (restore.sh); aqui solo se valida
#     presencia/tamanio para no tocar el volumen de datos.
#
# Exit code: 0 si todo pasa; 1 si falta algo o falla una comprobacion.
#
# Descubrimiento de contenedores y variables de conexion: identicos a
# backup.sh (COMPOSE_PROJECT, POSTGRES_CONTAINER, MONGO_CONTAINER,
# NEO4J_CONTAINER; PG_USER/PG_PASSWORD, MONGO_*).
# ---------------------------------------------------------------------------
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "${SCRIPT_DIR}/../.." && pwd)"

BACKUP_DIR=""
TARGET="all"

PG_USER="${PG_USER:-smxplore}"
PG_PASSWORD="${PG_PASSWORD:-}"
MONGO_REPLICA_SET="${MONGO_REPLICA_SET:-rs0}"
MONGO_TLS="${MONGO_TLS:-false}"
MONGO_CA_FILE="${MONGO_CA_FILE:-/tmp/mongo-ca.pem}"
MONGO_ROOT_USER="${MONGO_ROOT_USER:-root}"
MONGO_ROOT_PASSWORD="${MONGO_ROOT_PASSWORD:-}"
NEO4J_DATABASE="${NEO4J_DATABASE:-neo4j}"
NEO4J_MIN_DUMP_BYTES="${NEO4J_MIN_DUMP_BYTES:-1024}"

# PLAT-07: firma y cifrado. La verificacion profunda (pg_restore --list,
# mongorestore --dryRun) necesita el contenido en claro, asi que si el backup
# esta cifrado se descifra a un directorio temporal 0700 que se borra al salir.
AGE_IDENTITY_FILE="${AGE_IDENTITY_FILE:-}"
GPG_PASSPHRASE_FILE="${GPG_PASSPHRASE_FILE:-}"
BACKUP_HMAC_KEY_FILE="${BACKUP_HMAC_KEY_FILE:-}"
WORK_DIR=""

log() { printf '[verify-backup] %s\n' "$*"; }
warn() { printf '[verify-backup][AVISO] %s\n' "$*" >&2; }
fail() { printf '[verify-backup][FALLO] %s\n' "$*" >&2; FAILURES=$((FAILURES + 1)); }
die() { printf '[verify-backup][ERROR] %s\n' "$*" >&2; exit 1; }

FAILURES=0

usage() {
  cat <<'EOF'
Uso: deploy/backup/verify-backup.sh --backup DIR [--target all|postgres|mongo|neo4j]

  --backup DIR   Directorio del backup a verificar (obligatorio).
  --target T     all (default), postgres, mongo o neo4j.
  -h, --help     Muestra esta ayuda.
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --backup) [[ $# -ge 2 ]] || die "falta el valor de --backup"; BACKUP_DIR="$2"; shift 2 ;;
    --backup=*) BACKUP_DIR="${1#*=}"; shift ;;
    --target) [[ $# -ge 2 ]] || die "falta el valor de --target"; TARGET="$2"; shift 2 ;;
    --target=*) TARGET="${1#*=}"; shift ;;
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

command -v docker >/dev/null 2>&1 || die "docker no esta disponible en PATH"
docker info >/dev/null 2>&1 || die "el daemon de Docker no responde"

# --- Manifest y checksums ---------------------------------------------------
if [[ -f "${BACKUP_DIR}/manifest.txt" ]]; then
  log "manifest.txt encontrado"
  if grep -q '^failures=0$' "${BACKUP_DIR}/manifest.txt"; then
    log "manifest: failures=0"
  else
    fail "manifest.txt indica failures != 0 (backup incompleto)"
  fi
else
  fail "falta manifest.txt (no parece un backup de SM-Xplore)"
fi

if [[ -f "${BACKUP_DIR}/checksums.sha256" ]]; then
  log "validando checksums.sha256"
  if command -v sha256sum >/dev/null 2>&1; then
    if ( cd "${BACKUP_DIR}" && sha256sum -c checksums.sha256 --quiet ); then
      log "checksums: OK"
    else
      fail "checksums.sha256 no coincide"
    fi
  elif command -v shasum >/dev/null 2>&1; then
    if ( cd "${BACKUP_DIR}" && shasum -a 256 -c checksums.sha256 >/dev/null ); then
      log "checksums: OK"
    else
      fail "checksums.sha256 no coincide"
    fi
  else
    warn "no hay sha256sum/shasum en el host; no se validan checksums"
  fi
else
  warn "no hay checksums.sha256; se omite la validacion criptografica"
fi

# PLAT-07: firma HMAC de los checksums (autenticidad, no solo integridad).
if [[ -f "${BACKUP_DIR}/checksums.sha256.hmac" ]]; then
  if [[ -z "${BACKUP_HMAC_KEY_FILE}" ]]; then
    warn "backup firmado pero sin BACKUP_HMAC_KEY_FILE: no se puede verificar la firma"
  else
    expected="$(tr -d ' \r\n' < "${BACKUP_DIR}/checksums.sha256.hmac")"
    actual="$( cd "${BACKUP_DIR}" && openssl dgst -sha256 \
      -hmac "$(cat "${BACKUP_HMAC_KEY_FILE}")" checksums.sha256 | sed 's/^.*= //' | tr -d ' \r\n' )"
    if [[ "${expected}" = "${actual}" ]]; then
      log "firma HMAC: OK"
    else
      fail "firma HMAC invalida: el respaldo fue alterado o la clave no corresponde"
    fi
  fi
else
  warn "backup sin firma HMAC (checksums.sha256.hmac)"
fi

# --- Descifrado a un directorio temporal (PLAT-07) --------------------------
FIRST_ENC="$(find "${BACKUP_DIR}" -type f \( -name '*.age' -o -name '*.gpg' \) -print 2>/dev/null | head -n 1 || true)"
if [[ -n "${FIRST_ENC}" ]]; then
  case "${FIRST_ENC}" in
    *.age)
      command -v age >/dev/null 2>&1 || die "el backup esta cifrado con age y 'age' no esta en PATH"
      [[ -n "${AGE_IDENTITY_FILE}" ]] || die "define AGE_IDENTITY_FILE para descifrar y verificar el contenido"
      [[ -r "${AGE_IDENTITY_FILE}" ]] || die "AGE_IDENTITY_FILE no es legible: ${AGE_IDENTITY_FILE}"
      ;;
    *.gpg) command -v gpg >/dev/null 2>&1 || die "el backup esta cifrado con gpg y 'gpg' no esta en PATH" ;;
  esac

  log "descifrando a un directorio temporal para la verificacion profunda"
  WORK_DIR="$(mktemp -d "${TMPDIR:-/tmp}/smxplore-verify.XXXXXX")"
  chmod 0700 "${WORK_DIR}"
  trap '[[ -n "${WORK_DIR}" && -d "${WORK_DIR}" ]] && rm -rf -- "${WORK_DIR}"' EXIT
  while IFS= read -r -d '' f; do
    rel="${f#"${BACKUP_DIR}"/}"
    case "${rel}" in
      *.age) out="${WORK_DIR}/${rel%.age}" ;;
      *.gpg) out="${WORK_DIR}/${rel%.gpg}" ;;
      *) continue ;;
    esac
    mkdir -p "$(dirname "${out}")"
    case "${rel}" in
      *.age) age --decrypt --identity "${AGE_IDENTITY_FILE}" --output "${out}" "${f}" || die "no se pudo descifrar ${rel}" ;;
      *.gpg)
        if [[ -n "${GPG_PASSPHRASE_FILE}" ]]; then
          gpg --batch --yes --pinentry-mode loopback --passphrase-file "${GPG_PASSPHRASE_FILE}" --output "${out}" --decrypt "${f}" || die "no se pudo descifrar ${rel}"
        else
          gpg --batch --yes --output "${out}" --decrypt "${f}" || die "no se pudo descifrar ${rel}"
        fi
        ;;
    esac
  done < <(find "${BACKUP_DIR}" -type f \( -name '*.age' -o -name '*.gpg' \) -print0)
  BACKUP_DIR="${WORK_DIR}"
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
POSTGRES_CONTAINER="${POSTGRES_CONTAINER:-$(find_service_container postgres || true)}"
MONGO_CONTAINER="${MONGO_CONTAINER:-$(find_service_container mongo1 mongo || true)}"
log "proyecto Compose: ${PROJECT}"

# --- PostgreSQL -------------------------------------------------------------
verify_postgres() {
  local globals="${BACKUP_DIR}/postgres/globals.sql"
  local dump entries

  if [[ -f "${globals}" && -s "${globals}" ]]; then
    if grep -qi 'CREATE ROLE' "${globals}"; then
      log "PostgreSQL: globals.sql contiene definiciones de roles"
    else
      warn "PostgreSQL: globals.sql no contiene CREATE ROLE (puede ser un cluster sin roles extra)"
    fi
  else
    fail "PostgreSQL: falta postgres/globals.sql o esta vacio"
  fi

  shopt -s nullglob
  local dumps=("${BACKUP_DIR}"/postgres/*.dump)
  shopt -u nullglob
  if [[ ${#dumps[@]} -eq 0 ]]; then
    fail "PostgreSQL: no hay archivos postgres/*.dump"
    return 0
  fi

  [[ -n "${POSTGRES_CONTAINER}" ]] || { fail "PostgreSQL: no se encontro el contenedor"; return 0; }

  local flags=(-i)
  if [[ -n "${PG_PASSWORD}" ]]; then
    flags+=(-e "PGPASSWORD=${PG_PASSWORD}")
  fi

  for dump in "${dumps[@]}"; do
    entries="$(docker exec "${flags[@]}" "${POSTGRES_CONTAINER}" pg_restore --list < "${dump}" 2>/dev/null | wc -l | tr -d ' ' || true)"
    if [[ "${entries:-0}" -ge 1 ]]; then
      log "PostgreSQL: $(basename "${dump}") OK (${entries} entradas)"
    else
      fail "PostgreSQL: $(basename "${dump}") no se pudo interpretar con pg_restore --list"
    fi
  done
}

# --- MongoDB ----------------------------------------------------------------
verify_mongo() {
  local archive="${BACKUP_DIR}/mongo/mongo.archive.gz"
  if [[ ! -f "${archive}" || ! -s "${archive}" ]]; then
    fail "MongoDB: falta mongo/mongo.archive.gz o esta vacio"
    return 0
  fi
  [[ -n "${MONGO_CONTAINER}" ]] || { fail "MongoDB: no se encontro el contenedor"; return 0; }

  log "MongoDB: comprobando archivo con mongorestore --dryRun"
  local cmd=(docker exec -i "${MONGO_CONTAINER}" mongorestore
    "--uri=mongodb://127.0.0.1:27017/?replicaSet=${MONGO_REPLICA_SET}"
    --archive --gzip --dryRun)

  if [[ "${MONGO_TLS}" = "true" ]]; then
    cmd+=(--tls --tlsCAFile "${MONGO_CA_FILE}")
  fi
  if [[ -n "${MONGO_ROOT_PASSWORD}" ]]; then
    cmd+=(--username "${MONGO_ROOT_USER}" --password "${MONGO_ROOT_PASSWORD}" --authenticationDatabase admin)
  fi

  local output
  if output="$("${cmd[@]}" < "${archive}" 2>&1)"; then
    # mongorestore --dryRun enumera los namespaces que restauraria.
    if printf '%s' "${output}" | grep -qiE 'would restore|done'; then
      log "MongoDB: mongo.archive.gz OK (archivo interpretado por mongorestore)"
    else
      log "MongoDB: mongo.archive.gz OK (dryRun sin errores)"
    fi
  else
    fail "MongoDB: mongorestore --dryRun fallo: ${output}"
  fi
}

# --- Neo4j ------------------------------------------------------------------
verify_neo4j() {
  local dump="${BACKUP_DIR}/neo4j/${NEO4J_DATABASE}.dump"
  if [[ ! -f "${dump}" ]]; then
    fail "Neo4j: falta neo4j/${NEO4J_DATABASE}.dump"
    return 0
  fi
  local size
  size="$(wc -c < "${dump}" | tr -d ' ')"
  if [[ "${size}" -lt "${NEO4J_MIN_DUMP_BYTES}" ]]; then
    fail "Neo4j: ${dump} tiene ${size} bytes (< ${NEO4J_MIN_DUMP_BYTES}); dump sospechoso"
  else
    log "Neo4j: ${NEO4J_DATABASE}.dump OK (${size} bytes)"
  fi
  warn "Neo4j: la validacion completa de un dump requiere cargarlo (deploy/backup/restore.sh --target neo4j)"
}

case "${TARGET}" in
  postgres) verify_postgres ;;
  mongo) verify_mongo ;;
  neo4j) verify_neo4j ;;
  all)
    verify_postgres
    verify_mongo
    verify_neo4j
    ;;
esac

echo
if [[ "${FAILURES}" -gt 0 ]]; then
  printf '[verify-backup][FALLO] %s comprobacion(es) fallaron en %s\n' "${FAILURES}" "${BACKUP_DIR}" >&2
  exit 1
fi

log "OK: todas las comprobaciones pasaron en ${BACKUP_DIR}"
