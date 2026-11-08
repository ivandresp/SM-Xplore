#!/usr/bin/env bash
#
# Materializa un client.properties de Kafka con SASL_SSL + SCRAM-SHA-512 a
# partir de variables de entorno (PLAT-01).
#
# Por que existe: el compose de produccion no puede escribir el contenido de un
# secreto dentro de un `sasl.jaas.config`, y los scripts de Kafka solo aceptan
# `--command-config <archivo>`. Este script convierte variables de entorno
# (que Compose inyecta desde .env.prod) en un archivo 0600 dentro del
# contenedor, sin dejarlo en la imagen ni en el repo.
#
# Uso:
#   deploy/kafka/render-client-config.sh --out /tmp/admin-client.properties
#
# Opciones (cada una tiene su variable de entorno equivalente):
#   --out <ruta>                 Archivo de salida (KAFKA_CLIENT_CONFIG).
#   --username <user>            Usuario SCRAM (KAFKA_CLIENT_USERNAME).
#   --password-stdin             Lee el password SCRAM de stdin (PLAT-11: no lo pases por argv,
#                                queda en el historial del shell y en `ps`). Tambien vale
#                                KAFKA_CLIENT_PASSWORD.
#   --truststore <ruta>          Truststore PKCS12 (KAFKA_TRUSTSTORE_LOCATION,
#                                por defecto /etc/kafka/secrets/kafka.truststore.p12).
#   --truststore-password <pass> Password del truststore (KAFKA_TRUSTSTORE_PASSWORD).
#   --truststore-type <tipo>     Tipo del truststore (KAFKA_TRUSTSTORE_TYPE, PKCS12).
#   --protocol <proto>           Protocolo (KAFKA_SECURITY_PROTOCOL, SASL_SSL).
#   --mechanism <mec>            Mecanismo (KAFKA_SASL_MECHANISM, SCRAM-SHA-512).
#   --force                      Sobrescribe el archivo si ya existe.
#   -h | --help                  Muestra esta ayuda.
#
# El archivo generado nunca se imprime: el resumen muestra usuario, mecanismo y
# rutas, jamas el password.
#
set -euo pipefail

OUT="${KAFKA_CLIENT_CONFIG:-/tmp/kafka-client.properties}"
USERNAME="${KAFKA_CLIENT_USERNAME:-}"
PASSWORD="${KAFKA_CLIENT_PASSWORD:-}"
TRUSTSTORE="${KAFKA_TRUSTSTORE_LOCATION:-/etc/kafka/secrets/kafka.truststore.p12}"
TRUSTSTORE_PASSWORD="${KAFKA_TRUSTSTORE_PASSWORD:-}"
TRUSTSTORE_TYPE="${KAFKA_TRUSTSTORE_TYPE:-PKCS12}"
PROTOCOL="${KAFKA_SECURITY_PROTOCOL:-SASL_SSL}"
MECHANISM="${KAFKA_SASL_MECHANISM:-SCRAM-SHA-512}"
FORCE="false"

usage() {
    awk 'NR > 1 { if ($0 == "set -euo pipefail") exit; sub(/^# ?/, ""); print }' "${BASH_SOURCE[0]}"
}

fail() {
    echo "ERROR: $*" >&2
    exit 1
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --out)                  OUT="${2:?--out requiere una ruta}"; shift 2 ;;
        --username)             USERNAME="${2:?--username requiere un valor}"; shift 2 ;;
        --password-stdin)       read -r PASSWORD || true; shift ;;
        --truststore)           TRUSTSTORE="${2:?--truststore requiere una ruta}"; shift 2 ;;
        --truststore-password)  TRUSTSTORE_PASSWORD="${2:?--truststore-password requiere un valor}"; shift 2 ;;
        --truststore-type)      TRUSTSTORE_TYPE="${2:?--truststore-type requiere un valor}"; shift 2 ;;
        --protocol)             PROTOCOL="${2:?--protocol requiere un valor}"; shift 2 ;;
        --mechanism)            MECHANISM="${2:?--mechanism requiere un valor}"; shift 2 ;;
        --force)                FORCE="true"; shift ;;
        -h|--help)              usage; exit 0 ;;
        *)                      fail "opcion desconocida: $1 (usa --help)" ;;
    esac
done

[[ -n "${OUT}" ]] || fail "--out no puede estar vacio"
[[ -n "${USERNAME}" ]] || fail "falta el usuario SCRAM (--username o KAFKA_CLIENT_USERNAME)"
[[ -n "${PASSWORD}" ]] || fail "falta el password SCRAM (KAFKA_CLIENT_PASSWORD o --password-stdin)"
[[ -n "${TRUSTSTORE_PASSWORD}" ]] || fail "falta el password del truststore (KAFKA_TRUSTSTORE_PASSWORD)"
[[ -f "${TRUSTSTORE}" ]] || fail "no existe el truststore: ${TRUSTSTORE}"

# El password viaja dentro de un valor JAAS entre comillas dobles: un caracter
# especial romperia la configuracion y, peor, permitiria inyectar propiedades
# adicionales en el archivo. Los passwords se generan con `openssl rand -hex 16`
# (ver deploy/kafka/gen-tls.sh y .env.prod.example), asi que restringirlos a un
# juego seguro no limita al operador.
case "${PASSWORD}" in
    *'"'*|*'\'*|*';'*|*$'\n'*|*' '*)
        fail "el password SCRAM contiene caracteres no permitidos (\" \\ ; espacio o salto de linea)"
        ;;
esac
case "${USERNAME}" in
    *'"'*|*'\'*|*';'*|*$'\n'*|*' '*|"")
        fail "el usuario SCRAM contiene caracteres no permitidos"
        ;;
esac

if [[ -e "${OUT}" && "${FORCE}" != "true" ]]; then
    fail "ya existe ${OUT}; usa --force para sobrescribirlo"
fi

out_dir="$(dirname -- "${OUT}")"
mkdir -p "${out_dir}"
umask 077
tmp_out="$(mktemp "${out_dir}/.kafka-client.XXXXXX")"
trap 'rm -f "${tmp_out}"' EXIT

cat > "${tmp_out}" <<EOF
# Generado por deploy/kafka/render-client-config.sh - NO versionar.
# Usuario: ${USERNAME}
security.protocol=${PROTOCOL}
sasl.mechanism=${MECHANISM}
sasl.jaas.config=org.apache.kafka.common.security.scram.ScramLoginModule required username="${USERNAME}" password="${PASSWORD}";
ssl.truststore.location=${TRUSTSTORE}
ssl.truststore.password=${TRUSTSTORE_PASSWORD}
ssl.truststore.type=${TRUSTSTORE_TYPE}
ssl.endpoint.identification.algorithm=https
EOF

chmod 600 "${tmp_out}"
mv -f "${tmp_out}" "${OUT}"
trap - EXIT

echo "client.properties listo: ${OUT}"
echo "  usuario:     ${USERNAME}"
echo "  protocolo:   ${PROTOCOL} (${MECHANISM})"
echo "  truststore:  ${TRUSTSTORE} (${TRUSTSTORE_TYPE})"
echo "  permisos:    0600"
