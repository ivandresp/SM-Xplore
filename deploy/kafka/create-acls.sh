#!/usr/bin/env bash
#
# Crea los usuarios SCRAM-SHA-512 y las ACLs minimas del broker de produccion
# (PLAT-01). Es idempotente: se puede ejecutar en cada arranque de `kafka-init`.
#
# Uso:
#   deploy/kafka/create-acls.sh [opciones]
#
# Opciones:
#   --bootstrap-server <host:puerto>  Broker. Por defecto $KAFKA_BOOTSTRAP y, si
#                                     no, kafka:9092 (servicio del compose).
#   --command-config <ruta>           client.properties del usuario ADMIN
#                                     (debe ser super.usuarios del broker). Por
#                                     defecto $KAFKA_CLIENT_CONFIG.
#   --list                            Solo lista las ACLs de cada principal.
#   --dry-run                         Muestra los comandos sin ejecutarlos.
#   -h | --help                       Muestra esta ayuda.
#
# Credenciales (variables de entorno; passwords con `openssl rand -hex 16`):
#   KAFKA_MS02_USERNAME / KAFKA_MS02_PASSWORD   servicio ms-02
#   KAFKA_MS03_USERNAME / KAFKA_MS03_PASSWORD   servicio ms-03
#   KAFKA_MS04_USERNAME / KAFKA_MS04_PASSWORD   servicio ms-04
#   KAFKA_MS05_USERNAME / KAFKA_MS05_PASSWORD   servicio ms-05
#   KAFKA_DLQ_REPLAY_USERNAME / KAFKA_DLQ_REPLAY_PASSWORD   operacion (replay DLQ)
#   KAFKA_EXPORTER_USERNAME / KAFKA_EXPORTER_PASSWORD       observabilidad
#
# Matriz de ACLs (minima, sin comodines sobre topicos de negocio):
#   ms-02  escribe security.report.changed.
#   ms-03  escribe activities.activity.changed, activities.event.changed,
#          bookings.reservation.changed, reviews.review.changed y
#          catalog.site.changed.DLT; lee catalog.site.changed (grupo ms-03-site-events).
#   ms-04  escribe catalog.category.changed, catalog.site.changed,
#          catalog.service.changed, catalog.zone.changed,
#          bookings.reservation.changed y reviews.review.changed; lee
#          bookings.reservation.changed (grupo ms-04-notifications).
#   ms-05  lee los 7 topicos de catalogo/actividades/resenas (grupo
#          ms-05-recommendations) y escribe sus .dlq.
#   dlq-replay  lee los DLQ/DLT y escribe en los 8 topicos de negocio.
#   kafka-exporter  Describe sobre topicos, grupos y cluster.
#
# Todos los productores reciben IdempotentWrite sobre el cluster (el cliente
# Kafka de Spring Boot activa enable.idempotence por defecto).
#
# Permisos requeridos: el principal del --command-config debe ser super usuario
# (User:admin, configurado como KAFKA_SUPER_USERS en el compose).
#
set -euo pipefail

BOOTSTRAP_SERVER="${KAFKA_BOOTSTRAP:-kafka:9092}"
CLIENT_CONFIG="${KAFKA_CLIENT_CONFIG:-}"
DRY_RUN="false"
LIST_ONLY="false"
KAFKA_CONTAINER="${KAFKA_CONTAINER:-kafka}"

MS02_USERNAME="${KAFKA_MS02_USERNAME:-ms-02}"
MS02_PASSWORD="${KAFKA_MS02_PASSWORD:-}"
MS03_USERNAME="${KAFKA_MS03_USERNAME:-ms-03}"
MS03_PASSWORD="${KAFKA_MS03_PASSWORD:-}"
MS04_USERNAME="${KAFKA_MS04_USERNAME:-ms-04}"
MS04_PASSWORD="${KAFKA_MS04_PASSWORD:-}"
MS05_USERNAME="${KAFKA_MS05_USERNAME:-ms-05}"
MS05_PASSWORD="${KAFKA_MS05_PASSWORD:-}"
DLQ_REPLAY_USERNAME="${KAFKA_DLQ_REPLAY_USERNAME:-dlq-replay}"
DLQ_REPLAY_PASSWORD="${KAFKA_DLQ_REPLAY_PASSWORD:-}"
EXPORTER_USERNAME="${KAFKA_EXPORTER_USERNAME:-kafka-exporter}"
EXPORTER_PASSWORD="${KAFKA_EXPORTER_PASSWORD:-}"

# Topicos por servicio (duplicados a proposito: el script es la matriz de
# permisos y no debe depender de topics.env, que puede crecer sin cambiar ACLs).
MS03_PRODUCES="activities.activity.changed activities.event.changed bookings.reservation.changed reviews.review.changed"
MS03_CONSUMES="catalog.site.changed"
MS03_GROUP="ms-03-site-events"
MS03_DLT="catalog.site.changed.DLT"
MS04_PRODUCES="catalog.category.changed catalog.site.changed catalog.service.changed catalog.zone.changed bookings.reservation.changed reviews.review.changed"
MS04_CONSUMES="bookings.reservation.changed"
MS04_GROUP="ms-04-notifications"
MS05_CONSUMES="catalog.category.changed catalog.site.changed catalog.service.changed catalog.zone.changed activities.activity.changed activities.event.changed reviews.review.changed"
MS05_GROUP="ms-05-recommendations"
BUSINESS_TOPICS="catalog.category.changed catalog.site.changed catalog.service.changed catalog.zone.changed activities.activity.changed activities.event.changed reviews.review.changed bookings.reservation.changed security.report.changed"
DLQ_TOPICS="catalog.site.changed.DLT catalog.site.changed.dlq catalog.service.changed.dlq catalog.category.changed.dlq catalog.zone.changed.dlq activities.event.changed.dlq activities.activity.changed.dlq reviews.review.changed.dlq kafka.dlq.bookings.reservation.changed"
# MS04-11: topic de descarte del listener de notificaciones de ms-04 (no cuelga de un
# .dlq de ms-05; el replay necesita --target bookings.reservation.changed).
MS04_DLQ_TOPIC="${KAFKA_NOTIFICATIONS_DLQ_TOPIC:-kafka.dlq.bookings.reservation.changed}"
DLQ_REPLAY_GROUP="kafka-dlq-replay"

usage() {
    awk 'NR > 1 { if ($0 == "set -euo pipefail") exit; sub(/^# ?/, ""); print }' "${BASH_SOURCE[0]}"
}

fail() {
    echo "ERROR: $*" >&2
    exit 1
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --bootstrap-server) BOOTSTRAP_SERVER="${2:?--bootstrap-server requiere un valor}"; shift 2 ;;
        --command-config)   CLIENT_CONFIG="${2:?--command-config requiere una ruta}"; shift 2 ;;
        --list)             LIST_ONLY="true"; shift ;;
        --dry-run)          DRY_RUN="true"; shift ;;
        -h|--help)          usage; exit 0 ;;
        *)                  fail "opcion desconocida: $1 (usa --help)" ;;
    esac
done

# Un password va dentro del valor JAAS entre comillas dobles: los caracteres
# especiales rompen la configuracion o permiten inyectar propiedades. Los
# passwords se generan con `openssl rand -hex 16`.
validate_secret() {
    local label="$1" value="$2"
    [[ -n "${value}" ]] || fail "falta el password de ${label} (KAFKA_*_PASSWORD)"
    case "${value}" in
        *'"'*|*'\'*|*';'*|*$'\n'*|*' '*)
            fail "el password de ${label} contiene caracteres no permitidos (\" \\ ; espacio o salto de linea)"
            ;;
    esac
}

validate_user() {
    local label="$1" value="$2"
    [[ -n "${value}" ]] || fail "el usuario de ${label} no puede estar vacio"
    case "${value}" in
        *'"'*|*'\'*|*';'*|*$'\n'*|*' '*)
            fail "el usuario de ${label} contiene caracteres no permitidos"
            ;;
    esac
}

validate_user "ms-02" "${MS02_USERNAME}"; validate_secret "ms-02" "${MS02_PASSWORD}"
validate_user "ms-03" "${MS03_USERNAME}"; validate_secret "ms-03" "${MS03_PASSWORD}"
validate_user "ms-04" "${MS04_USERNAME}"; validate_secret "ms-04" "${MS04_PASSWORD}"
validate_user "ms-05" "${MS05_USERNAME}"; validate_secret "ms-05" "${MS05_PASSWORD}"
validate_user "dlq-replay" "${DLQ_REPLAY_USERNAME}"; validate_secret "dlq-replay" "${DLQ_REPLAY_PASSWORD}"
validate_user "kafka-exporter" "${EXPORTER_USERNAME}"; validate_secret "kafka-exporter" "${EXPORTER_PASSWORD}"

# Resolucion de binarios: mismo criterio que create-topics.sh.
IN_CONTAINER="false"
if [[ -n "${KAFKA_BIN_DIR:-}" && -x "${KAFKA_BIN_DIR}/kafka-acls.sh" ]]; then
    :
elif [[ -x /opt/kafka/bin/kafka-acls.sh ]]; then
    KAFKA_BIN_DIR=/opt/kafka/bin
elif command -v kafka-acls.sh >/dev/null 2>&1; then
    KAFKA_BIN_DIR="$(cd -- "$(dirname -- "$(command -v kafka-acls.sh)")" && pwd)"
elif command -v docker >/dev/null 2>&1 && docker ps --format '{{.Names}}' 2>/dev/null | grep -qx "${KAFKA_CONTAINER}"; then
    KAFKA_BIN_DIR=/opt/kafka/bin
    IN_CONTAINER="true"
else
    fail "no encuentro kafka-acls.sh: define KAFKA_BIN_DIR, anadelo al PATH o arranca el contenedor '${KAFKA_CONTAINER}'"
fi

run_kafka() {
    local binary="$1"
    shift
    if [[ "${IN_CONTAINER}" == "true" ]]; then
        docker exec -i "${KAFKA_CONTAINER}" "${KAFKA_BIN_DIR}/${binary}" "$@"
    else
        "${KAFKA_BIN_DIR}/${binary}" "$@"
    fi
}

common_args=(--bootstrap-server "${BOOTSTRAP_SERVER}")
if [[ -n "${CLIENT_CONFIG}" ]]; then
    common_args+=(--command-config "${CLIENT_CONFIG}")
fi

# El broker con autorizacion rechaza al admin si el client.properties no es de
# un super usuario: se avisa antes de intentar crear nada.
if [[ -z "${CLIENT_CONFIG}" && "${DRY_RUN}" != "true" ]]; then
    fail "falta --command-config con credenciales admin (o KAFKA_CLIENT_CONFIG); el broker de produccion exige autenticacion"
fi

# Permiso de escritura: kafka-acls puede ejecutarse como super usuario.
if [[ "${DRY_RUN}" != "true" && "${LIST_ONLY}" != "true" && ! -f "${CLIENT_CONFIG}" ]]; then
    fail "no existe el --command-config: ${CLIENT_CONFIG}"
fi

echo "Broker:        ${BOOTSTRAP_SERVER}"
echo "Credenciales:  ${CLIENT_CONFIG:-<sin client.properties>}"
echo "Comando:       ${KAFKA_BIN_DIR} (en contenedor: ${IN_CONTAINER})"
echo "Modo:          $([[ "${DRY_RUN}" == "true" ]] && echo 'dry-run' || { [[ "${LIST_ONLY}" == "true" ]] && echo 'listado' || echo 'real'; })"
echo

ensure_user() {
    local user="$1" password="$2"
    if [[ "${DRY_RUN}" == "true" ]]; then
        printf 'kafka-configs.sh %s --alter --add-config SCRAM-SHA-512=[password=***] --entity-type users --entity-name %s\n' "${common_args[*]}" "${user}"
        return 0
    fi
    run_kafka kafka-configs.sh "${common_args[@]}" --alter \
        --add-config "SCRAM-SHA-512=[password=${password}]" \
        --entity-type users --entity-name "${user}" >/dev/null
    echo "  usuario ${user}: SCRAM-SHA-512 asegurado"
}

allow() {
    local principal="$1"
    shift
    if [[ "${DRY_RUN}" == "true" ]]; then
        printf 'kafka-acls.sh %s --add --allow-principal User:%s %s\n' "${common_args[*]}" "${principal}" "$*"
        return 0
    fi
    run_kafka kafka-acls.sh "${common_args[@]}" --add --allow-principal "User:${principal}" "$@" >/dev/null
}

list_principal() {
    local principal="$1"
    echo "--- ACLs de ${principal}"
    run_kafka kafka-acls.sh "${common_args[@]}" --list --principal "User:${principal}" 2>&1 | sed 's/^/  /' || true
}

# --- Usuarios SCRAM ---------------------------------------------------------
echo "Usuarios SCRAM:"
ensure_user "${MS02_USERNAME}" "${MS02_PASSWORD}"
ensure_user "${MS03_USERNAME}" "${MS03_PASSWORD}"
ensure_user "${MS04_USERNAME}" "${MS04_PASSWORD}"
ensure_user "${MS05_USERNAME}" "${MS05_PASSWORD}"
ensure_user "${DLQ_REPLAY_USERNAME}" "${DLQ_REPLAY_PASSWORD}"
ensure_user "${EXPORTER_USERNAME}" "${EXPORTER_PASSWORD}"
echo

if [[ "${LIST_ONLY}" == "true" ]]; then
    echo "ACLs por principal:"
    list_principal "${MS02_USERNAME}"
    list_principal "${MS03_USERNAME}"
    list_principal "${MS04_USERNAME}"
    list_principal "${MS05_USERNAME}"
    list_principal "${DLQ_REPLAY_USERNAME}"
    list_principal "${EXPORTER_USERNAME}"
    exit 0
fi

echo "ACLs:"

# ms-02: publica security.report.changed (alertas y notificaciones).
allow "${MS02_USERNAME}" --producer --topic security.report.changed
allow "${MS02_USERNAME}" --operation IdempotentWrite --cluster

# ms-03: actividades, eventos, reservas y resenas; consume catalog.site.changed.
for topic in ${MS03_PRODUCES}; do
    allow "${MS03_USERNAME}" --producer --topic "${topic}"
done
allow "${MS03_USERNAME}" --producer --topic "${MS03_DLT}"
allow "${MS03_USERNAME}" --operation IdempotentWrite --cluster
for topic in ${MS03_CONSUMES}; do
    allow "${MS03_USERNAME}" --consumer --topic "${topic}" --group "${MS03_GROUP}"
done

# ms-04: catalogo, reservas de servicios y resenas; consume reservas.
for topic in ${MS04_PRODUCES}; do
    allow "${MS04_USERNAME}" --producer --topic "${topic}"
done
allow "${MS04_USERNAME}" --operation IdempotentWrite --cluster
# Escritura en el DLQ propio de notificaciones (MS04-11).
allow "${MS04_USERNAME}" --producer --topic "${MS04_DLQ_TOPIC}"
for topic in ${MS04_CONSUMES}; do
    allow "${MS04_USERNAME}" --consumer --topic "${topic}" --group "${MS04_GROUP}"
done

# ms-05: lee los 7 topicos de su interes y escribe los DLQ de descarte.
for topic in ${MS05_CONSUMES}; do
    allow "${MS05_USERNAME}" --consumer --topic "${topic}" --group "${MS05_GROUP}"
done
for topic in ${MS05_CONSUMES}; do
    allow "${MS05_USERNAME}" --producer --topic "${topic}.dlq"
done
allow "${MS05_USERNAME}" --operation IdempotentWrite --cluster

# Operacion: replay de DLQ (lee los DLQ/DLT y re-publica en el topico original).
for topic in ${DLQ_TOPICS}; do
    allow "${DLQ_REPLAY_USERNAME}" --consumer --topic "${topic}" --group "${DLQ_REPLAY_GROUP}"
done
for topic in ${BUSINESS_TOPICS}; do
    allow "${DLQ_REPLAY_USERNAME}" --producer --topic "${topic}"
done
allow "${DLQ_REPLAY_USERNAME}" --operation IdempotentWrite --cluster

# Observabilidad: kafka-exporter solo necesita Describe (nunca datos).
allow "${EXPORTER_USERNAME}" --operation Describe --topic '*'
allow "${EXPORTER_USERNAME}" --operation Describe --group '*'
allow "${EXPORTER_USERNAME}" --operation Describe --cluster

echo
if [[ "${DRY_RUN}" == "true" ]]; then
    echo "Dry-run: no se ha cambiado nada. Revisa los comandos anteriores."
else
    echo "Listo: usuarios SCRAM y ACLs minimas aseguradas (idempotente)."
    echo "Verifica con: $0 --list --command-config <admin-client.properties>"
fi
