#!/usr/bin/env bash
#
# Re-publica en el topico original los mensajes acumulados en un topico DLQ/DLT.
#
# Uso:
#   scripts/kafka-dlq-replay.sh --dlq <topico> [opciones]
#
# Opciones:
#   --bootstrap-server <host:puerto>  Broker. Por defecto $KAFKA_BOOTSTRAP; si no,
#                                     localhost:29092 en el host y localhost:9092
#                                     cuando se ejecuta dentro del contenedor.
#   --dlq <topico>                    Topico de descarte de origen (obligatorio).
#   --target <topico>                 Topico destino. Si se omite se deduce
#                                     quitando el sufijo .dlq o .DLT.
#   --max <n>                         Re-publica como mucho n mensajes.
#   --idle-timeout-ms <ms>            Tiempo de espera sin mensajes para cerrar
#                                     el consumidor (por defecto 10000).
#   --dry-run                         Consume y cuenta, pero no re-publica.
#   -h | --help                       Muestra esta ayuda.
#
# Formato preservado: key y value viajan tal cual. El consumidor imprime
# "key<TAB>value" y el productor parsea por el mismo separador, asi que el value
# no puede contener tabs (los eventos del contrato son JSON de una sola linea).
# La clave se conserva porque ms-05 exige key = aggregateId.
#
# Garantias:
#   - At-least-once: no hay deduplicacion. Los consumidores deben ser
#     idempotentes (ms-05 deduplica por eventId; ms-03 aplica snapshots).
#   - Cada ejecucion empieza desde el principio del topico DLQ (--from-beginning):
#     los mensajes no se borran de la DLQ. Usa --max para acotar y revisa antes
#     con --dry-run.
#   - Solo usa utilidades de Kafka (kafka-console-consumer / kafka-console-producer);
#     no requiere jq, python ni acceso al disco del broker.
#
# Ejemplos:
#   scripts/kafka-dlq-replay.sh --dlq reviews.review.changed.dlq --dry-run
#   scripts/kafka-dlq-replay.sh --dlq catalog.site.changed.DLT --max 100
#   scripts/kafka-dlq-replay.sh --dlq reviews.review.changed.dlq \
#       --bootstrap-server kafka:9092 --max 50
#
set -euo pipefail

BOOTSTRAP_SERVER="${KAFKA_BOOTSTRAP:-}"
DLQ_TOPIC=""
TARGET_TOPIC=""
MAX_MESSAGES=""
IDLE_TIMEOUT_MS="${KAFKA_DLQ_IDLE_TIMEOUT_MS:-10000}"
DRY_RUN="false"

# Separador de key/value en el pipeline. Un tab no aparece en los UUID ni en el
# JSON compacto del contrato, asi que el round-trip es seguro.
KEY_SEPARATOR=$'\t'

usage() {
    sed -n '2,45p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
}

fail() {
    echo "ERROR: $*" >&2
    exit 1
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --bootstrap-server)
            BOOTSTRAP_SERVER="${2:?--bootstrap-server requiere un valor}"
            shift 2
            ;;
        --dlq)
            DLQ_TOPIC="${2:?--dlq requiere un topico}"
            shift 2
            ;;
        --target)
            TARGET_TOPIC="${2:?--target requiere un topico}"
            shift 2
            ;;
        --max)
            MAX_MESSAGES="${2:?--max requiere un entero}"
            shift 2
            ;;
        --idle-timeout-ms)
            IDLE_TIMEOUT_MS="${2:?--idle-timeout-ms requiere un entero}"
            shift 2
            ;;
        --dry-run)
            DRY_RUN="true"
            shift
            ;;
        -h|--help)
            usage
            exit 0
            ;;
        *)
            fail "opcion desconocida: $1 (usa --help)"
            ;;
    esac
done

[[ -n "${DLQ_TOPIC}" ]] || fail "--dlq es obligatorio"
if [[ -n "${MAX_MESSAGES}" && ! "${MAX_MESSAGES}" =~ ^[0-9]+$ ]]; then
    fail "--max debe ser un entero positivo"
fi
if [[ ! "${IDLE_TIMEOUT_MS}" =~ ^[0-9]+$ ]]; then
    fail "--idle-timeout-ms debe ser un entero en milisegundos"
fi

# Deduce el destino si no se indico: quita el sufijo de descarte.
if [[ -z "${TARGET_TOPIC}" ]]; then
    case "${DLQ_TOPIC}" in
        *.dlq) TARGET_TOPIC="${DLQ_TOPIC%.dlq}" ;;
        *.DLT) TARGET_TOPIC="${DLQ_TOPIC%.DLT}" ;;
        *) fail "no puedo deducir el topico destino desde '${DLQ_TOPIC}': usa --target <topico>" ;;
    esac
    [[ -n "${TARGET_TOPIC}" ]] || fail "el topico destino quedo vacio tras quitar el sufijo"
else
    [[ "${TARGET_TOPIC}" != "${DLQ_TOPIC}" ]] || fail "--target no puede coincidir con --dlq"
fi

# Resuelve las utilidades de Kafka igual que create-topics.sh.
KAFKA_CONTAINER="${KAFKA_CONTAINER:-kafka}"
IN_CONTAINER="false"
CONSUMER_CMD=()
PRODUCER_CMD=()

if [[ -n "${KAFKA_BIN_DIR:-}" && -x "${KAFKA_BIN_DIR}/kafka-console-consumer.sh" ]]; then
    CONSUMER_CMD=("${KAFKA_BIN_DIR}/kafka-console-consumer.sh")
    PRODUCER_CMD=("${KAFKA_BIN_DIR}/kafka-console-producer.sh")
elif [[ -x /opt/kafka/bin/kafka-console-consumer.sh ]]; then
    # Imagen apache/kafka: scripts en /opt/kafka/bin, fuera del PATH.
    CONSUMER_CMD=(/opt/kafka/bin/kafka-console-consumer.sh)
    PRODUCER_CMD=(/opt/kafka/bin/kafka-console-producer.sh)
elif command -v kafka-console-consumer.sh >/dev/null 2>&1; then
    CONSUMER_CMD=("$(command -v kafka-console-consumer.sh)")
    PRODUCER_CMD=("$(command -v kafka-console-producer.sh)")
elif command -v docker >/dev/null 2>&1 && docker ps --format '{{.Names}}' 2>/dev/null | grep -qx "${KAFKA_CONTAINER}"; then
    CONSUMER_CMD=(docker exec -i "${KAFKA_CONTAINER}" /opt/kafka/bin/kafka-console-consumer.sh)
    PRODUCER_CMD=(docker exec -i "${KAFKA_CONTAINER}" /opt/kafka/bin/kafka-console-producer.sh)
    IN_CONTAINER="true"
else
    fail "no encuentro kafka-console-consumer.sh: define KAFKA_BIN_DIR, anadelo al PATH o arranca el contenedor '${KAFKA_CONTAINER}'"
fi

if [[ -z "${BOOTSTRAP_SERVER}" ]]; then
    if [[ "${IN_CONTAINER}" == "true" ]]; then
        BOOTSTRAP_SERVER="localhost:9092"
    else
        BOOTSTRAP_SERVER="localhost:29092"
    fi
fi

consumer_args=(--bootstrap-server "${BOOTSTRAP_SERVER}" --topic "${DLQ_TOPIC}"
               --from-beginning --timeout-ms "${IDLE_TIMEOUT_MS}"
               --property print.key=true --property print.value=true
               --property key.separator="${KEY_SEPARATOR}")
if [[ -n "${MAX_MESSAGES}" ]]; then
    consumer_args+=(--max-messages "${MAX_MESSAGES}")
fi

producer_args=(--bootstrap-server "${BOOTSTRAP_SERVER}" --topic "${TARGET_TOPIC}"
               --property parse.key=true --property key.separator="${KEY_SEPARATOR}")

echo "Broker:      ${BOOTSTRAP_SERVER}"
echo "DLQ origen:  ${DLQ_TOPIC}"
echo "Destino:     ${TARGET_TOPIC}"
echo "Limite:      $([[ -n "${MAX_MESSAGES}" ]] && echo "${MAX_MESSAGES} mensajes" || echo 'sin limite (hasta idle timeout)')"
echo "Modo:        $([[ "${DRY_RUN}" == "true" ]] && echo 'dry-run (no se re-publica)' || echo 'real')"
echo

payload_file="$(mktemp)"
trap 'rm -f "${payload_file}"' EXIT

echo "Consumiendo ${DLQ_TOPIC} desde el principio..."
# El consumidor termina por --max-messages o por --timeout-ms; en ambos casos
# puede salir con codigo distinto de cero, y eso no es un fallo del replay.
set +e
"${CONSUMER_CMD[@]}" "${consumer_args[@]}" >"${payload_file}" 2>/dev/null
consumer_status=$?
set -e

replayed=0
if [[ -s "${payload_file}" ]]; then
    replayed="$(wc -l <"${payload_file}" | tr -d ' ')"
fi

if [[ "${DRY_RUN}" == "true" ]]; then
    echo "Dry-run: ${replayed} mensajes listos para re-publicar (no se ha escrito nada)."
    exit 0
fi

if [[ "${replayed}" -eq 0 ]]; then
    echo "No hay mensajes nuevos en ${DLQ_TOPIC}; nada que re-publicar."
    exit 0
fi

"${PRODUCER_CMD[@]}" "${producer_args[@]}" <"${payload_file}"

echo "Re-publicados ${replayed} mensajes de ${DLQ_TOPIC} a ${TARGET_TOPIC}."
echo "Nota: la DLQ no se vacia. Si repites el comando, se repiten los mensajes."
if [[ "${consumer_status}" -ne 0 && -z "${MAX_MESSAGES}" ]]; then
    echo "El consumidor cerro por idle timeout (codigo ${consumer_status}); es el comportamiento esperado."
fi
