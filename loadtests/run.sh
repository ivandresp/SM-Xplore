#!/usr/bin/env bash
set -euo pipefail
#
# loadtests/run.sh - lanzador de las pruebas de carga k6 del stack SM-Xplore (SPEC P1-8).
#
# Levanta un contenedor efimero de k6 conectado a la red smxplore-stack (o a
# host.docker.internal con --target local) y monta loadtests/ en /scripts.
# Los resumenes JSON se escriben en loadtests/results/.
#
# Uso:
#   ./run.sh [recommendations|catalog|flows|all] [opciones]
#
# Opciones:
#   --target docker|local   docker = nombres de servicio en smxplore-stack (default)
#                           local  = host.docker.internal y puertos publicados en 127.0.0.1
#   --scenario smoke|load   escenario de k6 (default: smoke)
#   --vus N                 VUs concurrentes (default: 1 en smoke, 10 en load)
#   --duration 60s          duracion de la meseta en load (default: 60s)
#   --image IMAGEN          imagen de k6 (default: grafana/k6:0.54.0)
#   -h, --help              muestra esta ayuda
#
# Ejemplos:
#   ./run.sh recommendations --scenario load --vus 20 --duration 2m
#   ./run.sh catalog --target local
#   ./run.sh all --scenario load --vus 10
#
# Requisitos: Docker Desktop encendido y el stack levantado (docker compose up -d --wait).
# Git Bash (Windows) o bash (Linux/WSL) para ejecutar este script.

SCRIPT="recommendations"
TARGET="docker"
SCENARIO="smoke"
VUS=""
DURATION=""
K6_IMAGE="${K6_IMAGE:-grafana/k6:0.54.0}"

ADMIN_USER="${ADMIN_USER:-admin@smxplore.local}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:-ChangeMe123!}"

usage() {
  cat <<'EOF'
loadtests/run.sh - lanzador de las pruebas de carga k6 del stack SM-Xplore (SPEC P1-8).

Uso:
  ./run.sh [recommendations|catalog|flows|all] [opciones]

Opciones:
  --target docker|local   docker = nombres de servicio en smxplore-stack (default)
                          local  = host.docker.internal y puertos publicados en 127.0.0.1
  --scenario smoke|load   escenario de k6 (default: smoke)
  --vus N                 VUs concurrentes (default: 1 en smoke, 10 en load)
  --duration 60s          duracion de la meseta en load (default: 60s)
  --image IMAGEN          imagen de k6 (default: grafana/k6:0.54.0)
  -h, --help              muestra esta ayuda

Ejemplos:
  ./run.sh recommendations --scenario load --vus 20 --duration 2m
  ./run.sh catalog --target local
  ./run.sh all --scenario load --vus 10

Requisitos: Docker Desktop encendido y el stack levantado (docker compose up -d --wait).
EOF
}

while [ $# -gt 0 ]; do
  case "$1" in
    recommendations|catalog|flows|all) SCRIPT="$1" ;;
    --target) TARGET="${2:?falta el valor de --target}"; shift ;;
    --scenario) SCENARIO="${2:?falta el valor de --scenario}"; shift ;;
    --vus) VUS="${2:?falta el valor de --vus}"; shift ;;
    --duration) DURATION="${2:?falta el valor de --duration}"; shift ;;
    --image) K6_IMAGE="${2:?falta el valor de --image}"; shift ;;
    -h|--help) usage; exit 0 ;;
    *) echo "Opcion desconocida: $1" >&2; usage >&2; exit 2 ;;
  esac
  shift
done

case "$SCENARIO" in
  smoke|load) ;;
  *) echo "--scenario debe ser smoke o load (recibido: $SCENARIO)" >&2; exit 2 ;;
esac
case "$TARGET" in
  docker|local) ;;
  *) echo "--target debe ser docker o local (recibido: $TARGET)" >&2; exit 2 ;;
esac

if ! command -v docker >/dev/null 2>&1; then
  echo "Docker no esta en el PATH. Enciende Docker Desktop y reintenta." >&2
  exit 1
fi

# VUs por defecto: 1 en smoke, 10 en load (el usuario puede sobreescribirlo).
if [ -z "$VUS" ]; then
  if [ "$SCENARIO" = "smoke" ]; then VUS=1; else VUS=10; fi
fi
if ! [[ "$VUS" =~ ^[0-9]+$ ]] || [ "$VUS" -lt 1 ]; then
  echo "--vus debe ser un entero >= 1 (recibido: $VUS)" >&2
  exit 2
fi
if [ -z "$DURATION" ]; then DURATION="60s"; fi

REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
LOADTESTS_DIR="${REPO_ROOT}/loadtests"

# Docker en Windows necesita rutas estilo C:/... aunque el shell use /c/...
if command -v cygpath >/dev/null 2>&1; then
  HOST_REPO_ROOT="$(cygpath -w "$REPO_ROOT" | tr '\\' '/')"
else
  HOST_REPO_ROOT="$REPO_ROOT"
fi

RESULTS_DIR="${LOADTESTS_DIR}/results"
mkdir -p "$RESULTS_DIR"

if [ "$TARGET" = "docker" ]; then
  if ! docker network inspect smxplore-stack >/dev/null 2>&1; then
    echo "No existe la red smxplore-stack. Levanta el stack con: docker compose up -d --wait" >&2
    exit 1
  fi
  NETWORK_ARGS=(--network smxplore-stack)
  AUTH_URL="http://ms-01:8081"
  MS03_URL="http://ms-03:8082"
  MS04_URL="http://ms-04:8080"
  MS05_URL="http://ms-05:8080"
else
  # host.docker.internal funciona en Docker Desktop; en Linux se declara con host-gateway.
  NETWORK_ARGS=(--add-host=host.docker.internal:host-gateway)
  AUTH_URL="http://host.docker.internal:8081"
  MS03_URL="http://host.docker.internal:8082"
  MS04_URL="http://host.docker.internal:8084"
  MS05_URL="http://host.docker.internal:8085"
fi

run_script() {
  local name="$1"
  local -a env_args=(
    -e "SCENARIO=${SCENARIO}"
    -e "VUS=${VUS}"
    -e "DURATION=${DURATION}"
    -e "AUTH_BASE_URL=${AUTH_URL}"
    -e "ADMIN_USER=${ADMIN_USER}"
    -e "ADMIN_PASSWORD=${ADMIN_PASSWORD}"
  )

  case "$name" in
    recommendations) env_args+=(-e "BASE_URL=${MS05_URL}") ;;
    catalog) env_args+=(-e "BASE_URL=${MS03_URL}" -e "SITES_BASE_URL=${MS04_URL}") ;;
    flows)
      env_args+=(
        -e "BASE_URL=${MS03_URL}"
        -e "SITES_BASE_URL=${MS04_URL}"
        -e "ITERATIONS=1"
      )
      ;;
    *)
      echo "Script desconocido: $name" >&2
      exit 2
      ;;
  esac

  local stamp
  stamp="$(date +%Y%m%d-%H%M%S)"
  local summary="/results/${name}-${SCENARIO}-${stamp}.json"

  echo "== k6 ${name} (escenario ${SCENARIO}, target ${TARGET}, VUs ${VUS}) =="
  docker run --rm \
    "${NETWORK_ARGS[@]}" \
    -v "${HOST_REPO_ROOT}/loadtests:/scripts" \
    -v "${HOST_REPO_ROOT}/loadtests/results:/results" \
    "${env_args[@]}" \
    "${K6_IMAGE}" run --summary-export "$summary" "/scripts/k6/${name}.js"
  echo "Resumen JSON: ${HOST_REPO_ROOT}/loadtests/results/${name}-${SCENARIO}-${stamp}.json"
}

case "$SCRIPT" in
  all)
    run_script recommendations
    run_script catalog
    run_script flows
    ;;
  *)
    run_script "$SCRIPT"
    ;;
esac
