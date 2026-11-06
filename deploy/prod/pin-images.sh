#!/usr/bin/env bash
# Pineado de imágenes de producción (P1-5).
#
# Resuelve el digest de las cinco imágenes publicadas por `.github/workflows/release-images.yml`
# y reescribe `deploy/prod/images.env` con referencias `repo@sha256:...`. Es idempotente: cada
# ejecución deja el archivo completo con el tag y los digests actuales de ese tag.
#
# Uso:
#   deploy/prod/pin-images.sh <tag>            # por ejemplo v1.2.3 o 1.2.3 o <sha>
#   IMAGES_ENV=/tmp/images.env deploy/prod/pin-images.sh v1.2.3
#
# Requiere docker con el plugin buildx; no necesita credenciales si las imágenes son públicas
# (si son privadas: `docker login ghcr.io` antes).
set -euo pipefail

REGISTRY="${REGISTRY:-ghcr.io}"
IMAGE_OWNER="${IMAGE_OWNER:-ivandresp}"

readonly SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
readonly OUTPUT_FILE="${IMAGES_ENV:-$SCRIPT_DIR/images.env}"

# variable de entorno -> nombre del repositorio en GHCR
readonly SERVICES=(
  "MS01_IMAGE:sm-xplore-ms-01"
  "MS02_IMAGE:sm-xplore-ms-02"
  "MS03_IMAGE:sm-xplore-ms-03"
  "MS04_IMAGE:sm-xplore-ms-04"
  "MS05_IMAGE:sm-xplore-ms-05"
)

usage() {
  sed -n '2,14p' "${BASH_SOURCE[0]}" | sed 's/^# \{0,1\}//'
}

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
  echo "# Imágenes de producción de SM-Xplore pineadas por digest (P1-5)."
  echo "#"
  echo "# Cada línea es la referencia completa \`repo@sha256:...\` que consume deploy/prod/compose.yaml."
  echo "# No edites este archivo a mano: regenéralo con \`deploy/prod/pin-images.sh <tag>\` después de"
  echo "# publicar un release (\`.github/workflows/release-images.yml\`)."
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

mv -f "$tmp_file" "$OUTPUT_FILE"
trap - EXIT
printf '\nOK: %s actualizado con el tag %s\n' "$OUTPUT_FILE" "$tag"
