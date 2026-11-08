#!/usr/bin/env bash
# Pineado de imágenes de producción (P1-5, ampliado en PLAT-13).
#
# Resuelve el digest de las cinco imágenes de los servicios publicadas por
# `.github/workflows/release-images.yml` y de las imágenes de infraestructura y del gateway,
# y reescribe `deploy/prod/images.env` con referencias `repo@sha256:...`. Es idempotente.
#
# Uso:
#   deploy/prod/pin-images.sh <tag>              # tag de los servicios (v1.2.3, 1.2.3 o <sha>)
#   IMAGES_ENV=/tmp/images.env deploy/prod/pin-images.sh v1.2.3
#   deploy/prod/pin-images.sh <tag> --apps-only  # solo las cinco imágenes de servicios
#
# Las imágenes de infraestructura se pinean en el tag por defecto de `.env.prod.example`.
# Para cambiar la version, exporta la referencia antes de ejecutar, por ejemplo:
#   POSTGRES_REF=postgres:17.4-alpine deploy/prod/pin-images.sh v1.2.3
#
# Requiere docker con el plugin buildx; no necesita credenciales si las imágenes son públicas
# (si son privadas: `docker login ghcr.io` antes). Sin tags flotantes en produccion (PLAT-13).
set -euo pipefail

REGISTRY="${REGISTRY:-ghcr.io}"
IMAGE_OWNER="${IMAGE_OWNER:-ivandresp}"

readonly SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly OUTPUT_FILE="${IMAGES_ENV:-$SCRIPT_DIR/images.env}"

# variable de entorno -> nombre del repositorio en GHCR (se publican con el tag del release)
readonly SERVICES=(
  "MS01_IMAGE:sm-xplore-ms-01"
  "MS02_IMAGE:sm-xplore-ms-02"
  "MS03_IMAGE:sm-xplore-ms-03"
  "MS04_IMAGE:sm-xplore-ms-04"
  "MS05_IMAGE:sm-xplore-ms-05"
)

# variable de entorno -> referencia completa (PLAT-13). El tag se puede sobreescribir por entorno
# con <NOMBRE>_REF para actualizar una dependencia sin tocar este script.
readonly INFRA_IMAGES=(
  "POSTGRES_IMAGE:${POSTGRES_REF:-postgres:17-alpine}"
  "MONGO_IMAGE:${MONGO_REF:-mongo:8.2}"
  "REDIS_IMAGE:${REDIS_REF:-redis:7-alpine}"
  "NEO4J_IMAGE:${NEO4J_REF:-neo4j:5.26-community}"
  "KAFKA_IMAGE:${KAFKA_REF:-apache/kafka:4.1.0}"
  "KAFKA_EXPORTER_IMAGE:${KAFKA_EXPORTER_REF:-danielqsj/kafka-exporter:v1.9.0}"
  "POSTGRES_EXPORTER_IMAGE:${POSTGRES_EXPORTER_REF:-prometheuscommunity/postgres-exporter:v0.17.1}"
  "REDIS_EXPORTER_IMAGE:${REDIS_EXPORTER_REF:-oliver006/redis_exporter:v1.67.0}"
  "PROMETHEUS_IMAGE:${PROMETHEUS_REF:-prom/prometheus:v3.1.0}"
  "GATEWAY_IMAGE:${GATEWAY_REF:-nginxinc/nginx-unprivileged:1.27-alpine}"
)

usage() {
  sed -n '2,18p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
}

APPS_ONLY=0
for arg in "$@"; do
  case "$arg" in
    --apps-only) APPS_ONLY=1 ;;
    -h|--help) usage; exit 0 ;;
  esac
done

if [[ "${1:-}" == "-h" || "${1:-}" == "--help" ]]; then
  usage
  exit 0
fi

tag="${1:-}"
if [[ -z "$tag" ]]; then
  echo "error: falta el tag a pinear (por ejemplo v1.2.3)" >&2
  usage >&2
  exit 2
fi
if [[ ! "$tag" =~ ^[A-Za-z0-9._-]+$ ]]; then
  echo "error: tag inválido '$tag' (solo letras, números, punto, guion y guion bajo)" >&2
  exit 2
fi

command -v docker >/dev/null 2>&1 || {
  echo "error: docker no está en el PATH" >&2
  echo "       el pineado se hace contra el registro real, no contra imágenes locales" >&2
  exit 1
}
docker buildx version >/dev/null 2>&1 || {
  echo "error: falta el plugin docker buildx" >&2
  exit 1
}

tmp_file="$(mktemp)"
trap 'rm -f "$tmp_file"' EXIT

{
  echo "# Imágenes de producción de SM-Xplore pineadas por digest (P1-5, infra y gateway en PLAT-13)."
  echo "#"
  echo "# Cada línea es la referencia completa \`repo@sha256:...\` que consume deploy/prod/compose.yaml."
  echo "# No edites este archivo a mano: regenéralo con \`deploy/prod/pin-images.sh <tag>\` después de"
  echo "# publicar un release (\`.github/workflows/release-images.yml\`) o al actualizar una dependencia."
  echo "#"
  echo "# \`--apps-only\` solo escribe las cinco imágenes de servicios (MS01_IMAGE..MS05_IMAGE)."
  echo "#"
  echo "# Los placeholders \`sha256:REPLACE_ME\` hacen fallar el \`docker compose pull\`; es intencional,"
  echo "# así nadie despliega una imagen flotante por accidente."
  echo "#"
  echo "# tag de referencia: $tag"
} > "$tmp_file"

printf '%s\n' "Resolviendo digests para el tag '$tag' en $REGISTRY/$IMAGE_OWNER:"
printf '%-12s %-28s %s\n' "VARIABLE" "REPOSITORIO" "DIGEST"

for entry in "${SERVICES[@]}"; do
  variable="${entry%%:*}"
  service="${entry##*:}"
  repository="$REGISTRY/$IMAGE_OWNER/$service"
  reference="$repository:$tag"

  if ! digest="$(docker buildx imagetools inspect "$reference" --format '{{.Manifest.Digest}}' 2>/dev/null | tr -d '[:space:]"')"; then
    echo "error: no se pudo inspeccionar $reference" >&2
    exit 1
  fi
  if [[ ! "$digest" =~ ^sha256:[0-9a-f]{64}$ ]]; then
    echo "error: digest inesperado para $reference: '${digest:-<vacío>}'" >&2
    exit 1
  fi

  printf '%s=%s@%s\n' "$variable" "$repository" "$digest" >> "$tmp_file"
  printf '%-12s %-28s %s\n' "$variable" "$service" "$digest"
done

if [[ "$APPS_ONLY" -eq 0 ]]; then
  echo
  printf '%s\n' "Resolviendo digests de infraestructura y gateway:"
  for entry in "${INFRA_IMAGES[@]}"; do
    variable="${entry%%:*}"
    reference="${entry#*:}"

    # Si el operador ya paso un digest (repo@sha256:...), se respeta tal cual.
    if [[ "$reference" == *"@"* ]]; then
      printf '%s=%s\n' "$variable" "$reference" >> "$tmp_file"
      printf '%-26s %-52s %s\n' "$variable" "$reference" "(digest explicito)"
      continue
    fi

    if ! digest="$(docker buildx imagetools inspect "$reference" --format '{{.Manifest.Digest}}' 2>/dev/null | tr -d '[:space:]"')"; then
      echo "error: no se pudo inspeccionar $reference" >&2
      exit 1
    fi
    if [[ ! "$digest" =~ ^sha256:[0-9a-f]{64}$ ]]; then
      echo "error: digest inesperado para $reference: '${digest:-<vacío>}'" >&2
      exit 1
    fi

    printf '%s=%s@%s\n' "$variable" "$reference" "$digest" >> "$tmp_file"
    printf '%-26s %-52s %s\n' "$variable" "$reference" "$digest"
  done
fi

mv -f "$tmp_file" "$OUTPUT_FILE"
trap - EXIT
printf '\nOK: %s actualizado con el tag %s\n' "$OUTPUT_FILE" "$tag"
