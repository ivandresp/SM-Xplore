#!/usr/bin/env bash
# ---------------------------------------------------------------------------
# PLAT-01 - Genera la CA y el material TLS (keystore/truststore PKCS12) del
# broker Kafka de produccion.
#
# El compose de produccion (deploy/prod/compose.yaml) arranca el broker con
# INTERNAL y CONTROLLER en SASL_SSL:
#   listener.security.protocol.map = INTERNAL:SASL_SSL,CONTROLLER:SASL_SSL
#   ssl.keystore.location  = /etc/kafka/secrets/kafka.keystore.p12
#   ssl.truststore.location= /etc/kafka/secrets/kafka.truststore.p12
# Los servicios y kafka-init validan el broker con el mismo truststore; el CA en
# PEM (kafka-ca.crt) lo usa kafka-exporter (sarama no lee PKCS12).
#
# Uso:
#   deploy/kafka/gen-tls.sh [--out DIR] [--days N] [--force]
#                           [--keystore-password P] [--truststore-password P]
#
#   --out DIR                  Directorio de salida (default: deploy/kafka/certs).
#   --days N                   Validez del certificado del broker (default: 825).
#   --keystore-password P      Password del keystore PKCS12. Si se omite se
#                              toma de KAFKA_KEYSTORE_PASSWORD o se genera uno.
#   --truststore-password P    Password del truststore PKCS12. Si se omite se
#                              toma de KAFKA_TRUSTSTORE_PASSWORD o se genera uno.
#   --force                    Sobrescribe el material existente.
#
# Salida (en deploy/kafka/certs/):
#   kafka-ca.crt            CA publica (0444)         -> KAFKA_CA_FILE
#   kafka-ca.key            clave privada de la CA (0400, no distribuir)
#   kafka.keystore.p12      clave + certificado del broker (0400) -> KAFKA_KEYSTORE_FILE
#   kafka.truststore.p12    CA como truststore PKCS12 (0444)      -> KAFKA_TRUSTSTORE_FILE
#   kafka-server.crt/.key   certificado y clave sueltos (inspeccion/rotacion)
#
# SANs del certificado del broker: DNS:kafka, DNS:localhost, IP:127.0.0.1.
# El cliente se conecta a kafka:9092 y el broker se valida a si mismo por
# controller.quorum.voters/INTERNAL (kafka:9093), por eso el SAN DNS:kafka.
# Un cluster multi-broker necesita un certificado por nodo con sus SANs
# (kafka-1, kafka-2, ...): este script genera el de un unico broker KRaft.
#
# IMPORTANTE: deploy/kafka/certs/ contiene claves privadas; esta en .gitignore.
# No regeneres el material con --force sobre un stack desplegado: hay que
# recrear el broker y todos los clientes a la vez.
# ---------------------------------------------------------------------------
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd -- "${SCRIPT_DIR}/../.." && pwd)"

OUT_DIR="${REPO_ROOT}/deploy/kafka/certs"
DAYS=825
FORCE=0
KEYSTORE_PASSWORD="${KAFKA_KEYSTORE_PASSWORD:-}"
TRUSTSTORE_PASSWORD="${KAFKA_TRUSTSTORE_PASSWORD:-}"

SERVER_SANS="DNS:kafka,DNS:localhost,IP:127.0.0.1"

usage() {
  cat <<'EOF'
Uso: deploy/kafka/gen-tls.sh [opciones]

  --out DIR                  Directorio de salida (default: deploy/kafka/certs).
  --days N                   Validez del certificado del broker (default: 825).
  --keystore-password P      Password del keystore (o KAFKA_KEYSTORE_PASSWORD).
  --truststore-password P    Password del truststore (o KAFKA_TRUSTSTORE_PASSWORD).
  --force                    Sobrescribe el material existente.
  -h, --help                 Muestra esta ayuda.

Salida: kafka-ca.crt, kafka-ca.key, kafka.keystore.p12, kafka.truststore.p12,
kafka-server.crt y kafka-server.key.
EOF
}

log() { printf '[kafka-gen-tls] %s\n' "$*"; }
warn() { printf '[kafka-gen-tls][AVISO] %s\n' "$*" >&2; }
die() { printf '[kafka-gen-tls][ERROR] %s\n' "$*" >&2; exit 1; }

while [[ $# -gt 0 ]]; do
  case "$1" in
    --out)                 OUT_DIR="${2:?falta el valor de --out}"; shift 2 ;;
    --out=*)               OUT_DIR="${1#*=}"; shift ;;
    --days)                DAYS="${2:?falta el valor de --days}"; shift 2 ;;
    --days=*)              DAYS="${1#*=}"; shift ;;
    --keystore-password)   KEYSTORE_PASSWORD="${2:?falta el valor}"; shift 2 ;;
    --keystore-password=*) KEYSTORE_PASSWORD="${1#*=}"; shift ;;
    --truststore-password) TRUSTSTORE_PASSWORD="${2:?falta el valor}"; shift 2 ;;
    --truststore-password=*) TRUSTSTORE_PASSWORD="${1#*=}"; shift ;;
    --force|-f)            FORCE=1; shift ;;
    -h|--help)             usage; exit 0 ;;
    *)                     die "opcion no reconocida: $1 (usa --help)" ;;
  esac
done

command -v openssl >/dev/null 2>&1 || die "openssl no esta disponible en PATH"
[[ -n "${OUT_DIR}" ]] || die "--out no puede estar vacio"
case "${DAYS}" in
  ''|*[!0-9]*) die "--days debe ser un entero positivo (recibido: ${DAYS})" ;;
esac
[[ "${DAYS}" -ge 1 ]] || die "--days debe ser >= 1"

GENERATED_KEYSTORE_PASSWORD=0
GENERATED_TRUSTSTORE_PASSWORD=0
if [[ -z "${KEYSTORE_PASSWORD}" ]]; then
  KEYSTORE_PASSWORD="$(openssl rand -hex 16)"
  GENERATED_KEYSTORE_PASSWORD=1
fi
if [[ -z "${TRUSTSTORE_PASSWORD}" ]]; then
  TRUSTSTORE_PASSWORD="$(openssl rand -hex 16)"
  GENERATED_TRUSTSTORE_PASSWORD=1
fi
if [[ "${#KEYSTORE_PASSWORD}" -lt 6 || "${#TRUSTSTORE_PASSWORD}" -lt 6 ]]; then
  die "los passwords de keystore/truststore deben tener al menos 6 caracteres"
fi

CA_CERT="${OUT_DIR}/kafka-ca.crt"
CA_KEY="${OUT_DIR}/kafka-ca.key"
SERVER_KEY="${OUT_DIR}/kafka-server.key"
SERVER_CSR="${OUT_DIR}/kafka-server.csr"
SERVER_CERT="${OUT_DIR}/kafka-server.crt"
KEYSTORE="${OUT_DIR}/kafka.keystore.p12"
TRUSTSTORE="${OUT_DIR}/kafka.truststore.p12"

if [[ "${FORCE}" -ne 1 ]]; then
  for existing in "${CA_CERT}" "${CA_KEY}" "${KEYSTORE}" "${TRUSTSTORE}"; do
    if [[ -e "${existing}" ]]; then
      die "ya existe ${existing}; usa --force para regenerar TODO el material TLS"
    fi
  done
fi

umask 077
mkdir -p "${OUT_DIR}"
chmod 700 "${OUT_DIR}" 2>/dev/null || warn "no se pudo aplicar chmod 700 a ${OUT_DIR}"

TMP_DIR="$(mktemp -d)"
cleanup() {
  # Los passwords viajan por -passout/-passin: no se escriben en disco fuera de TMP_DIR.
  rm -rf "${TMP_DIR}"
}
trap cleanup EXIT

log "generando CA autofirmada (validez 3650 dias)"
# Se usan archivos de configuracion en lugar de -subj/-addext: en Git Bash
# (MSYS) los valores que empiezan por "/" se convierten a rutas de Windows y
# rompen el subject de openssl. Con -config funciona igual en Linux, macOS y
# Git Bash.
cat > "${TMP_DIR}/ca.cnf" <<EOF
[req]
distinguished_name = ca_dn
prompt = no
x509_extensions = v3_ca

[ca_dn]
CN = SM-Xplore Kafka Internal CA
O = SM-Xplore

[v3_ca]
basicConstraints = critical,CA:TRUE,pathlen:0
keyUsage = critical,keyCertSign,cRLSign
EOF

openssl genrsa -out "${TMP_DIR}/ca.key" 4096
openssl req -x509 -new -sha256 -key "${TMP_DIR}/ca.key" -days 3650 \
  -config "${TMP_DIR}/ca.cnf" -extensions v3_ca \
  -out "${TMP_DIR}/ca.crt"

log "generando clave y certificado del broker (SANs: ${SERVER_SANS})"
cat > "${TMP_DIR}/server.cnf" <<EOF
[req]
distinguished_name = server_dn
prompt = no
req_extensions = v3_server

[server_dn]
CN = kafka
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

log "creando keystore y truststore PKCS12 (los leen el broker y los clientes Java)"
openssl pkcs12 -export \
  -in "${TMP_DIR}/server.crt" -inkey "${TMP_DIR}/server.key" -certfile "${TMP_DIR}/ca.crt" \
  -name kafka -out "${TMP_DIR}/kafka.keystore.p12" \
  -passout "pass:${KEYSTORE_PASSWORD}"

openssl pkcs12 -export \
  -in "${TMP_DIR}/ca.crt" -nokeys \
  -name kafka-ca -out "${TMP_DIR}/kafka.truststore.p12" \
  -passout "pass:${TRUSTSTORE_PASSWORD}"

# Comprobacion de que ambos almacenes se abren con sus passwords.
openssl pkcs12 -info -noout -in "${TMP_DIR}/kafka.keystore.p12" \
  -passin "pass:${KEYSTORE_PASSWORD}" >/dev/null 2>&1 \
  || die "no se pudo reabrir el keystore generado"
openssl pkcs12 -info -noout -in "${TMP_DIR}/kafka.truststore.p12" \
  -passin "pass:${TRUSTSTORE_PASSWORD}" >/dev/null 2>&1 \
  || die "no se pudo reabrir el truststore generado"

install -m 0444 "${TMP_DIR}/ca.crt" "${CA_CERT}"
install -m 0400 "${TMP_DIR}/ca.key" "${CA_KEY}"
install -m 0400 "${TMP_DIR}/server.key" "${SERVER_KEY}"
install -m 0444 "${TMP_DIR}/server.csr" "${SERVER_CSR}"
install -m 0444 "${TMP_DIR}/server.crt" "${SERVER_CERT}"
install -m 0400 "${TMP_DIR}/kafka.keystore.p12" "${KEYSTORE}"
install -m 0444 "${TMP_DIR}/kafka.truststore.p12" "${TRUSTSTORE}"

log "OK. Material TLS en ${OUT_DIR}:"
printf '  %-26s %s\n' \
  "kafka-ca.crt" "CA publica (KAFKA_CA_FILE, PEM para exporters)" \
  "kafka-ca.key" "clave de la CA (no distribuir)" \
  "kafka.keystore.p12" "keystore del broker (KAFKA_KEYSTORE_FILE)" \
  "kafka.truststore.p12" "truststore de clientes (KAFKA_TRUSTSTORE_FILE)"

echo
echo "SANs del certificado del broker:"
openssl x509 -in "${SERVER_CERT}" -noout -ext subjectAltName || true

if [[ "${GENERATED_KEYSTORE_PASSWORD}" -eq 1 ]]; then
  warn "password de keystore generado (anotalo en .env.prod, NO se guarda en disco):"
  printf '  KAFKA_KEYSTORE_PASSWORD=%s\n' "${KEYSTORE_PASSWORD}"
fi
if [[ "${GENERATED_TRUSTSTORE_PASSWORD}" -eq 1 ]]; then
  warn "password de truststore generado (anotalo en .env.prod, NO se guarda en disco):"
  printf '  KAFKA_TRUSTSTORE_PASSWORD=%s\n' "${TRUSTSTORE_PASSWORD}"
fi

cat <<EOF

Siguientes pasos (stack de produccion):
  1. Copia el material a deploy/prod/secrets/kafka/ (o apunta a deploy/kafka/certs):
       install -m 0444 ${CA_CERT} deploy/prod/secrets/kafka/kafka-ca.crt
       install -m 0400 ${KEYSTORE} deploy/prod/secrets/kafka/kafka.keystore.p12
       install -m 0444 ${TRUSTSTORE} deploy/prod/secrets/kafka/kafka.truststore.p12
  2. En deploy/prod/.env.prod:
       KAFKA_CA_FILE=./secrets/kafka/kafka-ca.crt
       KAFKA_KEYSTORE_FILE=./secrets/kafka/kafka.keystore.p12
       KAFKA_TRUSTSTORE_FILE=./secrets/kafka/kafka.truststore.p12
       KAFKA_KEYSTORE_PASSWORD=<el de arriba>
       KAFKA_TRUSTSTORE_PASSWORD=<el de arriba>
  3. Genera los usuarios SCRAM y sus ACLs: los crea 'kafka-init' al arrancar
     (deploy/kafka/create-acls.sh) con las credenciales KAFKA_*_PASSWORD de
     .env.prod. Ver deploy/prod/README.md, seccion Kafka.
  4. No regeneres el material con --force sobre un stack ya desplegado: cambia
     la CA y todos los clientes dejan de validar el broker; hay que recrear
     broker y servicios a la vez.
EOF
