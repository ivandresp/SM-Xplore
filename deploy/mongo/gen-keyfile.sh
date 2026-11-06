#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# P1-7 - Genera el keyfile de autenticacion interna del replica set MongoDB.
#
# El replica set de produccion (deploy/prod/compose.yaml) arranca los tres
# nodos con `--auth --keyFile`. El keyfile es el secreto compartido con el que
# los miembros se autentican entre si; no se versiona.
#
# Uso:
#   deploy/mongo/gen-keyfile.sh [--out DIR] [--force]
#
#   --out DIR   Directorio de salida (default: deploy/mongo/secrets).
#   --force     Sobrescribe un keyfile existente.
#
# Salida:
#   deploy/mongo/secrets/mongo-keyfile   (base64, permisos 400, ~1 KB)
#
# Integracion con produccion:
#   Copia el archivo a deploy/prod/secrets/mongo-keyfile (o apunta
#   MONGO_KEYFILE_FILE de deploy/prod/.env.prod al archivo generado). El
#   compose lo monta como secreto 0444 y el entrypoint de cada nodo lo copia
#   a /tmp con permisos 400 y propietario mongodb.
#
# IMPORTANTE: deploy/mongo/secrets/ contiene material sensible. Debe quedar
# fuera de git (y de cualquier artefacto que se publique). Anade al .gitignore:
#   deploy/mongo/secrets/
# ---------------------------------------------------------------------------
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "${SCRIPT_DIR}/../.." && pwd)"

OUT_DIR="${REPO_ROOT}/deploy/mongo/secrets"
FORCE=0

usage() {
  cat <<'EOF'
Uso: deploy/mongo/gen-keyfile.sh [--out DIR] [--force]

  --out DIR   Directorio de salida (default: deploy/mongo/secrets).
  --force     Sobrescribe un keyfile existente.
  -h, --help  Muestra esta ayuda.
EOF
}

log() { printf '[gen-keyfile] %s\n' "$*"; }
warn() { printf '[gen-keyfile][AVISO] %s\n' "$*" >&2; }
die() { printf '[gen-keyfile][ERROR] %s\n' "$*" >&2; exit 1; }

while [[ $# -gt 0 ]]; do
  case "$1" in
    --out)
      [[ $# -ge 2 ]] || die "falta el valor de --out"
      OUT_DIR="$2"
      shift 2
      ;;
    --out=*)
      OUT_DIR="${1#*=}"
      shift
      ;;
    --force|-f)
      FORCE=1
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      die "opcion no reconocida: $1 (usa --help)"
      ;;
  esac
done

command -v openssl >/dev/null 2>&1 || die "openssl no esta disponible en PATH"
[[ -n "${OUT_DIR}" ]] || die "--out no puede estar vacio"

KEYFILE="${OUT_DIR}/mongo-keyfile"

if [[ -e "${KEYFILE}" && "${FORCE}" -ne 1 ]]; then
  die "ya existe ${KEYFILE}; usa --force para sobrescribirlo"
fi

umask 077
mkdir -p "${OUT_DIR}"
chmod 700 "${OUT_DIR}" 2>/dev/null || warn "no se pudo aplicar chmod 700 a ${OUT_DIR}"

log "generando keyfile base64 en ${KEYFILE}"
# MongoDB exige un keyfile base64 de entre 6 y 1024 caracteres; 756 bytes de
# entropia (1008 caracteres base64) es el tamanio del tutorial oficial. Se
# normaliza el final de linea a LF: openssl en Git Bash/Windows emite CRLF y el
# CR haria que MongoDB interpretara un keyfile distinto en cada nodo.
openssl rand -base64 756 | tr -d '\r' > "${KEYFILE}"

# Permisos: solo el propietario puede leerlo. El compose lo monta como secreto
# 0444 y el entrypoint lo reinstala con 400/owner mongodb; 400 aqui evita que
# el archivo quede legible en el host.
chmod 400 "${KEYFILE}"

CHARS="$(tr -d '\n' < "${KEYFILE}" | wc -c | tr -d ' ')"
if [[ "${CHARS}" -lt 6 || "${CHARS}" -gt 1024 ]]; then
  rm -f "${KEYFILE}"
  die "keyfile con tamanio inesperado (${CHARS} caracteres); se elimina"
fi

if ! tr -d '\n' < "${KEYFILE}" | grep -qE '^[A-Za-z0-9+/=]+$'; then
  rm -f "${KEYFILE}"
  die "el keyfile generado no es base64 valido; se elimina"
fi

if [[ "${CHARS}" -ne 1008 ]]; then
  warn "keyfile de ${CHARS} caracteres base64 (esperado 1008 para 756 bytes); MongoDB lo acepta si esta entre 6 y 1024"
fi

log "OK: ${KEYFILE} (${CHARS} caracteres base64, permisos $(stat -c '%a' "${KEYFILE}" 2>/dev/null || echo '400'))"

cat <<EOF

Siguientes pasos (stack de produccion):
  1. Copia el keyfile al directorio de secretos del compose, por ejemplo:
       install -m 0444 "${KEYFILE}" deploy/prod/secrets/mongo-keyfile
     o apunta MONGO_KEYFILE_FILE en deploy/prod/.env.prod a:
       ${KEYFILE}
  2. Los tres nodos comparten ESTE keyfile; no generes uno por nodo.
  3. Anade al .gitignore (si no esta ya):
       deploy/mongo/secrets/
       deploy/prod/secrets/
EOF
