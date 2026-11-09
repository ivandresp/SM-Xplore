#!/bin/sh
# =============================================================================
# SM-Xplore - arranque del gateway: prepara los certificados TLS
# =============================================================================
# La imagen oficial de nginx ejecuta los scripts de /docker-entrypoint.d/ antes
# de lanzar nginx. Este script garantiza que /etc/nginx/certs/tls.crt y
# tls.key existan:
#
#   TLS_SELF_SIGNED=true  (desarrollo, por defecto)
#       Si faltan los archivos, genera un certificado autofirmado valido para
#       localhost. Si ya existen (volumen persistente) no los toca.
#
#   TLS_SELF_SIGNED=false (produccion)
#       Exige certificados reales, inyectados como secretos de Docker. Si no
#       estan, aborta el arranque: es mejor fallar a exponer HTTP sin TLS
#       creyendo que hay TLS.
#
# Variables:
#   TLS_SELF_SIGNED   true | false (default: true)
#   TLS_CN            CN del certificado autofirmado (default: localhost)
#   TLS_DAYS          dias de validez del autofirmado (default: 825)
# =============================================================================
set -eu

TLS_SELF_SIGNED="${TLS_SELF_SIGNED:-true}"
TLS_CN="${TLS_CN:-localhost}"
TLS_DAYS="${TLS_DAYS:-825}"
CERT_DIR="/etc/nginx/certs"
CERT_FILE="${CERT_DIR}/tls.crt"
KEY_FILE="${CERT_DIR}/tls.key"

log() {
    echo "[smxplore-gateway] $*" >&2
}

if [ -s "${CERT_FILE}" ] && [ -s "${KEY_FILE}" ]; then
    log "certificados TLS presentes en ${CERT_DIR} (no se regeneran)"
    exit 0
fi

if [ "${TLS_SELF_SIGNED}" = "true" ]; then
    log "generando certificado autofirmado de desarrollo para CN=${TLS_CN}"
    # PLAT-12: el contenedor corre como usuario nginx (sin privilegios); en
    # desarrollo compose.yaml monta /etc/nginx/certs como tmpfs escribible.
    mkdir -p "${CERT_DIR}" 2>/dev/null || true
    if [ ! -w "${CERT_DIR}" ]; then
        log "ERROR: ${CERT_DIR} no es escribible por el usuario $(id -u)."
        log "       En desarrollo, monta /etc/nginx/certs como tmpfs (ver compose.yaml)."
        exit 1
    fi
    rm -f "${CERT_FILE}" "${KEY_FILE}"
    openssl req -x509 -nodes -newkey rsa:2048 -days "${TLS_DAYS}" -sha256 \
        -subj "/CN=${TLS_CN}" \
        -addext "subjectAltName=DNS:${TLS_CN},DNS:gateway,DNS:localhost,IP:127.0.0.1" \
        -keyout "${KEY_FILE}" \
        -out "${CERT_FILE}" 2>/dev/null
    chmod 600 "${KEY_FILE}"
    chmod 644 "${CERT_FILE}"
    log "certificado autofirmado listo (valido ${TLS_DAYS} dias)"
    exit 0
fi

log "ERROR: TLS_SELF_SIGNED=false pero faltan ${CERT_FILE} o ${KEY_FILE}."
log "       Inyecta los certificados reales como secretos (ver deploy/gateway/README.md)."
exit 1
