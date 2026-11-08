#!/usr/bin/env bash
#
# Crea de forma idempotente los topicos de Kafka declarados en topics.env.
#
# Uso:
#   deploy/kafka/create-topics.sh [opciones]
#
# Opciones:
#   --bootstrap-server <host:puerto>  Broker al que conectarse. Por defecto
#                                     $KAFKA_BOOTSTRAP y, si no esta definido,
#                                     kafka:9092 (el nombre del servicio Kafka del
#                                     compose). En local desde el host, el listener
#                                     externo es localhost:29092.
#   --topics-file <ruta>              Archivo de declaracion. Por defecto
#                                     $TOPICS_FILE y, si no esta definido, el
#                                     topics.env que vive junto a este script.
#   --replication-factor <n>          Fuerza el factor de replicacion para todos
#                                     los topicos (cluster multi-broker).
#   --command-config <ruta>           client.properties con las credenciales
#                                     (SASL_SSL + SCRAM-SHA-512 en produccion).
#                                     Por defecto $KAFKA_CLIENT_CONFIG. Sin este
#                                     archivo el broker con autenticacion rechaza
#                                     la conexion (PLAT-01).
#   --dry-run                         Muestra los comandos sin ejecutarlos.
#   -h | --help                       Muestra esta ayuda.
#
# El script es idempotente: usa --if-not-exists, asi que un topico ya creado no
# se toca ni provoca error. Sale con codigo 0 cuando todos los topicos existen.
#
# Lo invoca deploy/prod/compose.yaml como servicio one-shot `kafka-init`, que
# primero materializa el client.properties del usuario admin con
# deploy/kafka/render-client-config.sh y despues ejecuta este script:
#   bash /scripts/create-topics.sh --bootstrap-server kafka:9092 \
#     --command-config /tmp/admin-client.properties
# con deploy/kafka montado en /scripts y TOPICS_FILE=/scripts/topics.env.
#
# Ejemplos:
#   deploy/kafka/create-topics.sh                                  # stack de Compose
#   deploy/kafka/create-topics.sh --dry-run                        # revisar comandos
#   deploy/kafka/create-topics.sh --bootstrap-server localhost:29092  # desde el host
#   deploy/kafka/create-topics.sh --replication-factor 3           # cluster de 3 brokers
#   deploy/kafka/create-topics.sh --command-config /tmp/admin-client.properties
#
set -euo pipefail

SCRIPT_DIR="$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)"
# TOPICS_FILE permite montar el archivo en otra ruta (lo usa deploy/prod/compose.yaml).
TOPICS_FILE="${TOPICS_FILE:-${SCRIPT_DIR}/topics.env}"
BOOTSTRAP_SERVER="${KAFKA_BOOTSTRAP:-kafka:9092}"
REPLICATION_OVERRIDE="${KAFKA_REPLICATION_FACTOR:-}"
# client.properties con credenciales (SASL_SSL + SCRAM-SHA-512 en produccion).
CLIENT_CONFIG="${KAFKA_CLIENT_CONFIG:-}"
DRY_RUN="false"

usage() {
    awk 'NR > 1 { if ($0 == "set -euo pipefail") exit; sub(/^# ?/, ""); print }' "${BASH_SOURCE[0]}"
}

while [[ $# -gt 0 ]]; do
    case "$1" in
        --bootstrap-server)
            BOOTSTRAP_SERVER="${2:?--bootstrap-server requiere un valor}"
            shift 2
            ;;
        --topics-file)
            TOPICS_FILE="${2:?--topics-file requiere una ruta}"
            shift 2
            ;;
        --replication-factor)
            REPLICATION_OVERRIDE="${2:?--replication-factor requiere un valor}"
            shift 2
            ;;
        --command-config)
            CLIENT_CONFIG="${2:?--command-config requiere una ruta}"
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
            echo "ERROR: opcion desconocida: $1" >&2
            usage >&2
            exit 2
            ;;
    esac
done

if [[ ! -f "${TOPICS_FILE}" ]]; then
    echo "ERROR: no existe el archivo de topicos: ${TOPICS_FILE}" >&2
    exit 1
fi

if [[ -n "${REPLICATION_OVERRIDE}" && ! "${REPLICATION_OVERRIDE}" =~ ^[0-9]+$ ]]; then
    echo "ERROR: --replication-factor debe ser un entero positivo" >&2
    exit 2
fi

# Resuelve el binario de Kafka. Orden de preferencia:
#   1. KAFKA_BIN_DIR (ruta a los scripts de Kafka en el host).
#   2. El mismo script en PATH (imagen de Kafka o instalacion local).
#   3. docker exec en el contenedor del compose (imagen apache/kafka: usa
#      /opt/kafka/bin). El contenedor se puede cambiar con KAFKA_CONTAINER.
KAFKA_TOPICS_CMD=()
IN_CONTAINER="false"
KAFKA_CONTAINER="${KAFKA_CONTAINER:-kafka}"

if [[ -n "${KAFKA_BIN_DIR:-}" && -x "${KAFKA_BIN_DIR}/kafka-topics.sh" ]]; then
    KAFKA_TOPICS_CMD=("${KAFKA_BIN_DIR}/kafka-topics.sh")
elif [[ -x /opt/kafka/bin/kafka-topics.sh ]]; then
    # Imagen apache/kafka: los scripts no estan en PATH, pero si en /opt/kafka/bin.
    KAFKA_TOPICS_CMD=(/opt/kafka/bin/kafka-topics.sh)
elif command -v kafka-topics.sh >/dev/null 2>&1; then
    KAFKA_TOPICS_CMD=("$(command -v kafka-topics.sh)")
elif command -v docker >/dev/null 2>&1 && docker ps --format '{{.Names}}' 2>/dev/null | grep -qx "${KAFKA_CONTAINER}"; then
    KAFKA_TOPICS_CMD=(docker exec -i "${KAFKA_CONTAINER}" /opt/kafka/bin/kafka-topics.sh)
    IN_CONTAINER="true"
elif [[ "${DRY_RUN}" == "true" ]]; then
    # El dry-run solo imprime comandos: sirve para revisar la declaracion en un
    # equipo sin Kafka instalado (CI, portatil sin el contenedor arrancado).
    echo "AVISO: no encuentro kafka-topics.sh; el dry-run imprimira 'kafka-topics.sh' como marcador." >&2
    KAFKA_TOPICS_CMD=("kafka-topics.sh")
else
    echo "ERROR: no encuentro kafka-topics.sh." >&2
    echo "       Define KAFKA_BIN_DIR, anade los scripts de Kafka al PATH o" >&2
    echo "       arranca el contenedor '${KAFKA_CONTAINER}' (docker compose up -d kafka)." >&2
    exit 1
fi

# Valida el client.properties antes de ejecutar nada. Con docker exec la ruta
# se resuelve dentro del contenedor (montada), asi que solo se comprueba en el
# host cuando el comando corre aqui.
if [[ -n "${CLIENT_CONFIG}" ]]; then
    if [[ "${IN_CONTAINER}" == "false" && ! -f "${CLIENT_CONFIG}" ]]; then
        echo "ERROR: no existe el --command-config: ${CLIENT_CONFIG}" >&2
        exit 1
    fi
elif [[ "${DRY_RUN}" != "true" ]]; then
    echo "AVISO: sin --command-config; solo funciona contra un broker sin autenticacion" >&2
fi

echo "Broker:        ${BOOTSTRAP_SERVER}"
echo "Declaracion:   ${TOPICS_FILE}"
echo "Comando:       ${KAFKA_TOPICS_CMD[*]}"
echo "Credenciales:  ${CLIENT_CONFIG:-<sin client.properties>}"
[[ -n "${REPLICATION_OVERRIDE}" ]] && echo "Replicacion:   ${REPLICATION_OVERRIDE} (forzada)"
echo "Modo:          $([[ "${DRY_RUN}" == "true" ]] && echo 'dry-run (no se crea nada)' || echo 'real')"
echo

created=0
declared=0

while IFS= read -r raw_line || [[ -n "${raw_line}" ]]; do
    line="${raw_line%%#*}"
    # Quita espacios al inicio y al final.
    line="$(printf '%s' "${line}" | sed 's/^[[:space:]]*//; s/[[:space:]]*$//')"
    [[ -z "${line}" ]] && continue

    IFS=':' read -r topic partitions replication retention_ms extra <<< "${line}"
    if [[ -z "${topic}" || -z "${partitions}" || -z "${replication}" || -n "${extra:-}" ]]; then
        echo "ERROR: linea con formato invalido (se esperaba topico:particiones:replicacion[:retention.ms]): ${raw_line}" >&2
        exit 1
    fi
    if [[ ! "${partitions}" =~ ^[0-9]+$ || ! "${replication}" =~ ^[0-9]+$ ]]; then
        echo "ERROR: particiones y replicacion deben ser enteros: ${raw_line}" >&2
        exit 1
    fi
    if [[ -n "${retention_ms:-}" && ! "${retention_ms}" =~ ^[0-9]+$ ]]; then
        echo "ERROR: retention.ms debe ser un entero en milisegundos: ${raw_line}" >&2
        exit 1
    fi
    [[ -n "${REPLICATION_OVERRIDE}" ]] && replication="${REPLICATION_OVERRIDE}"

    args=(--bootstrap-server "${BOOTSTRAP_SERVER}" --create --if-not-exists
          --topic "${topic}" --partitions "${partitions}" --replication-factor "${replication}")
    if [[ -n "${CLIENT_CONFIG}" ]]; then
        args+=(--command-config "${CLIENT_CONFIG}")
    fi
    if [[ -n "${retention_ms:-}" ]]; then
        args+=(--config "retention.ms=${retention_ms}")
    fi

    declared=$((declared + 1))
    if [[ "${DRY_RUN}" == "true" ]]; then
        printf '%s %s\n' "${KAFKA_TOPICS_CMD[*]}" "${args[*]}"
        created=$((created + 1))
    else
        if "${KAFKA_TOPICS_CMD[@]}" "${args[@]}" >/dev/null; then
            echo "OK      ${topic} (${partitions} particiones, RF ${replication})"
            created=$((created + 1))
        else
            echo "ERROR   no se pudo crear o verificar ${topic}" >&2
            exit 1
        fi
    fi
done < "${TOPICS_FILE}"

if [[ "${declared}" -eq 0 ]]; then
    echo "ERROR: ${TOPICS_FILE} no declara ningun topico" >&2
    exit 1
fi

echo
if [[ "${DRY_RUN}" == "true" ]]; then
    echo "Dry-run: ${declared} topicos declarados, ninguno creado."
else
    echo "Listo: ${created}/${declared} topicos asegurados (idempotente, --if-not-exists)."
fi
