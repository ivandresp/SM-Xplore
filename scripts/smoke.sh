#!/usr/bin/env bash
# Verificacion rapida del stack integrado (compose.yaml raiz).
#
# Uso: bash scripts/smoke.sh
# Variables: HOST, MS01_PORT..MS05_PORT, WAIT_SECONDS.
set -uo pipefail

# Si existe .env, respeta los puertos configurados alli.
if [ -f .env ]; then
  set -a
  # shellcheck disable=SC1091
  . ./.env
  set +a
fi

HOST="${HOST:-localhost}"
MS01_PORT="${MS01_PORT:-8081}"
MS02_PORT="${MS02_PORT:-8080}"
MS03_PORT="${MS03_PORT:-8082}"
MS04_PORT="${MS04_PORT:-8084}"
MS05_PORT="${MS05_PORT:-8085}"
KAFKA_UI_PORT="${KAFKA_UI_PORT:-8090}"
MAILPIT_UI_PORT="${MAILPIT_UI_PORT:-8025}"
NEO4J_HTTP_PORT="${NEO4J_HTTP_PORT:-7474}"
WAIT_SECONDS="${WAIT_SECONDS:-300}"

failures=0
pass() { printf '  OK   %s\n' "$1"; }
fail() { printf '  FAIL %s\n' "$1"; failures=$((failures + 1)); }

wait_health() {
  name=$1
  url=$2
  waited=0
  printf 'Esperando %s ...\n' "$name"
  while [ "$waited" -lt "$WAIT_SECONDS" ]; do
    body=$(curl -fsS --max-time 3 "$url" 2>/dev/null || true)
    if printf '%s' "$body" | grep -q '"status":"UP"'; then
      pass "$name -> $url"
      return 0
    fi
    sleep 2
    waited=$((waited + 2))
  done
  fail "$name no respondio UP en ${WAIT_SECONDS}s ($url)"
  return 1
}

echo "== Salud de los servicios =="
wait_health "ms-01" "http://$HOST:$MS01_PORT/actuator/health"
wait_health "ms-02" "http://$HOST:$MS02_PORT/actuator/health"
wait_health "ms-03" "http://$HOST:$MS03_PORT/actuator/health"
wait_health "ms-04" "http://$HOST:$MS04_PORT/actuator/health"
wait_health "ms-05" "http://$HOST:$MS05_PORT/actuator/health"

echo "== JWKS de ms-01 =="
jwks=$(curl -fsS --max-time 5 "http://$HOST:$MS01_PORT/.well-known/jwks.json" 2>/dev/null || true)
if printf '%s' "$jwks" | grep -q '"kty"'; then
  pass "JWKS publica al menos una llave"
else
  fail "JWKS vacio o inaccesible"
fi

echo "== Catalogo publico de ms-03 =="
code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 "http://$HOST:$MS03_PORT/api/v1/activities" || true)
if [ "$code" = "200" ]; then
  pass "GET /api/v1/activities responde 200"
else
  fail "GET /api/v1/activities respondio $code"
fi

code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 "http://$HOST:$MS03_PORT/api/v1/events" || true)
if [ "$code" = "200" ]; then
  pass "GET /api/v1/events responde 200"
else
  fail "GET /api/v1/events respondio $code"
fi

echo "== Autenticacion y JWT entre servicios =="
login_body=$(printf '{"email":"%s","password":"%s"}' \
  "${BOOTSTRAP_ADMIN_EMAIL:-admin@smxplore.local}" "${BOOTSTRAP_ADMIN_PASSWORD:-ChangeMe123!}")
login=$(curl -fsS --max-time 10 -H "Content-Type: application/json" \
  -d "$login_body" "http://$HOST:$MS01_PORT/api/v1/auth/login" 2>/dev/null || true)
token=$(printf '%s' "$login" | sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p')
if [ -n "$token" ]; then
  pass "login del administrador devuelve accessToken"
  code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 -H "Authorization: Bearer $token" "http://$HOST:$MS04_PORT/api/v1/sites" || true)
  if [ "$code" = "200" ]; then
    pass "ms-04 valida el JWT emitido por ms-01 (GET /api/v1/sites)"
  else
    fail "ms-04 rechazo el JWT (codigo $code)"
  fi
else
  fail "no se pudo iniciar sesion con el administrador dev"
fi

echo "== Herramientas =="
for entry in "Kafka UI:$KAFKA_UI_PORT" "Mailpit:$MAILPIT_UI_PORT" "Neo4j:$NEO4J_HTTP_PORT"; do
  label=${entry%%:*}
  port=${entry##*:}
  code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 "http://$HOST:$port/" || true)
  if [ "$code" = "200" ] || [ "$code" = "302" ]; then
    pass "$label responde en $port"
  else
    fail "$label no responde en $port (codigo $code)"
  fi
done

echo
if [ "$failures" -eq 0 ]; then
  echo "Smoke OK: el stack esta arriba."
  exit 0
fi
echo "Smoke con $failures fallo(s). Revisa: docker compose ps && docker compose logs"
exit 1
