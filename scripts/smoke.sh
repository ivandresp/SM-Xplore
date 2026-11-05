#!/usr/bin/env bash
# Verificacion rapida del stack integrado (compose.yaml raiz).
#
# Uso: bash scripts/smoke.sh
# Variables: HOST, MS01_PORT..MS05_PORT, WAIT_SECONDS.
set -uo pipefail

# Si existe .env, respeta los puertos configurados alli. Se parsea linea a linea: sourcear rompe
# con valores sin comillas que llevan espacios (p. ej. BOOTSTRAP_ADMIN_NAME=Administrador SM-Xplore).
if [ -f .env ]; then
  while IFS='=' read -r key value; do
    case "$key" in
      ''|'#'*) continue ;;
    esac
    key=$(printf '%s' "$key" | tr -d '[:space:]')
    if [ -n "$key" ]; then
      export "$key=$value"
    fi
  done < .env
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
  code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 -H "Authorization: Bearer $token" "http://$HOST:$MS03_PORT/api/v1/reservations?status=CONFIRMED" || true)
  if [ "$code" = "200" ]; then
    pass "ms-03 valida el JWT con su JWKS (GET /api/v1/reservations)"
  else
    fail "ms-03 rechazo el JWT (codigo $code)"
  fi
else
  fail "no se pudo iniciar sesion con el administrador dev"
fi

echo "== Metricas (Prometheus) =="
for entry in "ms-01:$MS01_PORT" "ms-02:$MS02_PORT" "ms-03:$MS03_PORT" "ms-04:$MS04_PORT" "ms-05:$MS05_PORT"; do
  label=$(printf '%s' "$entry" | cut -d: -f1)
  port=$(printf '%s' "$entry" | cut -d: -f2)
  code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 "http://$HOST:$port/actuator/prometheus" || true)
  if [ "$code" = "200" ]; then
    pass "$label expone /actuator/prometheus"
  else
    fail "$label /actuator/prometheus respondio $code (se espera 200)"
  fi
done

# Peticion con cuerpo JSON: deja el status en RESP_CODE y el cuerpo en RESP_BODY.
send_json() {
  method=$1
  url=$2
  bearer=$3
  body=$4
  if [ -n "$bearer" ]; then
    raw=$(curl -sS --max-time 20 -X "$method" "$url" -H "Content-Type: application/json" -H "Authorization: Bearer $bearer" -d "$body" -w '\n%{http_code}' 2>/dev/null || true)
  else
    raw=$(curl -sS --max-time 20 -X "$method" "$url" -H "Content-Type: application/json" -d "$body" -w '\n%{http_code}' 2>/dev/null || true)
  fi
  RESP_CODE=$(printf '%s' "$raw" | tail -n 1)
  RESP_BODY=$(printf '%s' "$raw" | sed '$d')
}

post_json() {
  send_json POST "$1" "$2" "$3"
}

patch_json() {
  send_json PATCH "$1" "$2" "$3"
}

# Primer campo de texto del JSON recibido: suficiente para un smoke.
json_str() {
  printf '%s' "$1" | sed -n "s/.*\"$2\":\"\([^\"]*\)\".*/\1/p"
}

echo "== Flujo de negocio (catalogo -> actividad -> resena -> reserva) =="
category_id=""
zone_id=""
site_id=""
activity_id=""
tourist_id=""
tourist_token=""
reservation_id=""
if [ -z "$token" ]; then
  fail "sin token de administrador no se puede recorrer el flujo de negocio"
else
  stamp=$(date +%s)
  base04="http://$HOST:$MS04_PORT"
  base03="http://$HOST:$MS03_PORT"
  # El dominio exige endsAt > startsAt: se derivan ambos del mismo instante base.
  starts_at=$(date -u -d "+3 days" +%Y-%m-%dT%H:%M:%SZ 2>/dev/null || date -u -v+3d +%Y-%m-%dT%H:%M:%SZ)
  ends_at=$(date -u -d "+3 days +2 hours" +%Y-%m-%dT%H:%M:%SZ 2>/dev/null || date -u -v+3d -v+2H +%Y-%m-%dT%H:%M:%SZ)

  post_json "$base04/api/v1/categories" "$token" "{\"name\":\"Smoke categoria $stamp\",\"scope\":\"ACTIVITY\"}"
  category_id=$(json_str "$RESP_BODY" categoryId)
  if [ -n "$category_id" ]; then
    pass "ms-04 creo la categoria ($category_id)"
  else
    fail "ms-04 no creo la categoria (HTTP $RESP_CODE): $RESP_BODY"
  fi

  post_json "$base04/api/v1/zones" "$token" "{\"name\":\"Smoke zona $stamp\",\"type\":\"URBANA\",\"description\":\"zona del smoke\"}"
  zone_id=$(json_str "$RESP_BODY" zoneId)
  if [ -n "$zone_id" ]; then
    pass "ms-04 creo la zona ($zone_id)"
  else
    fail "ms-04 no creo la zona (HTTP $RESP_CODE): $RESP_BODY"
  fi

  if [ -n "$category_id" ] && [ -n "$zone_id" ]; then
    post_json "$base04/api/v1/sites" "$token" "{\"name\":\"Smoke sitio $stamp\",\"description\":\"sitio del smoke\",\"categoryId\":\"$category_id\",\"zoneId\":\"$zone_id\",\"latitude\":11.2408,\"longitude\":-74.1990,\"maxCapacity\":20}"
    site_id=$(json_str "$RESP_BODY" siteId)
    if [ -n "$site_id" ]; then
      pass "ms-04 creo el sitio ($site_id)"
    else
      fail "ms-04 no creo el sitio (HTTP $RESP_CODE): $RESP_BODY"
    fi
  else
    fail "sin categoria/zona no se puede crear el sitio"
  fi

  if [ -n "$site_id" ]; then
    post_json "$base03/api/v1/activities" "$token" "{\"siteId\":\"$site_id\",\"name\":\"Smoke actividad $stamp\",\"category\":\"AVENTURA\",\"price\":25.00,\"maxCapacity\":10,\"durationMinutes\":120,\"startsAt\":\"$starts_at\",\"endsAt\":\"$ends_at\",\"description\":\"actividad del smoke\"}"
    activity_id=$(json_str "$RESP_BODY" id)
    if [ -n "$activity_id" ]; then
      pass "ms-03 creo la actividad ($activity_id)"
      # La actividad nace en DRAFT: sin publicarla el catalogo da 404 y la resena/reserva 400.
      patch_json "$base03/api/v1/activities/$activity_id/status" "$token" "{\"status\":\"PUBLISHED\"}"
      activity_status=$(json_str "$RESP_BODY" status)
      if [ "$activity_status" = "PUBLISHED" ]; then
        pass "la actividad quedo PUBLISHED"
      else
        fail "no se pudo publicar la actividad (HTTP $RESP_CODE): $RESP_BODY"
      fi
    else
      fail "ms-03 no creo la actividad (HTTP $RESP_CODE): $RESP_BODY"
    fi
  fi

  if [ -n "$activity_id" ]; then
    code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 "$base03/api/v1/activities/$activity_id" || true)
    if [ "$code" = "200" ]; then
      pass "GET publico de la actividad responde 200"
    else
      fail "GET publico de la actividad respondio $code"
    fi
  fi

  # Turista real: registro publico y login, sin tocar usuarios de administrador.
  tourist_email="smoke-$stamp@smxplore.local"
  post_json "http://$HOST:$MS01_PORT/api/v1/auth/register/turista" "" "{\"fullName\":\"Smoke Turista $stamp\",\"email\":\"$tourist_email\",\"phone\":\"3001234567\",\"password\":\"SmokePass12345\",\"dniType\":\"CC\",\"dniNumber\":\"$stamp\",\"preferredLanguage\":\"es\",\"birthDate\":\"1990-01-01\",\"nationality\":\"CO\"}"
  tourist_id=$(json_str "$RESP_BODY" id)
  if [ -n "$tourist_id" ]; then
    pass "ms-01 registro al turista ($tourist_id)"
  else
    fail "ms-01 no registro al turista (HTTP $RESP_CODE): $RESP_BODY"
  fi

  post_json "http://$HOST:$MS01_PORT/api/v1/auth/login" "" "{\"email\":\"$tourist_email\",\"password\":\"SmokePass12345\"}"
  tourist_token=$(json_str "$RESP_BODY" accessToken)
  if [ -n "$tourist_token" ]; then
    pass "login del turista devuelve accessToken"
  else
    fail "login del turista fallo (HTTP $RESP_CODE): $RESP_BODY"
  fi

  if [ -n "$tourist_token" ] && [ -n "$activity_id" ]; then
    post_json "$base03/api/v1/reviews" "$tourist_token" "{\"itemType\":\"ACTIVITY\",\"itemId\":\"$activity_id\",\"rating\":5,\"comment\":\"resena del smoke\"}"
    if [ "$RESP_CODE" = "201" ] || [ "$RESP_CODE" = "200" ]; then
      pass "el turista publico una resena"
    else
      fail "la resena respondio $RESP_CODE: $RESP_BODY"
    fi

    post_json "$base03/api/v1/reservations" "$tourist_token" "{\"touristId\":\"$tourist_id\",\"itemType\":\"ACTIVITY\",\"itemId\":\"$activity_id\",\"numberOfPeople\":1,\"paymentMethod\":\"CARD\",\"bookedFor\":\"$starts_at\"}"
    reservation_id=$(json_str "$RESP_BODY" id)
    if [ -n "$reservation_id" ]; then
      pass "el turista creo la reserva ($reservation_id)"
      code=$(curl -s -o /dev/null -w '%{http_code}' --max-time 5 -H "Authorization: Bearer $tourist_token" "$base03/api/v1/reservations/$reservation_id" || true)
      if [ "$code" = "200" ]; then
        pass "GET de la reserva propia responde 200"
      else
        fail "GET de la reserva propia respondio $code"
      fi
    else
      fail "la reserva respondio $RESP_CODE: $RESP_BODY"
    fi
  fi

  # El outbox es la prueba de que el evento salio: se consulta si hay docker disponible.
  if [ -f compose.yaml ] && command -v docker >/dev/null 2>&1; then
    pg_user=smxplore
    if [ -n "${POSTGRES_USER:-}" ]; then pg_user=$POSTGRES_USER; fi
    # El relay publica cada 2 s: se reintenta la consulta en lugar de asumir que ya paso.
    published=0
    attempt=0
    while [ "$attempt" -lt 10 ]; do
      current=$(docker compose exec -T postgres psql -U "$pg_user" -d activities_events -tAc "SELECT count(*) FROM outbox_events WHERE status = 'PUBLISHED'" 2>/dev/null | tr -d '[:space:]' || true)
      case "$current" in
        ''|*[!0-9]*) ;;
        *) published=$current ;;
      esac
      if [ "$published" -gt 0 ]; then
        break
      fi
      attempt=$((attempt + 1))
      sleep 2
    done
    if [ "$published" -gt 0 ]; then
      pass "el outbox de ms-03 publico $published evento(s)"
    else
      fail "el outbox de ms-03 no registro eventos publicados tras 20 s"
    fi
  else
    printf '  SKIP outbox: sin docker en PATH o fuera del directorio del compose\n'
  fi
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