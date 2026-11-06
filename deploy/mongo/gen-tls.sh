#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# P1-7 - Genera la CA y el certificado de servidor autofirmado del replica set
# MongoDB de produccion.
#
# El compose de produccion arranca los tres nodos con:
#   --tlsMode requireTLS --tlsCertificateKeyFile /tmp/mongo-server.pem
#   --tlsCAFile /tmp/mongo-ca.pem --tlsAllowConnectionsWithoutCertificates
# y los clientes (servicios, mongo-init, probes) validan el servidor con la CA
# (sin certificado de cliente).
#
# Uso:
#   deploy/mongo/gen-tls.sh [--out DIR] [--days N] [--force]
#
#   --out DIR   Directorio de salida (default: deploy/mongo/certs).
#   --days N    Validez del certificado de servidor en dias (default: 825).
#   --force     Sobrescribe los archivos existentes.
#
# Salida (en deploy/mongo/certs/):
#   mongo-ca.pem         CA publica (0444)     -> MONGO_CA_FILE
#   mongo-ca.key         clave privada de la CA (0400)
#   mongo-server.pem     clave + certificado del servidor en un solo PEM (0400)
#   mongo-server.crt     certificado del servidor (0444)
#   mongo-server.key     clave privada del servidor (0400)
#   mongo-server.csr     CSR intermedio (no se versiona; se conserva por trazabilidad)
#
# SANs del certificado de servidor:
#   DNS:rs0, DNS:mongo1, DNS:mongo2, DNS:mongo3, DNS:localhost, IP:127.0.0.1
#   (mongo1/2/3 para el TLS intra-replica-set; localhost y 127.0.0.1 para la
#   excepcion de loopback de mongo-init y para las probes locales).
#
# Integracion con produccion: copiar a deploy/prod/secrets/ manteniendo los
# nombres mongo-ca.pem y mongo-server.pem, o apuntar MONGO_CA_FILE y
# MONGO_SERVER_CERT_FILE de deploy/prod/.env.prod a los archivos generados.
#
# IMPORTANTE: deploy/mongo/certs/ contiene claves privadas. Nunca debe
# versionarse. Anade al .gitignore:
#   deploy/mongo/certs/
#   deploy/prod/secrets/
# ---------------------------------------------------------------------------
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "${SCRIPT_DIR}/../.." && pwd)"

OUT_DIR="${REPO_ROOT}/deploy/mongo/certs"
DAYS=825
FORCE=0

SERVER_SANS="DNS:rs0,DNS:mongo1,DNS:mongo2,DNS:mongo3,DNS:localhost,IP:127.0.0.1"

usage() {
  cat <<'EOF'
Uso: deploy/mongo/gen-tls.sh [--out DIR] [--days N] [--force]

  --out DIR   Directorio de salida (default: deploy/mongo/certs).
  --days N    Validez del certificado de servidor en dias (default: 825).
  --force     Sobrescribe los archivos existentes.
  -h, --help  Muestra esta ayuda.
EOF
}

log() { printf '[gen-tls] %s\n' "$*"; }
warn() { printf '[gen-tls][AVISO] %s\n' "$*" >&2; }
die() { printf '[gen-tls][ERROR] %s\n' "$*" >&2; exit 1; }

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
    --days)
      [[ $# -ge 2 ]] || die "falta el valor de --days"
      DAYS="$2"
      shift 2
      ;;
    --days=*)
      DAYS="${1#*=}"
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
case "${DAYS}" in
  ''|*[!0-9]*) die "--days debe ser un entero positivo (recibido: ${DAYS})" ;;
esac
[[ "${DAYS}" -ge 1 ]] || die "--days debe ser >= 1"

CA_CERT="${OUT_DIR}/mongo-ca.pem"
CA_KEY="${OUT_DIR}/mongo-ca.key"
SERVER_KEY="${OUT_DIR}/mongo-server.key"
SERVER_CSR="${OUT_DIR}/mongo-server.csr"
SERVER_CERT="${OUT_DIR}/mongo-server.crt"
SERVER_PEM="${OUT_DIR}/mongo-server.pem"

if [[ "${FORCE}" -ne 1 ]]; then
  for existing in "${CA_CERT}" "${CA_KEY}" "${SERVER_CERT}" "${SERVER_PEM}"; do
    if [[ -e "${existing}" ]]; then
      die "ya existe ${existing}; usa --force para regenerar TODO el material TLS"
    fi
  done
fi

umask 077
mkdir -p "${OUT_DIR}"
chmod 700 "${OUT_DIR}" 2>/dev/null || warn "no se pudo aplicar chmod 700 a ${OUT_DIR}"

TMP_DIR="$(mktemp -d)"
cleanup() { rm -rf "${TMP_DIR}"; }
trap cleanup EXIT

log "generando CA autofirmada (validez 3650 dias)"
# Se usan archivos de configuracion en lugar de -subj/-addext: en Git Bash
# (MSYS) los valores que empiezan por "/" se convierten a rutas de Windows y
# rompen el subject de openssl. Con -config el script funciona igual en Linux,
# macOS y Git Bash.
cat > "${TMP_DIR}/ca.cnf" <<EOF
[req]
distinguished_name = ca_dn
prompt = no
x509_extensions = v3_ca

[ca_dn]
CN = SM-Xplore MongoDB Internal CA
O = SM-Xplore

[v3_ca]
basicConstraints = critical,CA:TRUE,pathlen:0
keyUsage = critical,keyCertSign,cRLSign
EOF

openssl genrsa -out "${TMP_DIR}/ca.key" 4096
openssl req -x509 -new -sha256 -key "${TMP_DIR}/ca.key" -days 3650 \
  -config "${TMP_DIR}/ca.cnf" -extensions v3_ca \
  -out "${TMP_DIR}/ca.crt"

log "generando clave y certificado de servidor (SANs: ${SERVER_SANS})"
cat > "${TMP_DIR}/server.cnf" <<EOF
[req]
distinguished_name = server_dn
prompt = no
req_extensions = v3_server

[server_dn]
CN = mongo1
O = SM-Xplore

[v3_server]
basicConstraints = critical,CA:FALSE
keyUsage = critical,digitalSignature,keyEncipherment
extendedKeyUsage = serverAuth
subjectAltName = ${SERVER_SANS}
EOF

openssl genrsa -out "${TMP_DIR}/server.key" 4096
openssl req -new -sha256 -key "${TMP_DIR}/server.key" \
  -config "${TMP_DIR}/server.cnf" -out "${TMP_DIR}/server.csr"

openssl x509 -req -sha256 -in "${TMP_DIR}/server.csr" \
  -CA "${TMP_DIR}/ca.crt" -CAkey "${TMP_DIR}/ca.key" -CAcreateserial \
  -days "${DAYS}" -extfile "${TMP_DIR}/server.cnf" -extensions v3_server \
  -out "${TMP_DIR}/server.crt"

log "validando la cadena con la CA"
openssl verify -CAfile "${TMP_DIR}/ca.crt" "${TMP_DIR}/server.crt"

# PEM combinado que exige --tlsCertificateKeyFile de mongod/mongosh.
cat "${TMP_DIR}/server.key" "${TMP_DIR}/server.crt" > "${TMP_DIR}/combined.pem"

install -m 0444 "${TMP_DIR}/ca.crt" "${CA_CERT}"
install -m 0400 "${TMP_DIR}/ca.key" "${CA_KEY}"
install -m 0400 "${TMP_DIR}/server.key" "${SERVER_KEY}"
install -m 0444 "${TMP_DIR}/server.csr" "${SERVER_CSR}"
install -m 0444 "${TMP_DIR}/server.crt" "${SERVER_CERT}"
install -m 0400 "${TMP_DIR}/combined.pem" "${SERVER_PEM}"

log "OK. Material TLS en ${OUT_DIR}:"
printf '  %-24s %s\n' \
  "mongo-ca.pem" "CA publica (MONGO_CA_FILE)" \
  "mongo-ca.key" "clave de la CA (no distribuir)" \
  "mongo-server.pem" "clave+cert servidor (MONGO_SERVER_CERT_FILE)" \
  "mongo-server.crt" "certificado de servidor (inspeccion)" \
  "mongo-server.key" "clave privada del servidor"

cat <<EOF

Detalle de SANs del certificado de servidor:
EOF
openssl x509 -in "${SERVER_CERT}" -noout -ext subjectAltName || true

cat <<EOF

Siguientes pasos (stack de produccion):
  1. Copia los archivos al directorio de secretos del compose:
       install -m 0444 "${CA_CERT}" deploy/prod/secrets/mongo-ca.pem
       install -m 0400 "${SERVER_PEM}" deploy/prod/secrets/mongo-server.pem
     o apunta MONGO_CA_FILE y MONGO_SERVER_CERT_FILE en deploy/prod/.env.prod
     a:
       ${CA_CERT}
       ${SERVER_PEM}
  2. Los tres nodos usan el MISMO par de certificados; no generes uno por nodo.
  3. No regeneres la CA con --force sobre un stack ya desplegado: los clientes
     (servicios y mongo-init) dejarian de validar el servidor y habria que
     recrear los contenedores.
  4. Anade al .gitignore (si no esta ya):
       deploy/mongo/certs/
       deploy/prod/secrets/
EOF
